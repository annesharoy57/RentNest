package com.example.houserentalapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class OwnerHomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var ivProfilePic: ImageView
    private lateinit var tvOwnerName: TextView
    private lateinit var tvPropertyCount: TextView
    private lateinit var tvNotificationBadge: TextView

    private var ownerListener: ValueEventListener? = null
    private var propertyListener: ValueEventListener? = null
    private var notificationListener: ValueEventListener? = null
    
    private var ownerRef: DatabaseReference? = null
    private var propertyQuery: Query? = null
    private var notificationRef: DatabaseReference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_owner_home)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Users")

        ivProfilePic = findViewById(R.id.ivOwnerHomeProfilePic)
        tvOwnerName = findViewById(R.id.tvOwnerHomeName)
        tvPropertyCount = findViewById(R.id.tvPropertyCount)
        tvNotificationBadge = findViewById(R.id.tvNotificationBadge)

        val btnLogout = findViewById<TextView>(R.id.btnBackOwnerHome)
        val navProfile = findViewById<LinearLayout>(R.id.nav_owner_profile)
        val cvAddProperty = findViewById<CardView>(R.id.cvAddProperty)
        val cvMyListings = findViewById<CardView>(R.id.cvMyListings)
        val btnNotifications = findViewById<ImageButton>(R.id.ivOwnerNotifications)

        val userId = auth.currentUser?.uid
        if (userId != null) {
            loadOwnerData(userId)
            loadPropertyCount(userId)
            listenForNotifications(userId)
        }

        btnLogout.setOnClickListener { logoutUser() }
        
        btnNotifications.setOnClickListener {
            startActivity(Intent(this, NotificationActivity::class.java))
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                logoutUser()
            }
        })

        navProfile.setOnClickListener {
            startActivity(Intent(this, OwnerProfileActivity::class.java))
        }

        cvAddProperty.setOnClickListener {
            startActivity(Intent(this, AddPropertyActivity::class.java))
        }

        cvMyListings.setOnClickListener {
            startActivity(Intent(this, MyListingsActivity::class.java))
        }
    }

    private fun logoutUser() {
        removeListeners()
        auth.signOut()
        val intent = Intent(this, Home2Activity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun removeListeners() {
        ownerListener?.let { ownerRef?.removeEventListener(it) }
        propertyListener?.let { propertyQuery?.removeEventListener(it) }
        notificationListener?.let { notificationRef?.removeEventListener(it) }
        
        ownerListener = null
        propertyListener = null
        notificationListener = null
    }

    override fun onDestroy() {
        removeListeners()
        super.onDestroy()
    }

    private fun loadOwnerData(userId: String) {
        ownerRef = database.child(userId)
        ownerListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val name = snapshot.child("name").value?.toString() ?: "Owner"
                    val imageUrl = snapshot.child("profileImageUrl").value?.toString()

                    tvOwnerName.text = name
                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(this@OwnerHomeActivity)
                            .load(imageUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(R.drawable.ic_person)
                            .circleCrop()
                            .into(ivProfilePic)
                        ivProfilePic.colorFilter = null
                        ivProfilePic.imageTintList = null
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ownerRef?.addValueEventListener(ownerListener!!)
    }

    private fun loadPropertyCount(userId: String) {
        propertyQuery = FirebaseDatabase.getInstance().getReference("Properties")
            .orderByChild("ownerId").equalTo(userId)
        
        propertyListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                tvPropertyCount.text = snapshot.childrenCount.toString()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        propertyQuery?.addValueEventListener(propertyListener!!)
    }

    private fun listenForNotifications(userId: String) {
        notificationRef = FirebaseDatabase.getInstance().getReference("Notifications").child(userId)
        
        notificationListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var unreadCount = 0
                for (data in snapshot.children) {
                    val notification = data.getValue(Notification::class.java)
                    if (notification != null && !notification.isRead) {
                        unreadCount++
                    }
                }
                
                if (unreadCount > 0) {
                    tvNotificationBadge.visibility = View.VISIBLE
                    tvNotificationBadge.text = if (unreadCount > 9) "9+" else unreadCount.toString()
                } else {
                    tvNotificationBadge.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        notificationRef?.addValueEventListener(notificationListener!!)
    }
}
