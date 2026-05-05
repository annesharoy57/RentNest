package com.example.houserentalapp

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_home)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Users")
        
        ivProfilePic = findViewById(R.id.ivUserHomeProfilePic)
        tvUserName = findViewById(R.id.tvUserHomeName)

        val btnBack = findViewById<ImageButton>(R.id.btnBackUserHome)
        val navHome = findViewById<LinearLayout>(R.id.nav_home)
        val navExplore = findViewById<LinearLayout>(R.id.nav_explore)
        val navFavorite = findViewById<LinearLayout>(R.id.nav_favorite)
        val navProfile = findViewById<LinearLayout>(R.id.nav_profile)

        val cvMap = findViewById<CardView>(R.id.cvMap)
        val cvBookings = findViewById<CardView>(R.id.cvUserBookings)
        val cvPayments = findViewById<CardView>(R.id.cvUserPayments)
        val cvReviews = findViewById<CardView>(R.id.cvReviews)

        val userId = auth.currentUser?.uid

        if (userId != null) {
            loadUserData(userId)
        }

        // Back button listener - Goes back to Role Selection (Home2Activity)
        btnBack.setOnClickListener {
            val intent = Intent(this, Home2Activity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        // Handle System Back Button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@UserHomeActivity, Home2Activity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            }
        })

        // Navigation listeners
        navHome.setOnClickListener {
            Toast.makeText(this, "You are already on Home", Toast.LENGTH_SHORT).show()
        }

        navExplore.setOnClickListener {
            startActivity(Intent(this, ExploreActivity::class.java))
        }

        navFavorite.setOnClickListener {
            Toast.makeText(this, "Favorites feature coming soon", Toast.LENGTH_SHORT).show()
        }

        navProfile.setOnClickListener {
            startActivity(Intent(this, UserProfileActivity::class.java))
        }

        // Feature listeners
        cvMap.setOnClickListener {
            Toast.makeText(this, "Map View coming soon", Toast.LENGTH_SHORT).show()
        }
        cvBookings.setOnClickListener {
            Toast.makeText(this, "Bookings feature coming soon", Toast.LENGTH_SHORT).show()
        }
        cvPayments.setOnClickListener {
            Toast.makeText(this, "Payments feature coming soon", Toast.LENGTH_SHORT).show()
        }
        cvReviews.setOnClickListener {
            Toast.makeText(this, "Reviews feature coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadUserData(userId: String) {
        database.child(userId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val name = snapshot.child("name").value?.toString() ?: "User"
                    val imageUrl = snapshot.child("profileImageUrl").value?.toString()
                    tvUserName.text = name
                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(this@UserHomeActivity).load(imageUrl)
                            .placeholder(R.drawable.ic_person)
                            .circleCrop().into(ivProfilePic)
                        ivProfilePic.imageTintList = null // Remove grey tint
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) { /* Silent */ }
        })
    }
}
