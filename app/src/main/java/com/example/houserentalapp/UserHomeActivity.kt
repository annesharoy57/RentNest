package com.example.houserentalapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class UserHomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var ivProfilePic: ImageView
    private lateinit var tvUserName: TextView
    private lateinit var tvNotificationBadge: TextView
    
    private var userRef: DatabaseReference? = null
    private var userListener: ValueEventListener? = null
    private var notificationRef: DatabaseReference? = null
    private var notificationListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_home)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Users")
        
        ivProfilePic = findViewById(R.id.ivUserHomeProfilePic)
        tvUserName = findViewById(R.id.tvUserHomeName)
        tvNotificationBadge = findViewById(R.id.tvUserNotificationBadge)

        val btnLogout = findViewById<TextView>(R.id.btnBackUserHome)
        val btnNotifications = findViewById<ImageButton>(R.id.btnNotificationsHeader)
        
        val navHome = findViewById<LinearLayout>(R.id.nav_home)
        val navExplore = findViewById<LinearLayout>(R.id.nav_explore)
        val navFavorite = findViewById<LinearLayout>(R.id.nav_favorite)
        val navProfile = findViewById<LinearLayout>(R.id.nav_profile)

        val catHouse = findViewById<CardView>(R.id.catHouse)
        val cvMap = findViewById<CardView>(R.id.cvMap)
        val cvBookings = findViewById<CardView>(R.id.cvUserBookings)
        val cvPayments = findViewById<CardView>(R.id.cvUserPayments)
        val cvReviews = findViewById<CardView>(R.id.cvReviews)

        val userId = auth.currentUser?.uid

        if (userId != null) {
            loadUserData(userId)
            listenForNotifications(userId)
        }

        btnLogout.setOnClickListener {
            logoutUser()
        }
        
        btnNotifications.setOnClickListener {
            startActivity(Intent(this, NotificationActivity::class.java))
        }

        navHome.setOnClickListener {
            Toast.makeText(this, "You are already on Home", Toast.LENGTH_SHORT).show()
        }

        navExplore.setOnClickListener {
            startActivity(Intent(this, ExploreActivity::class.java))
        }

        navFavorite.setOnClickListener {
            startActivity(Intent(this, FavoriteActivity::class.java))
        }

        navProfile.setOnClickListener {
            startActivity(Intent(this, UserProfileActivity::class.java))
        }

        catHouse.setOnClickListener {
            startActivity(Intent(this, SavedHousesActivity::class.java))
        }

        cvMap.setOnClickListener {
            val intent = Intent(this, MapActivity::class.java)
            intent.putExtra("PICK_MODE", false)
            startActivity(intent)
        }
        cvBookings.setOnClickListener {
            startActivity(Intent(this, UserBookingsActivity::class.java))
        }
        cvPayments.setOnClickListener {
            val intent = Intent(this, PaymentsListActivity::class.java)
            intent.putExtra("IS_OWNER", false)
            startActivity(intent)
        }
        cvReviews.setOnClickListener {
            startActivity(Intent(this, MyReviewsActivity::class.java))
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
        userListener?.let { userRef?.removeEventListener(it) }
        notificationListener?.let { notificationRef?.removeEventListener(it) }
        userListener = null
        notificationListener = null
    }

    override fun onDestroy() {
        removeListeners()
        super.onDestroy()
    }

    private fun loadUserData(userId: String) {
        userRef = database.child(userId)
        userListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val name = snapshot.child("name").value?.toString() ?: "User"
                    val imageUrl = snapshot.child("profileImageUrl").value?.toString()
                    tvUserName.text = name
                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(this@UserHomeActivity).load(imageUrl)
                            .placeholder(R.drawable.ic_person)
                            .circleCrop().into(ivProfilePic)
                        ivProfilePic.imageTintList = null
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) { }
        }
        userRef?.addValueEventListener(userListener!!)
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
