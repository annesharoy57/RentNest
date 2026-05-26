package com.example.houserentalapp

import android.content.Intent
import android.os.Bundle
import android.view.View
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

class AdminHomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var ivAdminProfile: ImageView
    private lateinit var tvAdminName: TextView
    private lateinit var tvUserCount: TextView
    private lateinit var tvOwnerCount: TextView
    private lateinit var tvHouseCount: TextView
    private lateinit var tvNotifyBadge: TextView

    private var adminListener: ValueEventListener? = null
    private var usersListener: ValueEventListener? = null
    private var propertiesListener: ValueEventListener? = null
    private var reportsListener: ValueEventListener? = null
    
    private var adminRef: DatabaseReference? = null
    private var usersRef: DatabaseReference? = null
    private var propertiesRef: DatabaseReference? = null
    private var reportsRef: DatabaseReference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_home)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        // Initialize Views
        ivAdminProfile = findViewById(R.id.ivAdminHomeProfilePic)
        tvAdminName = findViewById(R.id.tvAdminHomeName)
        tvUserCount = findViewById(R.id.tvTotalUsersCount)
        tvOwnerCount = findViewById(R.id.tvTotalOwnersCount)
        tvHouseCount = findViewById(R.id.tvTotalHousesCount)
        tvNotifyBadge = findViewById(R.id.tvAdminNotificationBadge)

        val btnLogout = findViewById<TextView>(R.id.btnAdminLogout)
        val cvUserList = findViewById<CardView>(R.id.cvUserList)
        val cvOwnerList = findViewById<CardView>(R.id.cvOwnerList)
        val cvHouseList = findViewById<CardView>(R.id.cvHouseList)
        val cvReports = findViewById<CardView>(R.id.cvManageReports)
        val btnNotify = findViewById<View>(R.id.btnAdminNotifications)
        val navProfile = findViewById<LinearLayout>(R.id.nav_admin_profile)

        val adminId = auth.currentUser?.uid
        if (adminId != null) {
            loadAdminData(adminId)
            loadStats()
            listenForReports()
        }

        btnLogout.setOnClickListener { logoutAdmin() }

        cvUserList.setOnClickListener {
            val intent = Intent(this, AdminUserListActivity::class.java)
            intent.putExtra("ROLE", "User")
            startActivity(intent)
        }

        cvOwnerList.setOnClickListener {
            val intent = Intent(this, AdminUserListActivity::class.java)
            intent.putExtra("ROLE", "Owner")
            startActivity(intent)
        }

        cvHouseList.setOnClickListener {
            startActivity(Intent(this, AdminHouseListActivity::class.java))
        }

        cvReports.setOnClickListener {
            startActivity(Intent(this, AdminReportsActivity::class.java))
        }
        
        btnNotify.setOnClickListener {
            startActivity(Intent(this, AdminReportsActivity::class.java))
        }

        navProfile.setOnClickListener {
            startActivity(Intent(this, AdminProfileActivity::class.java))
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                logoutAdmin()
            }
        })
    }

    private fun loadAdminData(adminId: String) {
        adminRef = database.child("Users").child(adminId)
        adminListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val name = snapshot.child("name").value?.toString() ?: "Admin"
                    val imageUrl = snapshot.child("profileImageUrl").value?.toString()

                    tvAdminName.text = name
                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(this@AdminHomeActivity)
                            .load(imageUrl)
                            .circleCrop()
                            .placeholder(R.drawable.ic_person)
                            .into(ivAdminProfile)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        adminRef?.addValueEventListener(adminListener!!)
    }

    private fun loadStats() {
        usersRef = database.child("Users")
        usersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var uCount = 0
                var oCount = 0
                for (user in snapshot.children) {
                    val role = user.child("role").value?.toString()
                    if (role == "User") uCount++
                    else if (role == "Owner") oCount++
                }
                tvUserCount.text = uCount.toString()
                tvOwnerCount.text = oCount.toString()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        usersRef?.addValueEventListener(usersListener!!)

        propertiesRef = database.child("Properties")
        propertiesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                tvHouseCount.text = snapshot.childrenCount.toString()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        propertiesRef?.addValueEventListener(propertiesListener!!)
    }

    private fun listenForReports() {
        reportsRef = database.child("Reports")
        reportsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var pendingCount = 0
                for (report in snapshot.children) {
                    if (report.child("status").value == "PENDING") {
                        pendingCount++
                    }
                }
                if (pendingCount > 0) {
                    tvNotifyBadge.visibility = View.VISIBLE
                    tvNotifyBadge.text = if (pendingCount > 9) "9+" else pendingCount.toString()
                } else {
                    tvNotifyBadge.visibility = View.GONE
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        reportsRef?.addValueEventListener(reportsListener!!)
    }

    private fun logoutAdmin() {
        removeListeners()
        auth.signOut()
        val intent = Intent(this, Home2Activity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun removeListeners() {
        adminListener?.let { adminRef?.removeEventListener(it) }
        usersListener?.let { usersRef?.removeEventListener(it) }
        propertiesListener?.let { propertiesRef?.removeEventListener(it) }
        reportsListener?.let { reportsRef?.removeEventListener(it) }
    }

    override fun onDestroy() {
        removeListeners()
        super.onDestroy()
    }
}
