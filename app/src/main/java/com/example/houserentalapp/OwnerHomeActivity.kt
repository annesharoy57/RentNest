package com.example.houserentalapp

import android.content.Intent
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_owner_home)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Users")

        ivProfilePic = findViewById(R.id.ivOwnerHomeProfilePic)
        tvOwnerName = findViewById(R.id.tvOwnerHomeName)
        tvPropertyCount = findViewById(R.id.tvPropertyCount)

        val btnBack = findViewById<ImageButton>(R.id.btnBackOwnerHome)
        val navProfile = findViewById<LinearLayout>(R.id.nav_owner_profile)
        val cvAddProperty = findViewById<CardView>(R.id.cvAddProperty)
        val cvMyListings = findViewById<CardView>(R.id.cvMyListings)

        val userId = auth.currentUser?.uid
        if (userId != null) {
            loadOwnerData(userId)
            loadPropertyCount(userId)
        }

        btnBack.setOnClickListener { navigateToHome2() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToHome2()
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

    private fun navigateToHome2() {
        val intent = Intent(this, Home2Activity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    private fun loadOwnerData(userId: String) {
        database.child(userId).addValueEventListener(object : ValueEventListener {
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
        })
    }

    private fun loadPropertyCount(userId: String) {
        FirebaseDatabase.getInstance().getReference("Properties")
            .orderByChild("ownerId").equalTo(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    tvPropertyCount.text = snapshot.childrenCount.toString()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
}
