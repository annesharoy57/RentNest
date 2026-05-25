package com.example.houserentalapp

data class Review(
    var reviewId: String? = null,
    var propertyId: String? = null,
    var propertyTitle: String? = null,
    var propertyImage: String? = null,
    var propertyLocation: String? = null,
    var userId: String? = null,
    var rating: Float = 0f,
    var review: String? = null,
    var timestamp: Any? = null
)
