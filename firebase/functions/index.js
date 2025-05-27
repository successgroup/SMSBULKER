const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

exports.deliveryReportWebhook = functions.https.onRequest(async (req, res) => {
  try {
    const { message_id, recipient, status, timestamp, error_code, error_message, user_id } = req.body;
    
    if (!message_id || !status || !user_id) {
      return res.status(400).send({ error: 'Missing required fields' });
    }
    
    // Forward to the specific user via FCM
    const message = {
      data: {
        message_id,
        recipient: recipient || '',
        status,
        timestamp: timestamp || Date.now().toString(),
        error_code: error_code || '',
        error_message: error_message || '',
        user_id
      },
      topic: `delivery_reports_${user_id}` // User-specific topic
    };
    
    await admin.messaging().send(message);
    res.status(200).send({ success: true });
  } catch (error) {
    console.error('Error processing delivery report:', error);
    res.status(500).send({ error: 'Internal server error' });
  }
});