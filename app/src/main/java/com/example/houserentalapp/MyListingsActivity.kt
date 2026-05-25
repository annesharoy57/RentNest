package com.example.houserentalapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MyListingsActivity : AppCompatActivity() {

    private lateinit var rvMyListings: RecyclerView
    private lateinit var pbMyListings: ProgressBar
    private lateinit var tvEmptyListings: TextView
    private lateinit var adapter: PropertyAdapter
    private val propertyList = mutableListOf<Property>()

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().getReference("Properties")
    
    private var propertyQuery: Query? = null
    private var propertyListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_listings)

        rvMyListings = findViewById(R.id.rvMyListings)
        pbMyListings = findViewById(R.id.pbMyListings)
        tvEmptyListings = findViewById(R.id.tvEmptyListings)

        findViewById<ImageButton>(R.id.btnBackMyListings).setOnClickListener { finish() }

        setupRecyclerView()
        loadMyProperties()
    }

    private fun setupRecyclerView() {
        adapter = PropertyAdapter(propertyList) { property ->
            val intent = Intent(this, EditPropertyActivity::class.java)
            intent.putExtra("PROPERTY_ID", property.propertyId)
            startActivity(intent)
        }
        rvMyListings.layoutManager = LinearLayoutManager(this)
        rvMyListings.adapter = adapter
    }

    private fun loadMyProperties() {
        val userId = auth.currentUser?.uid ?: return
        pbMyListings.visibility = View.VISIBLE

        propertyQuery = database.orderByChild("ownerId").equalTo(userId)
        propertyListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                propertyList.clear()
                for (data in snapshot.children) {
                    val property = data.getValue(Property::class.java)
                    property?.let { propertyList.add(it) }
                }
                pbMyListings.visibility = View.GONE
                if (propertyList.isEmpty()) {
                    tvEmptyListings.visibility = View.VISIBLE
                } else {
                    tvEmptyListings.visibility = View.GONE
                }
                adapter.updateList(propertyList)
            }

            override fun onCancelled(error: DatabaseError) {
                pbMyListings.visibility = View.GONE
                if (auth.currentUser != null) {
                    Toast.makeText(this@MyListingsActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        propertyQuery?.addValueEventListener(propertyListener!!)
    }

    override fun onDestroy() {
        propertyListener?.let { propertyQuery?.removeEventListener(it) }
        super.onDestroy()
    }
}
