package com.example.houserentalapp

data class Payment(
    var paymentId: String? = null,
    var propertyId: String? = null,
    var propertyTitle: String? = null,
    var propertyImage: String? = null,
    var propertyLocation: String? = null,
    var userId: String? = null,
    var userName: String? = null,
    var userProfilePic: String? = null,
    var ownerId: String? = null,
    var amount: String? = null,
    var bKashNumber: String? = null,
    var transactionId: String? = null,
    var timestamp: Long = System.currentTimeMillis()
)
