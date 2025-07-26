// Based on your PaymentModels.kt file
const PaymentStatus = {
  PENDING: 'PENDING',
  PROCESSING: 'PROCESSING',
  SUCCESS: 'SUCCESS',
  FAILED: 'FAILED',
  CANCELLED: 'CANCELLED'
};

class PaymentTransaction {
  constructor({
    id,
    userId,
    packageId,
    amount,
    currency,
    credits,
    status,
    paymentMethod = 'paystack',
    paystackReference,
    createdAt,
    completedAt = null,
    failureReason = null,
    paymentChannel = null
  }) {
    this.id = id;
    this.userId = userId;
    this.packageId = packageId;
    this.amount = amount;
    this.currency = currency;
    this.credits = credits;
    this.status = status;
    this.paymentMethod = paymentMethod;
    this.paystackReference = paystackReference;
    this.createdAt = createdAt;
    this.completedAt = completedAt;
    this.failureReason = failureReason;
    this.paymentChannel = paymentChannel; // Added for mobile money payments
  }
}

class PaymentRequest {
  constructor({
    packageId,
    amount,
    currency,
    email,
    userId,
    credits = 0,
    mobileNumber = null
  }) {
    this.packageId = packageId;
    this.amount = amount;
    this.currency = currency;
    this.email = email;
    this.userId = userId;
    this.credits = credits;
    this.mobileNumber = mobileNumber; // Added for mobile money payments
  }
}

class PaymentResponse {
  constructor({
    success,
    transactionId,
    paystackReference,
    message,
    credits = 0
  }) {
    this.success = success;
    this.transactionId = transactionId;
    this.paystackReference = paystackReference;
    this.message = message;
    this.credits = credits;
  }
}

module.exports = {
  PaymentStatus,
  PaymentTransaction,
  PaymentRequest,
  PaymentResponse
};