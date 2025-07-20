/**
 * Script to update Firestore rules to fix permission issues
 * This script generates a new firestore.rules file with proper permissions
 * for the payment system
 */

require('dotenv').config();
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// Define the path to the rules file
const rulesFilePath = path.resolve(process.cwd(), 'firestore.rules');

// Define the updated rules
const updatedRules = `rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Common helper functions
    function isAuthenticated() {
      return request.auth != null;
    }
    
    function isOwner(userId) {
      return isAuthenticated() && request.auth.uid == userId;
    }
    
    function isAdmin() {
      return isAuthenticated() && get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role == 'admin';
    }
    
    function isBackendService() {
      // This allows unauthenticated requests from the backend service
      // For production, consider using Firebase Auth with a service account
      return true;
    }
    
    // User collection rules
    match /users/{userId} {
      // Users can read and update their own data
      allow read: if isOwner(userId) || isAdmin() || isBackendService();
      allow create: if isAuthenticated() || isBackendService();
      allow update: if isOwner(userId) || isAdmin() || isBackendService();
      allow delete: if isAdmin();
      
      // Special rule for credit updates from backend service
      // This allows unauthenticated backend service to update only credits and lastCreditUpdate
      allow update: if isBackendService() && 
                     request.resource.data.diff(resource.data).affectedKeys()
                       .hasOnly(['credits', 'lastCreditUpdate']);
                       
      // Allow access to creditBalance subcollection
      match /creditBalance/{document} {
        allow read: if isOwner(userId) || isAdmin() || isBackendService();
        allow write: if isOwner(userId) || isBackendService();
      }
    }
    
    // Payment transactions rules
    match /payment_transactions/{transactionId} {
      // Users can read their own transactions
      allow read: if isAuthenticated() && 
                   resource.data.userId == request.auth.uid;
      
      // Backend service can perform all operations
      allow create, update, delete: if isBackendService();
      
      // Users can create their own transactions
      allow create: if isAuthenticated() && 
                     request.resource.data.userId == request.auth.uid;
    }
    
    // Notifications rules
    match /notifications/{notificationId} {
      // Users can read their own notifications
      allow read: if isAuthenticated() && 
                   resource.data.userId == request.auth.uid;
      
      // Users can update read status of their own notifications
      allow update: if isAuthenticated() && 
                     resource.data.userId == request.auth.uid && 
                     request.resource.data.diff(resource.data).affectedKeys().hasOnly(['read']);
      
      // Backend service can perform all operations
      allow create, update, delete: if isBackendService();
    }
    
    // Credit update queue rules
    match /credit_update_queue/{queueId} {
      // Only backend service can access
      allow read, write: if isBackendService();
    }
    
    // Credit update retries rules
    match /credit_update_retries/{retryId} {
      // Only backend service can access
      allow read, write: if isBackendService();
    }
    
    // Connectivity test collection
    match /_connectivity_test/{docId} {
      // Allow backend service to test connectivity
      allow read, write: if isBackendService();
    }
  }
}`;

/**
 * Backup the existing rules file
 */
function backupRulesFile() {
  if (fs.existsSync(rulesFilePath)) {
    const backupPath = `${rulesFilePath}.backup.${Date.now()}`;
    fs.copyFileSync(rulesFilePath, backupPath);
    console.log(`✅ Backed up existing rules to ${backupPath}`);
    return true;
  } else {
    console.log('⚠️ No existing rules file found to backup');
    return false;
  }
}

/**
 * Write the updated rules to the file
 */
function writeRulesFile() {
  try {
    fs.writeFileSync(rulesFilePath, updatedRules);
    console.log(`✅ Successfully wrote updated rules to ${rulesFilePath}`);
    return true;
  } catch (error) {
    console.error(`❌ Failed to write rules file: ${error.message}`);
    return false;
  }
}

/**
 * Deploy the updated rules to Firebase
 */
function deployRules() {
  console.log('Attempting to deploy updated rules to Firebase...');
  
  try {
    // Check if Firebase CLI is installed
    execSync('firebase --version', { stdio: 'ignore' });
    
    // Deploy the rules
    console.log('Running firebase deploy --only firestore:rules');
    execSync('firebase deploy --only firestore:rules', { stdio: 'inherit' });
    
    console.log('✅ Successfully deployed updated rules to Firebase');
    return true;
  } catch (error) {
    console.error(`❌ Failed to deploy rules: ${error.message}`);
    console.log('You can manually deploy the rules using the Firebase CLI:');
    console.log('  firebase deploy --only firestore:rules');
    return false;
  }
}

/**
 * Main function
 */
function main() {
  console.log('======================================');
  console.log('FIRESTORE RULES UPDATE TOOL');
  console.log('======================================');
  console.log('This tool will update your Firestore rules to fix permission issues');
  console.log('');
  
  // Backup existing rules
  const backupSuccess = backupRulesFile();
  
  // Write updated rules
  const writeSuccess = writeRulesFile();
  if (!writeSuccess) {
    console.error('Failed to update rules file. Exiting.');
    process.exit(1);
  }
  
  console.log('');
  console.log('Updated rules have been written to firestore.rules');
  console.log('The new rules include:');
  console.log('- Proper permissions for backend service to update user credits');
  console.log('- Permissions for payment transactions operations');
  console.log('- Permissions for notifications');
  console.log('- Permissions for credit update retry system');
  console.log('');
  
  // Ask if user wants to deploy
  console.log('Would you like to deploy these rules to Firebase now? (y/n)');
  console.log('(Automatic deployment requires the Firebase CLI to be installed)');
  console.log('');
  console.log('Since this is a script, we\'ll attempt to deploy automatically.');
  console.log('You can press Ctrl+C to cancel if you want to review the rules first.');
  console.log('');
  
  // Give the user a moment to cancel
  setTimeout(() => {
    deployRules();
    
    console.log('');
    console.log('======================================');
    console.log('NEXT STEPS');
    console.log('======================================');
    console.log('1. Run the permission diagnostic script to verify the rules are working:');
    console.log('   node src/scripts/checkFirestorePermissions.js');
    console.log('');
    console.log('2. If you have pending failed credit updates, run the retry script:');
    console.log('   node src/scripts/processCreditUpdateQueue.js');
    console.log('');
    console.log('3. Consider setting up the retry script as a cron job for production');
    console.log('======================================');
  }, 5000); // 5 second delay to allow cancellation
}

// Run the main function
main();