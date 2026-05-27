package com.example.houserentalapp

import com.google.firebase.database.PropertyName

data class Notification(
    var id: String? = null,
    var fromUserId: String? = null,
    var fromUserName: String? = null,
    var fromUserProfilePic: String? = null,
    var propertyId: String? = null,
    var propertyTitle: String? = null,
    var propertyImage: String? = null,
    var propertyPrice: String? = null,
    var propertyLocation: String? = null,
    var bookingId: String? = null,
    var type: String? = "LIKE", // LIKE, REVIEW, BOOKING_REQUEST, BOOKING_ACCEPTED, BOOKING_DECLINED, BOOKING_PAID
    var reviewText: String? = null,
    var rating: Float = 0f,
    
    @get:PropertyName("isRead")
    @set:PropertyName("isRead")
    var isRead: Boolean = false,

    var timestamp: Long = System.currentTimeMillis()
)
