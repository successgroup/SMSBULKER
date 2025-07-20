const axios = require('axios');
const { v4: uuidv4 } = require('uuid');
const { firestore, admin } = require('../config/firebase');
const { PaymentStatus, PaymentResponse } = require('../models/paymentModels');

// Paystack API configuration
const PAYSTACK_SECRET_KEY = process.env.PAYSTACK_SECRET_KEY;
const PAYSTACK_BASE_URL = process.env.PAYSTACK_BASE_URL || 'https://api.paystack.co';

// Initialize Paystack transaction
const initializeTransaction = async (req, res) => {
  try {
    // Get payment details from request body
    const { email, amount, currency, userId, packageId } = req.body;

    // Validate required fields
    if (!email || !amount || !currency || !userId) {
      return res.status(400).json({
        success: false,
        message: 'Missing required fields: email, amount, currency, userId',
      });
    }

    // Generate a unique reference for this transaction
    const reference = `txn_${uuidv4().replace(/-/g, '')}`;

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
        callback_url: 'https://smsbulker.web.app/payment/callback',
      },
      {
        headers: {
          'Authorization': `Bearer ${PAYSTACK_SECRET_KEY}`,
          'Content-Type': 'application/json',
        },
      },
    );

    // Check if Paystack initialization was successful
    if (paystackResponse.data.status) {
      // Create a transaction record in Firestore (status: PENDING)
      const transactionId = `txn_${Date.now()}`;
      await firestore.collection('payment_transactions')
        .doc(transactionId).set({
          id: transactionId,
          userId,
          packageId: packageId || 'custom',
          amount,
          currency,
          credits: req.body.credits || 0,
          status: PaymentStatus.PENDING,
          paymentMethod: 'paystack',
          paystackReference: reference,
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
        });

      // Return success response with access_code and reference
      return res.status(200).json(new PaymentResponse({
        success: true,
        transactionId,
        paystackReference: paystackResponse.data.data.access_code,
        message: 'Payment initiated successfully',
      }));
    } else {
      // Return error if Paystack initialization failed
      return res.status(400).json(new PaymentResponse({
        success: false,
        message: 'Failed to initialize payment with Paystack',
      }));
    }
  } catch (error) {
    console.error('Error initializing Paystack transaction:', error);
    return res.status(500).json(new PaymentResponse({
      success: false,
      message: `Error initializing payment: ${error.message}`,
    }));
  }
};

