package com.example.houserentalapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.database.*

class AdminHouseListActivity : AppCompatActivity() {

    private lateinit var rvHouseList: RecyclerView
    private lateinit var pbHouseList: ProgressBar
    private val houseList = mutableListOf<Property>()
    private lateinit var adapter: AdminHouseAdapter

    private val database = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_house_list)

        rvHouseList = findViewById(R.id.rvAdminHouseList)
        pbHouseList = findViewById(R.id.pbHouseList)

        findViewById<View>(R.id.btnBackHouseList).setOnClickListener { finish() }

        rvHouseList.layoutManager = LinearLayoutManager(this)
        adapter = AdminHouseAdapter(houseList) { property ->
            showDeleteConfirmation(property)
        }
        rvHouseList.adapter = adapter

        loadHouses()
    }

    private fun loadHouses() {
        pbHouseList.visibility = View.VISIBLE
        database.child("Properties").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                houseList.clear()
                for (data in snapshot.children) {
                    val property = data.getValue(Property::class.java)
                    if (property != null) {
                        property.propertyId = data.key
                        houseList.add(property)
                    }
                }
                pbHouseList.visibility = View.GONE
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                pbHouseList.visibility = View.GONE
            }
        })
    }

    private fun showDeleteConfirmation(property: Property) {
        AlertDialog.Builder(this)
            .setTitle("Delete Property")
            .setMessage("Are you sure you want to delete '${property.title}'?")
            .setPositiveButton("Delete") { _, _ -> deleteHouse(property) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteHouse(property: Property) {
        val id = property.propertyId ?: return
        database.child("Properties").child(id).removeValue().addOnSuccessListener {
            Toast.makeText(this, "Property deleted successfully", Toast.LENGTH_SHORT).show()
        }
    }
}

class AdminHouseAdapter(
    private val houses: List<Property>,
    private val onDeleteClick: (Property) -> Unit
) : RecyclerView.Adapter<AdminHouseAdapter.HouseViewHolder>() {

    class HouseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivHouse: ImageView = view.findViewById(R.id.ivAdminHouseImage)
        val tvTitle: TextView = view.findViewById(R.id.tvAdminHouseTitle)
        val tvLocation: TextView = view.findViewById(R.id.tvAdminHouseLocation)
        val tvOwner: TextView = view.findViewById(R.id.tvAdminHouseOwner)
        val tvPrice: TextView = view.findViewById(R.id.tvAdminHousePrice)
        val tvStatus: TextView = view.findViewById(R.id.tvAdminHouseStatus)
        val tvDesc: TextView = view.findViewById(R.id.tvAdminHouseDesc)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteHouse)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HouseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_house, parent, false)
        return HouseViewHolder(view)
    }

    override fun onBindViewHolder(holder: HouseViewHolder, position: Int) {
        val property = houses[position]
        holder.tvTitle.text = property.title ?: "No Title"
        holder.tvLocation.text = property.location ?: "No Location"
        holder.tvOwner.text = "Owner ID: ${property.ownerId}"
        holder.tvPrice.text = "৳ ${property.rentAmount}"
        holder.tvStatus.text = if (property.isAvailable) "Available" else "Rented/Unavailable"
        holder.tvDesc.text = property.description ?: "No description"

        if (!property.imageUrls.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(property.imageUrls!![0])
                .placeholder(R.drawable.ic_home)
                .into(holder.ivHouse)
        } else {
            holder.ivHouse.setImageResource(R.drawable.ic_home)
        }

        holder.btnDelete.setOnClickListener { onDeleteClick(property) }
    }

    override fun getItemCount(): Int = houses.size
}
