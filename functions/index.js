const {onRequest} = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const axios = require("axios");
const admin = require("firebase-admin");
const {v4: uuidv4} = require("uuid");

// Initialize Firebase Admin SDK
admin.initializeApp();

// Paystack API configuration
const PAYSTACK_SECRET_KEY = process.env.PAYSTACK_SECRET_KEY ||
 "YOUR_PAYSTACK_SECRET_KEY";
const PAYSTACK_BASE_URL = "https://api.paystack.co";

// Initialize Paystack transaction
exports.initializePaystackTransaction = onRequest(async (req, res) => {
  try {
    // Set CORS headers
    res.set("Access-Control-Allow-Origin", "*");
    res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
    res.set("Access-Control-Allow-Headers", "Content-Type");

    // Handle preflight OPTIONS request
    if (req.method === "OPTIONS") {
      res.status(204).send("");
      return;
    }

    // Ensure request is POST
    if (req.method !== "POST") {
      return res.status(405).json({
        success: false, message: "Method not allowed"});
    }

    // Get payment details from request body
    const {email, amount, currency, userId, packageId} = req.body;

    // Validate required fields
    if (!email || !amount || !currency || !userId) {
      return res.status(400).json({
        success: false,
        message: "Missing required fields: email, amount, currency, userId",
      });
    }

    // Generate a unique reference for this transaction
    const reference = `txn_${uuidv4().replace(/-/g, "")}`;

    // Convert amount to kobo (Paystack uses the smallest currency unit)
    const amountInKobo = Math.round(amount * 100);

    // Make request to Paystack API to initialize transaction
    const paystackResponse = await axios.post(
        `${PAYSTACK_BASE_URL}/transaction/initialize`,
        {
          email,
          amount: amountInKobo,
          currency,
          reference,
          callback_url: "https://smsbulker.web.app/payment/callback",
        },
        {
          headers: {
            "Authorization": `Bearer ${PAYSTACK_SECRET_KEY}`,
            "Content-Type": "application/json",
          },
        },
    );

    // Check if Paystack initialization was successful
    if (paystackResponse.data.status) {
      // Create a transaction record in Firestore (status: PENDING)
      const transactionId = `txn_${Date.now()}`;
      await admin.firestore().collection("payment_transactions")
          .doc(transactionId).set({
            id: transactionId,
            userId,
            packageId: packageId || "custom",
            amount,
            currency,
            credits: req.body.credits || 0,
            status: "PENDING",
            paymentMethod: "paystack",
            paystackReference: reference,
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
          });

      // Return success response with access_code and reference
      return res.status(200).json({
        success: true,
        transactionId,
        paystackReference: paystackResponse.data.data.access_code,
        message: "Payment initiated successfully",
      });
    } else {
      // Return error if Paystack initialization failed
      return res.status(400).json({
        success: false,
        message: "Failed to initialize payment with Paystack",
      });
    }
  } catch (error) {
    logger.error("Error initializing Paystack transaction:", error);
    return res.status(500).json({
      success: false,
      message: `Error initializing payment: ${error.message}`,
    });
  }
});

// Verify Paystack transaction
exports.verifyPaystackTransaction = onRequest(async (req, res) => {
  try {
    // Set CORS headers
    res.set("Access-Control-Allow-Origin", "*");
    res.set("Access-Control-Allow-Methods", "GET, OPTIONS");
    res.set("Access-Control-Allow-Headers", "Content-Type");

    // Handle preflight OPTIONS request
    if (req.method === "OPTIONS") {
      res.status(204).send("");
      return;
    }

    // Ensure request is GET
    if (req.method !== "GET") {
      return res.status(405).json({success: false,
        message: "Method not allowed"});
    }

    // Get reference from URL parameters
    const reference = req.params[0].split("/").pop();

    if (!reference) {
      return res.status(400).json({
        success: false,
        message: "Transaction reference is required",
      });
    }

    // Verify transaction with Paystack
    const paystackResponse = await axios.get(
        `${PAYSTACK_BASE_URL}/transaction/verify/${reference}`,
        {
          headers: {
            "Authorization": `Bearer ${PAYSTACK_SECRET_KEY}`,
            "Content-Type": "application/json",
          },
        },
    );

    // Find the transaction in Firestore
    const transactionsSnapshot = await admin
        .firestore()
        .collection("payment_transactions")
        .where("paystackReference", "==", reference)
        .limit(1)
        .get();

    if (transactionsSnapshot.empty) {
      return res.status(404).json({
        success: false,
        message: "Transaction not found",
      });
    }

    const transactionDoc = transactionsSnapshot.docs[0];
    const transaction = transactionDoc.data();
    const transactionId = transactionDoc.id;

    // Check if Paystack verification was successful
    if (paystackResponse.data.status &&
        paystackResponse.data.data.status === "success") {
      // Update transaction status in Firestore
      await admin.firestore().collection("payment_transactions")
          .doc(transactionId).update({
            status: "SUCCESS",
            completedAt: admin.firestore.FieldValue.serverTimestamp(),
          });

      // Update user's credits in Firestore
      const userRef = admin.firestore().collection("users")
          .doc(transaction.userId);
      await admin.firestore().runTransaction(async (dbTransaction) => {
        const userDoc = await dbTransaction.get(userRef);
        if (!userDoc.exists) {
          throw new Error("User not found");
        }

        const userData = userDoc.data();
        const currentCredits = userData.credits || 0;
        const newCredits = currentCredits + transaction.credits;

        dbTransaction.update(userRef, {
          credits: newCredits,
          lastCreditUpdate: admin.firestore.FieldValue.serverTimestamp(),
        });
      });

      // Return success response with transaction details
      return res.status(200).json({
        id: transactionId,
        userId: transaction.userId,
        packageId: transaction.packageId,
        amount: transaction.amount,
        currency: transaction.currency,
        credits: transaction.credits,
        status: "SUCCESS",
        paymentMethod: "paystack",
        paystackReference: reference,
        createdAt: transaction.createdAt,
        completedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    } else {
      // Update transaction status to FAILED in Firestore
      await admin.firestore().collection("payment_transactions")
          .doc(transactionId).update({
            status: "FAILED",
            failureReason: paystackResponse.data.data.gateway_response ||
            "Payment verification failed",
            completedAt: admin.firestore.FieldValue.serverTimestamp(),
          });

      // Return failure response
      return res.status(400).json({
        id: transactionId,
        userId: transaction.userId,
        packageId: transaction.packageId,
        amount: transaction.amount,
        currency: transaction.currency,
        credits: transaction.credits,
        status: "FAILED",
        paymentMethod: "paystack",
        paystackReference: reference,
        createdAt: transaction.createdAt,
        completedAt: admin.firestore.FieldValue.serverTimestamp(),
        failureReason: paystackResponse.data.data.gateway_response ||
            "Payment verification failed",
      });
    }
  } catch (error) {
    logger.error("Error verifying Paystack transaction:", error);
    return res.status(500).json({
      success: false,
      message: `Error verifying payment: ${error.message}`,
    });
  }
});
