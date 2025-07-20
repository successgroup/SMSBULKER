/**
 * Script to check and diagnose Firestore permissions
 * This script tests various operations to help identify permission issues
 */

require('dotenv').config();
const admin = require('firebase-admin');

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
        
        // Log service account details (without sensitive info)
        console.log('Service account project ID:', serviceAccount.project_id);
        console.log('Service account client email:', serviceAccount.client_email);
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
 * Test read access to a collection
 */
async function testCollectionRead(collectionName) {
  console.log(`Testing read access to '${collectionName}' collection...`);
  try {
    const snapshot = await firestore.collection(collectionName).limit(1).get();
    console.log(`✅ Successfully read from '${collectionName}' collection`);
    console.log(`   Documents found: ${snapshot.size}`);
    return true;
  } catch (error) {
    console.error(`❌ Failed to read from '${collectionName}' collection:`, error.message);
    return false;
  }
}

/**
 * Test write access to a collection
 */
async function testCollectionWrite(collectionName) {
  const testDocId = `permission_test_${Date.now()}`;
  console.log(`Testing write access to '${collectionName}' collection...`);
  
  try {
    // Try to write a test document
    await firestore.collection(collectionName).doc(testDocId).set({
      test: true,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      description: 'Permission test document'
    });
    console.log(`✅ Successfully wrote to '${collectionName}/${testDocId}'`);
    
    // Clean up the test document
    await firestore.collection(collectionName).doc(testDocId).delete();
    console.log(`✅ Successfully deleted '${collectionName}/${testDocId}'`);
    
    return true;
  } catch (error) {
    console.error(`❌ Failed to write to '${collectionName}' collection:`, error.message);
    return false;
  }
}

/**
 * Test user credit update
 */
async function testUserCreditUpdate() {
  const testUserId = `test_user_${Date.now()}`;
  console.log(`Testing user credit update for test user '${testUserId}'...`);
  
  try {
    // Create a test user
    await firestore.collection('users').doc(testUserId).set({
      email: `test_${Date.now()}@example.com`,
      credits: 0,
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    });
    console.log(`✅ Successfully created test user '${testUserId}'`);
    
    // Test updating credits with a transaction
    await firestore.runTransaction(async (transaction) => {
      const userRef = firestore.collection('users').doc(testUserId);
      const userDoc = await transaction.get(userRef);
      
      if (!userDoc.exists) {
        throw new Error('Test user not found');
      }
      
      // Update only allowed fields
      transaction.update(userRef, {
        credits: admin.firestore.FieldValue.increment(100),
        lastCreditUpdate: admin.firestore.FieldValue.serverTimestamp()
      });
    });
    console.log(`✅ Successfully updated credits for test user using transaction`);
    
    // Test direct update
    await firestore.collection('users').doc(testUserId).update({
      credits: admin.firestore.FieldValue.increment(50),
      lastCreditUpdate: admin.firestore.FieldValue.serverTimestamp()
    });
    console.log(`✅ Successfully updated credits for test user using direct update`);
    
    // Test creditBalance subcollection
    console.log(`Testing creditBalance subcollection for test user '${testUserId}'...`);
    const creditBalanceRef = firestore.collection('users').doc(testUserId).collection('creditBalance').doc('current');
    
    // Create creditBalance document
    await creditBalanceRef.set({
      availableCredits: 100,
      usedCredits: 0,
      lastUpdated: admin.firestore.FieldValue.serverTimestamp()
    });
    console.log(`✅ Successfully created creditBalance document for test user`);
    
    // Update creditBalance document
    await creditBalanceRef.update({
      availableCredits: admin.firestore.FieldValue.increment(50),
      lastUpdated: admin.firestore.FieldValue.serverTimestamp()
    });
    console.log(`✅ Successfully updated creditBalance document for test user`);
    
    // Clean up
    await creditBalanceRef.delete();
    await firestore.collection('users').doc(testUserId).delete();
    console.log(`✅ Successfully deleted test user '${testUserId}' and creditBalance document`);
    
    return true;
  } catch (error) {
    console.error(`❌ Failed to test user credit update:`, error.message);
    return false;
  }
}

/**
 * Test payment transaction operations
 */
