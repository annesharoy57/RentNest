package com.example.houserentalapp

data class Booking(
    var propertyId: String? = null,
    var propertyTitle: String? = null,
    var propertyPrice: String? = null,
    var propertyLocation: String? = null,
    var propertyImage: String? = null,
    var ownerId: String? = null,
    var status: String? = "PENDING",
    var timestamp: Any? = null
)
