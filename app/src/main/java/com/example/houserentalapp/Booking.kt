package com.example.houserentalapp

data class Booking(
    var bookingId: String? = null,
    var propertyId: String? = null,
    var propertyTitle: String? = null,
    var propertyPrice: String? = null,
    var propertyLocation: String? = null,
    var propertyImage: String? = null,
    var ownerId: String? = null,
    var userId: String? = null,
    var userName: String? = null,
    var userProfilePic: String? = null,
    var status: String? = "PENDING",
    var timestamp: Any? = null
)
