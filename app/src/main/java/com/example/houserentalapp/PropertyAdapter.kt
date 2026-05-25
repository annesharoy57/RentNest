package com.example.houserentalapp

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth

class PropertyAdapter(
    private var propertyList: List<Property>,
    private val onEditClick: ((Property) -> Unit)? = null,
    private val onDetailsClick: ((Property) -> Unit)? = null
) : RecyclerView.Adapter<PropertyAdapter.PropertyViewHolder>() {

    class PropertyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivPropertyImage: ImageView = itemView.findViewById(R.id.ivPropertyImage)
        val tvTitle: TextView = itemView.findViewById(R.id.tvPropertyTitle)
        val tvLocation: TextView = itemView.findViewById(R.id.tvPropertyLocation)
        val tvRent: TextView = itemView.findViewById(R.id.tvPropertyRent)
        val tvStatus: TextView = itemView.findViewById(R.id.tvPropertyStatus)
        val btnEdit: ImageButton = itemView.findViewById(R.id.btnEditProperty)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PropertyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_property, parent, false)
        return PropertyViewHolder(view)
    }

    override fun onBindViewHolder(holder: PropertyViewHolder, position: Int) {
        val property = propertyList[position]
        holder.tvTitle.text = property.title
        holder.tvLocation.text = property.location
        holder.tvRent.text = "৳ ${property.rentAmount} / month"

        if (property.isAvailable) {
            holder.tvStatus.text = "Available"
            holder.tvStatus.setBackgroundResource(R.drawable.bg_status_available)
        } else {
            holder.tvStatus.text = "Occupied"
            holder.tvStatus.setBackgroundResource(R.drawable.bg_status_unavailable)
        }

        if (!property.imageUrls.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(property.imageUrls!![0])
                .placeholder(R.drawable.ic_home)
                .into(holder.ivPropertyImage)
        }

        // Only show edit button if the current user is the owner and onEditClick is provided
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (onEditClick != null && property.ownerId == currentUserId) {
            holder.btnEdit.visibility = View.VISIBLE
            holder.btnEdit.setOnClickListener {
                onEditClick.invoke(property)
            }
        } else {
            holder.btnEdit.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            if (onDetailsClick != null) {
                onDetailsClick.invoke(property)
            } else {
                val intent = Intent(holder.itemView.context, PropertyDetailsActivity::class.java)
                intent.putExtra("PROPERTY_ID", property.propertyId)
                holder.itemView.context.startActivity(intent)
            }
        }
    }

    override fun getItemCount(): Int = propertyList.size

    fun updateList(newList: List<Property>) {
        propertyList = newList
        notifyDataSetChanged()
    }
}
