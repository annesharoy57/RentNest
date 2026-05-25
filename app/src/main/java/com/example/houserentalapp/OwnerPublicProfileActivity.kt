package com.example.houserentalapp

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.database.*

class OwnerPublicProfileActivity : AppCompatActivity() {

    private lateinit var ivProfile: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvProfession: TextView
    private lateinit var tvAddress: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvBio: TextView
    
    private lateinit var rvOwnerProperties: RecyclerView
    private lateinit var database: DatabaseReference
    private val propertyList = mutableListOf<Property>()
    private lateinit var adapter: PropertyAdapter

    private var propertiesQuery: Query? = null
    private var propertiesListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_owner_profile_view)

        val ownerId = intent.getStringExtra("OWNER_ID")
        if (ownerId == null) {
            finish()
            return
        }

        ivProfile = findViewById(R.id.ivOwnerProfilePic)
        tvName = findViewById(R.id.tvOwnerProfileName)
        tvEmail = findViewById(R.id.tvOwnerProfileEmail)
        tvProfession = findViewById(R.id.tvOwnerProfession)
        tvAddress = findViewById(R.id.tvOwnerAddress)
        tvPhone = findViewById(R.id.tvOwnerPhone)
        tvBio = findViewById(R.id.tvOwnerBio)
        
        rvOwnerProperties = findViewById(R.id.rvOwnerProperties)

        rvOwnerProperties.layoutManager = LinearLayoutManager(this)
        // Passing null for edit click to make it a read-only view
        adapter = PropertyAdapter(propertyList)
        rvOwnerProperties.adapter = adapter

        database = FirebaseDatabase.getInstance().reference
        loadOwnerData(ownerId)
        loadOwnerProperties(ownerId)

        findViewById<View>(R.id.btnBackOwnerProfile).setOnClickListener { finish() }
    }

    private fun loadOwnerData(ownerId: String) {
        database.child("Users").child(ownerId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val name = snapshot.child("name").value?.toString() ?: "Owner"
                val email = snapshot.child("email").value?.toString() ?: ""
                val profession = snapshot.child("profession").value?.toString() ?: "Not specified"
                val address = snapshot.child("address").value?.toString() ?: "Not specified"
                val phone = snapshot.child("phone").value?.toString() ?: "Not specified"
                val bio = snapshot.child("bio").value?.toString() ?: "No bio available."
                val profileUrl = snapshot.child("profileImageUrl").value?.toString()

                tvName.text = name
                tvEmail.text = email
                tvProfession.text = profession
                tvAddress.text = address
                tvPhone.text = phone
                tvBio.text = bio
                
                if (!profileUrl.isNullOrEmpty()) {
                    Glide.with(this@OwnerPublicProfileActivity).load(profileUrl).circleCrop().into(ivProfile)
                } else {
                    ivProfile.setImageResource(R.drawable.ic_person)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadOwnerProperties(ownerId: String) {
        propertiesQuery = database.child("Properties").orderByChild("ownerId").equalTo(ownerId)
        propertiesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                propertyList.clear()
                for (data in snapshot.children) {
                    val property = data.getValue(Property::class.java)
                    if (property != null) propertyList.add(property)
                }
                adapter.updateList(propertyList)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        propertiesQuery?.addValueEventListener(propertiesListener!!)
    }

    override fun onDestroy() {
        propertiesListener?.let { propertiesQuery?.removeEventListener(it) }
        super.onDestroy()
    }
}
