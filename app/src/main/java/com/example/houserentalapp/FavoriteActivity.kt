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

class FavoriteActivity : AppCompatActivity() {

    private lateinit var rvFavorites: RecyclerView
    private lateinit var pbFavorite: ProgressBar
    private lateinit var tvNoFavorites: TextView
    private lateinit var adapter: ExploreAdapter
    private val favoriteList = mutableListOf<Property>()
    private val favoritesSet = mutableSetOf<String>()
    
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    private var favRef: DatabaseReference? = null
    private var favListener: ValueEventListener? = null
    private val propertiesRef = FirebaseDatabase.getInstance().getReference("Properties")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorite)

        rvFavorites = findViewById(R.id.rvFavorites)
        pbFavorite = findViewById(R.id.pbFavorite)
        tvNoFavorites = findViewById(R.id.tvNoFavorites)

        findViewById<View>(R.id.btnBackFavorite).setOnClickListener { finish() }

        setupRecyclerView()
        loadFavoritesData()
    }

    private fun setupRecyclerView() {
        rvFavorites.layoutManager = LinearLayoutManager(this)
        adapter = ExploreAdapter(favoriteList, auth.currentUser?.uid, favoritesSet) { property ->
            val intent = Intent(this, PropertyDetailsActivity::class.java)
            intent.putExtra("PROPERTY_ID", property.propertyId)
            startActivity(intent)
        }
        rvFavorites.adapter = adapter
    }

    private fun loadFavoritesData() {
        val userId = auth.currentUser?.uid ?: return
        pbFavorite.visibility = View.VISIBLE

        favRef = database.getReference("Favorites").child(userId)

        favListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                favoritesSet.clear()
                if (!snapshot.exists()) {
                    favoriteList.clear()
                    adapter.notifyDataSetChanged()
                    pbFavorite.visibility = View.GONE
                    tvNoFavorites.visibility = View.VISIBLE
                    return
                }

                val favIds = mutableListOf<String>()
                for (data in snapshot.children) {
                    val id = data.key ?: continue
                    favIds.add(id)
                    favoritesSet.add(id)
                }

                propertiesRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(propSnapshot: DataSnapshot) {
                        favoriteList.clear()
                        for (id in favIds) {
                            val prop = propSnapshot.child(id).getValue(Property::class.java)
                            if (prop != null) {
                                prop.propertyId = id
                                favoriteList.add(prop)
                            }
                        }
                        
                        pbFavorite.visibility = View.GONE
                        tvNoFavorites.visibility = if (favoriteList.isEmpty()) View.VISIBLE else View.GONE
                        adapter.notifyDataSetChanged()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        pbFavorite.visibility = View.GONE
                        if (auth.currentUser != null) {
                            Toast.makeText(this@FavoriteActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                pbFavorite.visibility = View.GONE
                if (auth.currentUser != null) {
                    Toast.makeText(this@FavoriteActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        favRef?.addValueEventListener(favListener!!)
    }

    override fun onDestroy() {
        favListener?.let { favRef?.removeEventListener(it) }
        super.onDestroy()
    }
}
