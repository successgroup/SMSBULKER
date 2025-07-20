# Payment System Maintenance Scripts

## Overview

This directory contains scripts for handling payment processing edge cases, particularly for retrying failed credit updates when Firestore permission issues occur, and for diagnosing Firestore permission problems.

## Scripts

### processCreditUpdateQueue.js

This script processes the credit update retry queue. It looks for failed credit updates that are scheduled for retry and attempts to update user credits again.

#### Features

- Processes pending credit updates in the `credit_update_queue` collection
- Implements exponential backoff for retries (5, 10, 20, 40, 80 minutes)
- Logs detailed information about each retry attempt
- Creates notifications for users when credits are successfully added

### checkFirestorePermissions.js

This script diagnoses Firestore permission issues by testing various operations that are critical for the payment system.

#### Features

- Tests read and write access to all relevant collections
- Verifies user credit update operations using both transactions and direct updates
- Tests payment transaction operations
- Tests notification creation
- Provides a detailed summary of test results with troubleshooting suggestions

### updateFirestoreRules.js

This script updates Firestore security rules to fix permission issues that may be causing credit update failures.

#### Features

- Generates a new `firestore.rules` file with proper permissions for the payment system
- Backs up the existing rules file before making changes
- Includes rules for all collections used by the payment system
- Provides special rules for backend service to update user credits
- Can automatically deploy the updated rules to Firebase (requires Firebase CLI)

## Setup Instructions

### Running Scripts Manually

You can run the scripts manually:

```bash
# Process the credit update queue
node src/scripts/processCreditUpdateQueue.js

# Check Firestore permissions
node src/scripts/checkFirestorePermissions.js

# Update Firestore rules to fix permission issues
node src/scripts/updateFirestoreRules.js
```

### Setting Up as a Cron Job

For production environments, it's recommended to set up the script as a cron job to run automatically at regular intervals.

#### Example cron setup (Linux/Unix):

```bash
# Run every 10 minutes
*/10 * * * * cd /path/to/smsbulker-api && node src/scripts/processCreditUpdateQueue.js >> /var/log/credit-update-retries.log 2>&1
```

#### Using a process manager (PM2):

```bash
pm2 start src/scripts/processCreditUpdateQueue.js --cron "*/10 * * * *" --name "credit-update-retries"
```

## Firestore Collections

The retry system uses the following Firestore collections:

1. `credit_update_queue` - Stores pending credit updates that need to be retried
2. `credit_update_retries` - Logs the history of retry attempts
3. `notifications` - Stores notifications for users about credit updates

## Manual Retry Endpoint

A REST API endpoint is available for manually triggering retries:

```
POST /retryPaymentCreditUpdate/:transactionId
```

This endpoint can be used by administrators to manually retry a failed credit update for a specific transaction.

## Troubleshooting

### Diagnosing Permission Issues

If credit updates are consistently failing with `PERMISSION_DENIED` errors, run the permission diagnostic script:

```bash
node src/scripts/checkFirestorePermissions.js
```

This script will test all relevant operations and provide a detailed report of which permissions are working and which are failing.

### Common Issues

1. **Firestore Rules**: Ensure the backend service account has permission to update user documents. Check your `firestore.rules` file to verify that the backend service account can update the `credits` and `lastCreditUpdate` fields in user documents.

2. **Firebase Service Account**: Verify the service account credentials are correctly configured in your `.env` file. The `FIREBASE_SERVICE_ACCOUNT` environment variable should contain a valid base64-encoded service account key.

3. **Logs**: Check the application logs for detailed error messages. Look for specific error codes like `permission-denied` which indicate permission issues.

4. **Retry Queue**: Check the `credit_update_queue` collection in Firestore to see if there are any pending retries that have failed multiple times.

## Monitoring

Monitor the `credit_update_queue` collection for entries with status `ERROR` or entries with high `attempts` counts, as these indicate persistent issues that may require manual intervention.