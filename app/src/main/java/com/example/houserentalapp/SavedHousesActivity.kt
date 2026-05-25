package com.example.houserentalapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class SavedHousesActivity : AppCompatActivity() {

    private lateinit var rvSaved: RecyclerView
    private lateinit var pbSaved: ProgressBar
    private lateinit var tvNoSaved: TextView
    private lateinit var adapter: ExploreAdapter
    private val savedList = mutableListOf<Property>()
    private val favoritesSet = mutableSetOf<String>()
    
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_houses)

        rvSaved = findViewById(R.id.rvSavedHouses)
        pbSaved = findViewById(R.id.pbSaved)
        tvNoSaved = findViewById(R.id.tvNoSaved)

        findViewById<View>(R.id.btnBackSaved).setOnClickListener { finish() }

        setupRecyclerView()
        loadSavedHouses()
    }

    private fun setupRecyclerView() {
        rvSaved.layoutManager = LinearLayoutManager(this)
        adapter = ExploreAdapter(savedList, auth.currentUser?.uid, favoritesSet) { property ->
            val intent = Intent(this, PropertyDetailsActivity::class.java)
            intent.putExtra("PROPERTY_ID", property.propertyId)
            startActivity(intent)
        }
        rvSaved.adapter = adapter
    }

    private fun loadSavedHouses() {
        val userId = auth.currentUser?.uid ?: return
        pbSaved.visibility = View.VISIBLE

        val saveRef = database.getReference("SavedProperties").child(userId)
        saveRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                savedList.clear()
                for (data in snapshot.children) {
                    val prop = data.getValue(Property::class.java)
                    if (prop != null) {
                        savedList.add(prop)
                        prop.propertyId?.let { favoritesSet.add(it) }
                    }
                }
                
                pbSaved.visibility = View.GONE
                if (savedList.isEmpty()) {
                    tvNoSaved.visibility = View.VISIBLE
                } else {
                    tvNoSaved.visibility = View.GONE
                }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                pbSaved.visibility = View.GONE
                Toast.makeText(this@SavedHousesActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