// Verify Paystack transaction
// SOLUTION 4: Add detailed error handling and logging
const verifyTransaction = async (req, res) => {
  try {
    console.log(`Starting transaction verification for reference: ${req.params.reference}`);
    // Get reference from URL parameters
    const { reference } = req.params;

    if (!reference) {
      console.error('Transaction verification failed: Missing reference parameter');
      return res.status(400).json({
        success: false,
        message: 'Transaction reference is required',
      });
    }

    // Verify transaction with Paystack
    console.log(`Calling Paystack API to verify transaction: ${reference}`);
    let paystackResponse;
    try {
      paystackResponse = await axios.get(
        `${PAYSTACK_BASE_URL}/transaction/verify/${reference}`,
        {
          headers: {
            'Authorization': `Bearer ${PAYSTACK_SECRET_KEY}`,
            'Content-Type': 'application/json',
          },
        },
      );
      console.log(`Paystack verification response received for ${reference}: ${paystackResponse.data.status ? 'Success' : 'Failed'}`);
    } catch (error) {
      console.error(`Paystack API error for reference ${reference}:`, error.message);
      return res.status(500).json({
        success: false,
        message: `Error verifying payment with Paystack: ${error.message}`,
      });
    }

    // Find the transaction in Firestore
    console.log(`Searching for transaction with reference ${reference} in Firestore`);
    let transactionsSnapshot;
    try {
      transactionsSnapshot = await firestore
        .collection('payment_transactions')
        .where('paystackReference', '==', reference)
        .limit(1)
        .get();
    } catch (error) {
      console.error(`Firestore query error for reference ${reference}:`, error.message);
      return res.status(500).json({
        success: false,
        message: `Database error while retrieving transaction: ${error.message}`,
      });
    }

    if (transactionsSnapshot.empty) {
      console.error(`Transaction not found in Firestore for reference: ${reference}`);
      return res.status(404).json({
        success: false,
        message: 'Transaction not found',
      });
    }
    
    console.log(`Transaction found in Firestore for reference: ${reference}`)

    const transactionDoc = transactionsSnapshot.docs[0];
    const transaction = transactionDoc.data();
    const transactionId = transactionDoc.id;

    // Check if Paystack verification was successful
    console.log(`Checking Paystack verification status for transaction ${transactionId}`);
    if (paystackResponse.data.status &&
      paystackResponse.data.data.status === 'success') {
      console.log(`Paystack verification successful for transaction ${transactionId}`);
      
      // Update transaction status in Firestore
      try {
        console.log(`Updating transaction ${transactionId} status to SUCCESS`);
        await firestore.collection('payment_transactions')
          .doc(transactionId).update({
            status: PaymentStatus.SUCCESS,
            completedAt: admin.firestore.FieldValue.serverTimestamp(),
          });
        console.log(`Transaction ${transactionId} status updated successfully`);
      } catch (error) {
        console.error(`Error updating transaction status: ${error.message}`);
        return res.status(500).json({
          success: false,
          message: `Failed to update transaction status: ${error.message}`,
        });
      }

      // Credit update is now handled in the success response section

      // Flag to track if credit update was successful
      let creditUpdateSuccess = true;
      let creditUpdateError = null;
      
      try {
        // Update user's creditBalance in Firestore
        const userRef = firestore.collection('users')
          .doc(transaction.userId);
        const creditBalanceRef = userRef.collection('creditBalance').doc('current');
        
        try {
          console.log(`Attempting to update creditBalance for user ${transaction.userId} with ${transaction.credits} credits`);
          
          // Check if user document exists before transaction
          const userDocCheck = await userRef.get();
          if (!userDocCheck.exists) {
            console.error(`User document not found for ID: ${transaction.userId}`);
            throw new Error('User not found');
          }
          
          // Check if creditBalance document exists
          const creditBalanceCheck = await creditBalanceRef.get();
          
          await firestore.runTransaction(async (dbTransaction) => {
            let creditBalanceDoc;
            
            if (creditBalanceCheck.exists) {
              creditBalanceDoc = await dbTransaction.get(creditBalanceRef);
              const creditBalanceData = creditBalanceDoc.data();
              const currentCredits = creditBalanceData.availableCredits || 0;
              const usedCredits = creditBalanceData.usedCredits || 0;
              const newCredits = currentCredits + transaction.credits;
              
              console.log(`Updating creditBalance: ${currentCredits} + ${transaction.credits} = ${newCredits}`);
              
              dbTransaction.update(creditBalanceRef, {
                availableCredits: newCredits,
                lastUpdated: admin.firestore.FieldValue.serverTimestamp()
              });
            } else {
              // Create a new creditBalance document if it doesn't exist
              console.log(`Creating new creditBalance document for user ${transaction.userId}`);
              
              const newCreditBalance = {
                availableCredits: transaction.credits,
                usedCredits: 0,
                lastUpdated: admin.firestore.FieldValue.serverTimestamp(),
                nextRefillDate: null,
                autoRefillEnabled: false,
                lowBalanceAlert: 100
              };
              
              dbTransaction.set(creditBalanceRef, newCreditBalance);
            }
          });
          console.log(`Successfully updated credits for user ${transaction.userId}: added ${transaction.credits} credits`);
           
           // Send notification to the app about successful credit update
             try {
               // Create a notification record in Firestore
               await firestore.collection('notifications')
                 .add({
                   userId: transaction.userId,
                   type: 'CREDIT_UPDATE',
                   title: 'Credits Added',
                   message: `${transaction.credits} credits have been added to your account. Your credit balance has been updated.`,
                   data: {
                     transactionId: transactionId,
                     credits: transaction.credits,
                     timestamp: admin.firestore.FieldValue.serverTimestamp()
                   },
                   read: false,
                   createdAt: admin.firestore.FieldValue.serverTimestamp()
                 });
             console.log(`Notification created for user ${transaction.userId} about credit update`);
           } catch (notificationError) {
             console.error(`Failed to create notification: ${notificationError.message}`);
             // Don't fail the transaction if notification creation fails
           }
        } catch (error) {
            console.error(`Failed to update user credits: ${error.message}`);
            console.error(`Error code: ${error.code}, Error details:`, error);
            
            creditUpdateSuccess = false;
            creditUpdateError = error;
            
            // Check if it's a permission error
            if (error.code === 'permission-denied') {
              console.error('Permission denied error detected. This may be due to Firestore rules restrictions.');
              console.error('Ensure the backend service is properly authenticated or Firestore rules allow this operation.');
            }
            
            // Try a direct update as fallback (non-transactional)
            try {
              console.log('Attempting direct update of creditBalance as fallback...');
              const creditBalanceRef = userRef.collection('creditBalance').doc('current');
              const creditBalanceDoc = await creditBalanceRef.get();
              
              if (creditBalanceDoc.exists) {
                // Update existing creditBalance document
                await creditBalanceRef.update({
                  availableCredits: admin.firestore.FieldValue.increment(transaction.credits),
                  lastUpdated: admin.firestore.FieldValue.serverTimestamp(),
                });
              } else {
                // Create a new creditBalance document
                await creditBalanceRef.set({
                  availableCredits: transaction.credits,
                  usedCredits: 0,
                  lastUpdated: admin.firestore.FieldValue.serverTimestamp(),
                  nextRefillDate: null,
                  autoRefillEnabled: false,
                  lowBalanceAlert: 100
                });
              }
              
              console.log(`Fallback method: Successfully updated creditBalance for user ${transaction.userId}`);
              creditUpdateSuccess = true;
              creditUpdateError = null;
              
              // Send notification to the app about successful credit update
              try {
                // Create a notification record in Firestore
                await firestore.collection('notifications')
                  .add({
                    userId: transaction.userId,
                    type: 'CREDIT_UPDATE',
                    title: 'Credits Added',
                    message: `${transaction.credits} credits have been added to your account. Your credit balance has been updated.`,
                    data: {
                      transactionId: transactionId,
                      credits: transaction.credits,
                      timestamp: admin.firestore.FieldValue.serverTimestamp()
                    },
                    read: false,
                    createdAt: admin.firestore.FieldValue.serverTimestamp()
                  });
                console.log(`Notification created for user ${transaction.userId} about credit update`);
              } catch (notificationError) {
                console.error(`Failed to create notification: ${notificationError.message}`);
                // Don't fail the transaction if notification creation fails
              }
            } catch (fallbackError) {
              console.error(`Fallback update also failed: ${fallbackError.message}`);
              // Keep track of the error but don't throw - we'll return a partial success response
              
              // Schedule a retry by creating a record in the retry queue
              try {
                await firestore.collection('credit_update_queue').add({
                  userId: transaction.userId,
                  transactionId: transactionId,
                  credits: transaction.credits,
                  status: 'PENDING',
                  attempts: 0,
                  maxAttempts: 5,
                  nextAttemptAt: new Date(Date.now() + 5 * 60 * 1000), // Try again in 5 minutes
                  createdAt: admin.firestore.FieldValue.serverTimestamp(),
                  updatedAt: admin.firestore.FieldValue.serverTimestamp(),
                  error: fallbackError.message
                });
                console.log(`Scheduled credit update retry for transaction ${transactionId}`);
              } catch (queueError) {
                console.error(`Failed to schedule retry: ${queueError.message}`);
              }
            }
        }
      } catch (creditError) {
        console.error('Unexpected error during credit update process:', creditError);
        creditUpdateSuccess = false;
        creditUpdateError = creditError;
      }
      
      // Return success response with transaction details
      // Include credit update status in the response
      return res.status(200).json({
        id: transactionId,
        userId: transaction.userId,
        packageId: transaction.packageId,
        amount: transaction.amount,
        currency: transaction.currency,
        credits: transaction.credits,
        status: PaymentStatus.SUCCESS,
        paymentMethod: 'paystack',
        paystackReference: reference,
        createdAt: transaction.createdAt,
        completedAt: admin.firestore.FieldValue.serverTimestamp(),
        creditUpdateSuccess: creditUpdateSuccess,
        creditUpdateError: creditUpdateError ? creditUpdateError.message : null,
      });
    } else {
      console.log(`Paystack verification failed for transaction ${transactionId}`);
      const failureReason = paystackResponse.data.data.gateway_response || 'Payment verification failed';
      console.log(`Failure reason: ${failureReason}`);
      
      // Update transaction status to FAILED in Firestore
      try {
        console.log(`Updating transaction ${transactionId} status to FAILED`);
        await firestore.collection('payment_transactions')
          .doc(transactionId).update({
            status: PaymentStatus.FAILED,
            failureReason: failureReason,
            completedAt: admin.firestore.FieldValue.serverTimestamp(),
          });
        console.log(`Transaction ${transactionId} status updated to FAILED successfully`);
      } catch (error) {
        console.error(`Error updating transaction status to FAILED: ${error.message}`);
        return res.status(500).json({
          success: false,
          message: `Failed to update transaction status: ${error.message}`,
        });
      }

      // Return failure response
      console.log(`Returning failure response for transaction ${transactionId}`);
      return res.status(400).json({
        id: transactionId,
        userId: transaction.userId,
        packageId: transaction.packageId,
        amount: transaction.amount,
        currency: transaction.currency,
        credits: transaction.credits,
        status: PaymentStatus.FAILED,
        paymentMethod: 'paystack',
        paystackReference: reference,
        createdAt: transaction.createdAt,
        completedAt: admin.firestore.FieldValue.serverTimestamp(),
        failureReason: failureReason,
      });
    }
  } catch (error) {
    // SOLUTION 4: Enhanced error handling with detailed logging
    console.error('Error verifying Paystack transaction:', error);
    
    // Log additional details about the error
    const errorDetails = {
      message: error.message,
      stack: error.stack,
      reference: req.params.reference || 'unknown',
      endpoint: `${PAYSTACK_BASE_URL}/transaction/verify/${req.params.reference}`,
      timestamp: new Date().toISOString()
    };
    
    console.error('Detailed error information:', JSON.stringify(errorDetails));
    
    // Return a more informative error response
    return res.status(500).json({
      success: false,
      message: `Error verifying payment: ${error.message}`,
      errorCode: error.code || 'UNKNOWN_ERROR',
      reference: req.params.reference || 'unknown'
    });
  }
};

