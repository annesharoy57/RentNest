package com.example.houserentalapp

import com.google.firebase.database.PropertyName

data class Notification(
    var id: String? = null,
    var fromUserId: String? = null,
    var fromUserName: String? = null,
    var fromUserProfilePic: String? = null,
    var propertyId: String? = null,
    var propertyTitle: String? = null,
    var type: String? = "LIKE",
    
    @get:PropertyName("isRead")
    @set:PropertyName("isRead")
    var isRead: Boolean = false,

    var timestamp: Long = System.currentTimeMillis()
)
