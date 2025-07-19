const admin = require('firebase-admin');
const dotenv = require('dotenv');

dotenv.config();

// Initialize Firebase Admin with credentials from environment variable
let serviceAccount;

if (process.env.FIREBASE_SERVICE_ACCOUNT) {
  // Decode the base64 encoded service account key
  const decodedServiceAccount = Buffer.from(
    process.env.FIREBASE_SERVICE_ACCOUNT,
    'base64'
  ).toString('utf-8');
  
  serviceAccount = JSON.parse(decodedServiceAccount);
} else {
  console.warn('FIREBASE_SERVICE_ACCOUNT not found, using application default credentials');
}

admin.initializeApp({
  credential: serviceAccount 
    ? admin.credential.cert(serviceAccount)
    : admin.credential.applicationDefault(),
  databaseURL: process.env.FIREBASE_DATABASE_URL
});

const firestore = admin.firestore();

module.exports = { admin, firestore };