// Function to retry failed credit updates
const retryFailedCreditUpdate = async (userId, credits, transactionId) => {
  console.log(`Retrying credit update for user ${userId} with ${credits} credits (Transaction: ${transactionId})`);
  
  const userRef = firestore.collection('users').doc(userId);
  const creditBalanceRef = userRef.collection('creditBalance').doc('current');
  
  try {
    // Check if user exists
    const userDoc = await userRef.get();
    if (!userDoc.exists) {
      console.error(`Retry failed: User ${userId} not found`);
      return false;
    }
    
    // Check if creditBalance document exists
    const creditBalanceDoc = await creditBalanceRef.get();
    
    if (creditBalanceDoc.exists) {
      // Update existing creditBalance document
      await creditBalanceRef.update({
        availableCredits: admin.firestore.FieldValue.increment(credits),
        lastUpdated: admin.firestore.FieldValue.serverTimestamp(),
      });
    } else {
      // Create a new creditBalance document
      await creditBalanceRef.set({
        availableCredits: credits,
        usedCredits: 0,
        lastUpdated: admin.firestore.FieldValue.serverTimestamp(),
        nextRefillDate: null,
        autoRefillEnabled: false,
        lowBalanceAlert: 100
      });
    }
    
    console.log(`Retry successful: Updated creditBalance for user ${userId}`);
    
    // Create a record of the retry
    await firestore.collection('credit_update_retries').add({
      userId,
      transactionId,
      credits,
      status: 'SUCCESS',
      timestamp: admin.firestore.FieldValue.serverTimestamp()
    });
    
    // Send notification
    await firestore.collection('notifications').add({
      userId,
      type: 'CREDIT_UPDATE_RETRY',
      title: 'Credits Added (Delayed)',
      message: `${credits} credits have been added to your account. Your credit balance has been updated.`,
      data: {
        transactionId,
        credits,
        timestamp: admin.firestore.FieldValue.serverTimestamp()
      },
      read: false,
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    });
    
    return true;
  } catch (error) {
    console.error(`Retry failed: ${error.message}`);
    
    // Record the failed retry
    try {
      await firestore.collection('credit_update_retries').add({
        userId,
        transactionId,
        credits,
        status: 'FAILED',
        error: error.message,
        timestamp: admin.firestore.FieldValue.serverTimestamp()
      });
    } catch (logError) {
      console.error(`Failed to log retry attempt: ${logError.message}`);
    }
    
    return false;
  }
};