async function testPaymentTransactions() {
  const testTransactionId = `test_txn_${Date.now()}`;
  console.log(`Testing payment transaction operations for '${testTransactionId}'...`);
  
  try {
    // Create a test transaction
    await firestore.collection('payment_transactions').doc(testTransactionId).set({
      userId: `test_user_${Date.now()}`,
      amount: 1000,
      credits: 100,
      status: 'PENDING',
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    });
    console.log(`✅ Successfully created test transaction '${testTransactionId}'`);
    
    // Update the transaction status
    await firestore.collection('payment_transactions').doc(testTransactionId).update({
      status: 'SUCCESS',
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });
    console.log(`✅ Successfully updated test transaction status`);
    
    // Clean up
    await firestore.collection('payment_transactions').doc(testTransactionId).delete();
    console.log(`✅ Successfully deleted test transaction '${testTransactionId}'`);
    
    return true;
  } catch (error) {
    console.error(`❌ Failed to test payment transaction operations:`, error.message);
    return false;
  }
}

/**
 * Test notifications
 */
async function testNotifications() {
  console.log(`Testing notifications...`);
  
  try {
    // Create a test notification
    const notificationRef = await firestore.collection('notifications').add({
      userId: `test_user_${Date.now()}`,
      type: 'TEST',
      title: 'Test Notification',
      message: 'This is a test notification',
      read: false,
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    });
    console.log(`✅ Successfully created test notification '${notificationRef.id}'`);
    
    // Clean up
    await notificationRef.delete();
    console.log(`✅ Successfully deleted test notification '${notificationRef.id}'`);
    
    return true;
  } catch (error) {
    console.error(`❌ Failed to test notifications:`, error.message);
    return false;
  }
}

/**
 * Run all tests
 */
async function runAllTests() {
  console.log('======================================');
  console.log('FIRESTORE PERMISSIONS DIAGNOSTIC TOOL');
  console.log('======================================');
  console.log('Starting tests...');
  console.log('');
  
  // Test collections
  const collections = ['users', 'payment_transactions', 'notifications', 'credit_update_queue'];
  const readResults = {};
  const writeResults = {};
  
  for (const collection of collections) {
    readResults[collection] = await testCollectionRead(collection);
    writeResults[collection] = await testCollectionWrite(collection);
    console.log('');
  }
  
  // Test specific operations
  console.log('Testing specific operations:');
  const userCreditUpdateResult = await testUserCreditUpdate();
  console.log('');
  
  const paymentTransactionsResult = await testPaymentTransactions();
  console.log('');
  
  const notificationsResult = await testNotifications();
  console.log('');
  
  // Summary
  console.log('======================================');
  console.log('TEST RESULTS SUMMARY');
  console.log('======================================');
  
  console.log('Collection Read Access:');
  for (const [collection, result] of Object.entries(readResults)) {
    console.log(`- ${collection}: ${result ? '✅ PASS' : '❌ FAIL'}`);
  }
  
  console.log('\nCollection Write Access:');
  for (const [collection, result] of Object.entries(writeResults)) {
    console.log(`- ${collection}: ${result ? '✅ PASS' : '❌ FAIL'}`);
  }
  
  console.log('\nSpecific Operations:');
  console.log(`- User Credit Update: ${userCreditUpdateResult ? '✅ PASS' : '❌ FAIL'}`);
  console.log(`- Payment Transactions: ${paymentTransactionsResult ? '✅ PASS' : '❌ FAIL'}`);
  console.log(`- Notifications: ${notificationsResult ? '✅ PASS' : '❌ FAIL'}`);
  
  // Overall assessment
  const allPassed = Object.values(readResults).every(r => r) && 
                   Object.values(writeResults).every(r => r) && 
                   userCreditUpdateResult && 
                   paymentTransactionsResult && 
                   notificationsResult;
  
  console.log('\n======================================');
  if (allPassed) {
    console.log('✅ ALL TESTS PASSED');
    console.log('Your Firebase service account has all the necessary permissions.');
  } else {
    console.log('❌ SOME TESTS FAILED');
    console.log('Your Firebase service account is missing some permissions.');
    console.log('\nPossible solutions:');
    console.log('1. Check your Firestore security rules');
    console.log('2. Verify the service account has the correct roles');
    console.log('3. Make sure the FIREBASE_SERVICE_ACCOUNT environment variable is correctly set');
  }
  console.log('======================================');
}

// Run the tests
runAllTests()
  .then(() => {
    console.log('Diagnostic completed');
    process.exit(0);
  })
  .catch(error => {
    console.error('Fatal error in diagnostic tool:', error);
    process.exit(1);
  });