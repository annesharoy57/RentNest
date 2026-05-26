package com.example.houserentalapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class OwnerBookingRequestsActivity : AppCompatActivity() {

    private lateinit var rvRequests: RecyclerView
    private lateinit var pbRequests: ProgressBar
    private lateinit var tvNoRequests: TextView
    private val requestList = mutableListOf<Booking>()
    private lateinit var adapter: OwnerBookingAdapter

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_owner_booking_requests)

        rvRequests = findViewById(R.id.rvBookingRequests)
        pbRequests = findViewById(R.id.pbRequests)
        tvNoRequests = findViewById(R.id.tvNoRequests)

        findViewById<View>(R.id.btnBackRequests).setOnClickListener { finish() }

        rvRequests.layoutManager = LinearLayoutManager(this)
        adapter = OwnerBookingAdapter(requestList, 
            onAccept = { booking -> updateBookingStatus(booking, "ACCEPTED") },
            onDecline = { booking -> updateBookingStatus(booking, "DECLINED") }
        )
        rvRequests.adapter = adapter

        loadBookingRequests()
    }

    private fun loadBookingRequests() {
        val ownerId = auth.currentUser?.uid ?: return
        pbRequests.visibility = View.VISIBLE

        val requestRef = database.getReference("OwnerRequests").child(ownerId)
        requestRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                requestList.clear()
                for (data in snapshot.children) {
                    val booking = data.getValue(Booking::class.java)
                    if (booking != null) {
                        booking.bookingId = data.key
                        requestList.add(booking)
                    }
                }
                pbRequests.visibility = View.GONE
                tvNoRequests.visibility = if (requestList.isEmpty()) View.VISIBLE else View.GONE
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                pbRequests.visibility = View.GONE
                Toast.makeText(this@OwnerBookingRequestsActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateBookingStatus(booking: Booking, newStatus: String) {
        val ownerId = auth.currentUser?.uid ?: return
        val userId = booking.userId ?: return
        val bookingId = booking.bookingId ?: return
        val propertyId = booking.propertyId ?: return

        // Fetch Owner Details to send in the notification properly
        database.getReference("Users").child(ownerId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val ownerName = snapshot.child("name").value?.toString() ?: "Owner"
                val ownerProfilePic = snapshot.child("profileImageUrl").value?.toString() ?: ""

                // UPDATE BOTH NODES (Owner's request node and User's booking node)
                // With the new rules, the owner has permission to write to /Bookings/userId
                val updates = hashMapOf<String, Any?>()
                updates["/OwnerRequests/$ownerId/$bookingId/status"] = newStatus
                updates["/Bookings/$userId/$propertyId/status"] = newStatus

                database.reference.updateChildren(updates)
                    .addOnSuccessListener {
                        Toast.makeText(this@OwnerBookingRequestsActivity, "Booking $newStatus", Toast.LENGTH_SHORT).show()
                        sendNotificationToUser(booking, newStatus, ownerName, ownerProfilePic)
                    }
                    .addOnFailureListener { e ->
                        // If it fails, it might be a permission issue with /Bookings/userId
                        // But with the latest rules provided by user, it should work.
                        Toast.makeText(this@OwnerBookingRequestsActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e("OwnerRequests", "Permission Denied or Error: ${e.message}")
                    }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@OwnerBookingRequestsActivity, "Failed to fetch owner info", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun sendNotificationToUser(booking: Booking, status: String, ownerName: String, ownerProfilePic: String) {
        val userId = booking.userId ?: return
        val notifyRef = database.getReference("Notifications").child(userId)
        val notifyId = notifyRef.push().key ?: return
        
        val notification = Notification(
            id = notifyId,
            fromUserId = auth.currentUser?.uid,
            fromUserName = ownerName,
            fromUserProfilePic = ownerProfilePic,
            propertyId = booking.propertyId,
            propertyTitle = booking.propertyTitle,
            propertyImage = booking.propertyImage,
            propertyPrice = booking.propertyPrice,
            bookingId = booking.bookingId,
            type = "BOOKING_$status",
            timestamp = System.currentTimeMillis()
        )
        notifyRef.child(notifyId).setValue(notification)
    }
}

class OwnerBookingAdapter(
    private val requestList: List<Booking>,
    private val onAccept: (Booking) -> Unit,
    private val onDecline: (Booking) -> Unit
) : RecyclerView.Adapter<OwnerBookingAdapter.RequestViewHolder>() {

    class RequestViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivUser: ImageView = view.findViewById(R.id.ivUserRequestAvatar)
        val tvUserName: TextView = view.findViewById(R.id.tvUserRequestName)
        val tvStatus: TextView = view.findViewById(R.id.tvRequestStatus)
        val ivProperty: ImageView = view.findViewById(R.id.ivRequestPropertyImage)
        val tvPropertyTitle: TextView = view.findViewById(R.id.tvRequestPropertyTitle)
        val tvPrice: TextView = view.findViewById(R.id.tvRequestPrice)
        val btnAccept: View = view.findViewById(R.id.btnAcceptRequest)
        val btnDecline: View = view.findViewById(R.id.btnDeclineRequest)
        val layoutActions: View = view.findViewById(R.id.layoutActionButtons)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_owner_booking_request, parent, false)
        return RequestViewHolder(view)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        val booking = requestList[position]
        val context = holder.itemView.context

        holder.tvUserName.text = booking.userName
        holder.tvPropertyTitle.text = booking.propertyTitle
        holder.tvPrice.text = "৳ ${booking.propertyPrice} / mo"
        holder.tvStatus.text = booking.status

        if (booking.status != "PENDING") {
            holder.layoutActions.visibility = View.GONE
        } else {
            holder.layoutActions.visibility = View.VISIBLE
        }

        if (!booking.userProfilePic.isNullOrEmpty()) {
            Glide.with(context).load(booking.userProfilePic).circleCrop().placeholder(R.drawable.ic_person).into(holder.ivUser)
        }
        if (!booking.propertyImage.isNullOrEmpty()) {
            Glide.with(context).load(booking.propertyImage).placeholder(R.drawable.ic_home).into(holder.ivProperty)
        }

        holder.btnAccept.setOnClickListener { onAccept(booking) }
        holder.btnDecline.setOnClickListener { onDecline(booking) }
    }

    override fun getItemCount(): Int = requestList.size
}
