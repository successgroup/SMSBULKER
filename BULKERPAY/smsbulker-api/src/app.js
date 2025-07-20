const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const paymentRoutes = require('./routes/paymentRoutes');

const app = express();

// Trust proxy - required for applications behind a reverse proxy (like on Render.com)
// This ensures correct client IP detection for rate limiting
app.set('trust proxy', 1);

// Security middleware
app.use(helmet());
app.use(cors());

// Rate limiting
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 100, // limit each IP to 100 requests per windowMs
  standardHeaders: true,
  legacyHeaders: false,
  // Properly handle IP identification behind proxies
  // The 'trust proxy' setting above ensures this works correctly
  trustProxy: true,
  // Skip validation after first request to avoid excessive logging
  validate: {
    xForwardedForHeader: false,
    default: true
  }
});
app.use(limiter);

// Body parsing middleware
app.use(express.json());
app.use(express.urlencoded({ extended: false }));

// Routes
app.use('/api/payments', paymentRoutes);

// Direct routes for mobile app compatibility
app.post('/initializePaystackTransaction', (req, res) => {
  // Forward the request to the proper controller
  const { initializeTransaction } = require('./controllers/paymentController');
  return initializeTransaction(req, res);
});

// Direct route for verifying Paystack transactions (mobile app compatibility)
app.get('/verifyPaystackTransaction/:reference', (req, res) => {
  // Forward the request to the proper controller
  const { verifyTransaction } = require('./controllers/paymentController');
  return verifyTransaction(req, res);
});

// Route for manually retrying failed credit updates
app.post('/retryPaymentCreditUpdate/:transactionId', (req, res) => {
  // Forward the request to the proper controller
  const { manualRetryUpdate } = require('./controllers/paymentController');
  return manualRetryUpdate(req, res);
});

// Health check endpoint
app.get('/health', (req, res) => {
  res.status(200).json({ status: 'ok' });
});

// 404 handler
app.use((req, res) => {
  res.status(404).json({ message: 'Route not found' });
});

// Error handler
app.use((err, req, res, next) => {
  console.error(err.stack);
  res.status(500).json({
    message: 'Something went wrong!',
    error: process.env.NODE_ENV === 'development' ? err.message : undefined
  });
});

module.exports = app;