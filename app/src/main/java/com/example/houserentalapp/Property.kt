package com.example.houserentalapp

import com.google.firebase.database.PropertyName

data class Property(
    var propertyId: String? = null,
    var ownerId: String? = null,
    var title: String? = null,
    var description: String? = null,
    var location: String? = null,
    var rentAmount: String? = null,
    var imageUrls: List<String>? = null,
    var videoUrl: String? = null,
    var latitude: Double? = null,
    var longitude: Double? = null,
    
    @get:PropertyName("available")
    @set:PropertyName("available")
    var isAvailable: Boolean = true,

    var createdAt: Long = System.currentTimeMillis()
)
