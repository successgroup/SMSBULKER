const admin = require('firebase-admin');
const dotenv = require('dotenv');

dotenv.config();

// Initialize Firebase Admin with credentials from environment variable
let serviceAccount;

if (process.env.FIREBASE_SERVICE_ACCOUNT) {
  try {
    // Decode the base64 encoded service account key
    const decodedServiceAccount = Buffer.from(
      process.env.FIREBASE_SERVICE_ACCOUNT,
      'base64'
    ).toString('utf-8');
    
    // Log a small portion of the decoded service account to verify it's valid
    // (without exposing sensitive information)
    console.log('Decoded service account preview:', 
      decodedServiceAccount.substring(0, 20) + '...' + 
      decodedServiceAccount.substring(decodedServiceAccount.length - 20));
    
    serviceAccount = JSON.parse(decodedServiceAccount);
    
    // Verify essential fields are present
    if (!serviceAccount.project_id || !serviceAccount.private_key || !serviceAccount.client_email) {
      console.error('Service account is missing required fields');
      console.log('Available fields:', Object.keys(serviceAccount).join(', '));
      serviceAccount = null;
    } else {
      console.log(`Service account loaded for project: ${serviceAccount.project_id}`);
    }
  } catch (error) {
    console.error('Error parsing service account:', error.message);
    serviceAccount = null;
  }
} else {
  console.warn('FIREBASE_SERVICE_ACCOUNT not found, using application default credentials');
}

// Add debug logging to help diagnose the issue
console.log('Firebase initialization starting...');
console.log('Service account available:', !!serviceAccount);

try {
  admin.initializeApp({
    credential: serviceAccount
      ? admin.credential.cert(serviceAccount)
      : admin.credential.applicationDefault(),
    databaseURL: process.env.FIREBASE_DATABASE_URL
  });
  console.log('Firebase Admin SDK initialized successfully');
} catch (error) {
  console.error('Firebase initialization error:', error);
  // Continue without throwing to prevent app crash, but operations will fail
}

const firestore = admin.firestore();

// Function to verify Firebase connectivity
const verifyFirebaseConnectivity = async () => {
  try {
    console.log('Verifying Firebase connectivity...');
    
    // Try to access Firestore
    const testDoc = await firestore.collection('_connectivity_test').doc('test').get();
    console.log('Successfully connected to Firestore');
    
    // Try a simple write operation to test permissions
    try {
      // Create a temporary document with a timestamp
      await firestore.collection('_connectivity_test').doc('test').set({
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
        environment: process.env.NODE_ENV || 'development'
      });
      console.log('Successfully wrote to Firestore - write permissions confirmed');
    } catch (writeError) {
      console.error('Failed to write to Firestore:', writeError.message);
      console.error('This may indicate permission issues with the service account');
    }
    
    return true;
  } catch (error) {
    console.error('Firebase connectivity test failed:', error.message);
    console.error('Error code:', error.code);
    console.error('Error details:', error);
    return false;
  }
};

// Test function specifically for user credits update permission
const testUserCreditsUpdatePermission = async () => {
  try {
    console.log('Testing user credits update permission...');
    
    // Create a test user document if it doesn't exist
    const testUserId = 'test_user_' + Date.now();
    const testUserRef = firestore.collection('users').doc(testUserId);
    
    // First create the test user
    await testUserRef.set({
      credits: 0,
      lastCreditUpdate: admin.firestore.FieldValue.serverTimestamp(),
      testUser: true
    });
    console.log(`Created test user document: ${testUserId}`);
    
    // Now try to update just the credits field
    await testUserRef.update({
      credits: 100,
      lastCreditUpdate: admin.firestore.FieldValue.serverTimestamp()
    });
    console.log('Successfully updated test user credits - permissions confirmed');
    
    // Clean up the test user
    await testUserRef.delete();
    console.log(`Deleted test user document: ${testUserId}`);
    
    return true;
  } catch (error) {
    console.error('User credits update permission test failed:', error.message);
    console.error('Error code:', error.code);
    console.error('This is likely the same issue affecting the payment transaction credit updates');
    return false;
  }
};

// Run the connectivity test
verifyFirebaseConnectivity().then(isConnected => {
  if (isConnected) {
    console.log('Firebase is properly configured and connected');
    
    // If basic connectivity works, test the specific permission we need
    testUserCreditsUpdatePermission().then(hasPermission => {
      if (hasPermission) {
        console.log('User credits update permission confirmed - payment system should work');
      } else {
        console.error('User credits update permission denied - payment system will fail');
        console.error('Check Firestore rules and service account permissions');
      }
    });
  } else {
    console.error('Firebase connectivity issues detected - check credentials and permissions');
  }
});

module.exports = { admin, firestore };