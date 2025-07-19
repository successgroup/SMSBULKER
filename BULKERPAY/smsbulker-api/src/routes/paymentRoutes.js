const express = require('express');
const { initializeTransaction, verifyTransaction } = require('../controllers/paymentController');

const router = express.Router();

// Initialize payment route
router.post('/initialize', initializeTransaction);

// Verify payment route
router.get('/verify/:reference', verifyTransaction);

module.exports = router;