/**
 * Script to process the credit update retry queue
 * This script can be run as a cron job to retry failed credit updates
 */

require('dotenv').config();
const admin = require('firebase-admin');
const { retryFailedCreditUpdate } = require('../controllers/paymentController');

// Initialize Firebase if not already initialized
let firestore;
try {
  if (!admin.apps.length) {
    // Load the service account
    let serviceAccount;
    if (process.env.FIREBASE_SERVICE_ACCOUNT) {
      try {
        const decodedServiceAccount = Buffer.from(
          process.env.FIREBASE_SERVICE_ACCOUNT,
          'base64'
        ).toString('utf8');
        serviceAccount = JSON.parse(decodedServiceAccount);
        console.log('Service account loaded from environment variable');
      } catch (error) {
        console.error('Error parsing service account:', error.message);
        process.exit(1);
      }
    } else {
      console.log('Using application default credentials');
    }

    // Initialize the app
    const firebaseConfig = serviceAccount
      ? { credential: admin.credential.cert(serviceAccount) }
      : {};

    admin.initializeApp(firebaseConfig);
  }

  firestore = admin.firestore();
} catch (error) {
  console.error('Firebase initialization error:', error);
  process.exit(1);
}

/**
 * Process the credit update queue
 */
async function processCreditUpdateQueue() {
  console.log('Starting credit update queue processing...');
  const now = new Date();
  
  try {
    // Get all pending retries that are due
    const queueSnapshot = await firestore.collection('credit_update_queue')
      .where('status', '==', 'PENDING')
      .where('nextAttemptAt', '<=', now)
      .where('attempts', '<', 'maxAttempts')
      .limit(50) // Process in batches
      .get();
    
    if (queueSnapshot.empty) {
      console.log('No pending credit updates to process');
      return;
    }
    
    console.log(`Found ${queueSnapshot.size} pending credit updates to process`);
    
    // Process each retry
    const promises = [];
    queueSnapshot.forEach(doc => {
      const retryData = doc.data();
      const retryId = doc.id;
      
      promises.push(processRetry(retryId, retryData));
    });
    
    await Promise.all(promises);
    console.log('Finished processing credit update queue');
  } catch (error) {
    console.error('Error processing credit update queue:', error);
  }
}

/**
 * Process a single retry
 */
async function processRetry(retryId, retryData) {
  console.log(`Processing retry ${retryId} for transaction ${retryData.transactionId}`);
  
  try {
    // Update the retry record to mark it as in progress
    await firestore.collection('credit_update_queue').doc(retryId).update({
      status: 'IN_PROGRESS',
      attempts: admin.firestore.FieldValue.increment(1),
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });
    
    // Attempt the retry
    const success = await retryFailedCreditUpdate(
      retryData.userId,
      retryData.credits,
      retryData.transactionId
    );
    
    if (success) {
      // Mark as completed
      await firestore.collection('credit_update_queue').doc(retryId).update({
        status: 'COMPLETED',
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });
      console.log(`Successfully processed retry ${retryId}`);
    } else {
      // Calculate next retry time with exponential backoff
      const nextAttempt = new Date();
      const backoffMinutes = Math.pow(2, retryData.attempts) * 5; // 5, 10, 20, 40, 80 minutes
      nextAttempt.setMinutes(nextAttempt.getMinutes() + backoffMinutes);
      
      // Update retry record
      await firestore.collection('credit_update_queue').doc(retryId).update({
        status: 'PENDING',
        nextAttemptAt: nextAttempt,
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });
      console.log(`Retry ${retryId} failed, scheduled next attempt in ${backoffMinutes} minutes`);
    }
  } catch (error) {
    console.error(`Error processing retry ${retryId}:`, error);
    
    // Update retry record with error
    try {
      const nextAttempt = new Date();
      nextAttempt.setMinutes(nextAttempt.getMinutes() + 15); // Try again in 15 minutes
      
      await firestore.collection('credit_update_queue').doc(retryId).update({
        status: 'ERROR',
        error: error.message,
        nextAttemptAt: nextAttempt,
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });
    } catch (updateError) {
      console.error(`Failed to update retry record ${retryId}:`, updateError);
    }
  }
}

// Run the processor
processCreditUpdateQueue()
  .then(() => {
    console.log('Credit update queue processing completed');
    process.exit(0);
  })
  .catch(error => {
    console.error('Fatal error in credit update queue processing:', error);
    process.exit(1);
  });