// Endpoint to manually retry a failed credit update
const manualRetryUpdate = async (req, res) => {
  try {
    const { transactionId } = req.params;
    
    if (!transactionId) {
      return res.status(400).json({
        success: false,
        message: 'Transaction ID is required'
      });
    }
    
    // Get the transaction details
    const transactionDoc = await firestore.collection('payment_transactions')
      .doc(transactionId).get();
      
    if (!transactionDoc.exists) {
      return res.status(404).json({
        success: false,
        message: 'Transaction not found'
      });
    }
    
    const transaction = transactionDoc.data();
    
    // Only retry if transaction was successful but credit update failed
    if (transaction.status !== PaymentStatus.SUCCESS) {
      return res.status(400).json({
        success: false,
        message: 'Cannot retry credit update for non-successful transaction'
      });
    }
    
    // Attempt the retry
    const retryResult = await retryFailedCreditUpdate(
      transaction.userId,
      transaction.credits,
      transactionId
    );
    
    if (retryResult) {
      return res.status(200).json({
        success: true,
        message: `Successfully updated credit balance for user ${transaction.userId}`,
        transactionId,
        credits: transaction.credits
      });
    } else {
      return res.status(500).json({
        success: false,
        message: 'Failed to update credit balance. See server logs for details.',
        transactionId
      });
    }
  } catch (error) {
    console.error('Error in manual retry:', error);
    return res.status(500).json({
      success: false,
      message: `Error: ${error.message}`
    });
  }
};

module.exports = {
  initializeTransaction,
  verifyTransaction,
  manualRetryUpdate,
  retryFailedCreditUpdate
};