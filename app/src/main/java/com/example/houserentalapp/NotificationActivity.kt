package com.example.houserentalapp

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class NotificationActivity : AppCompatActivity() {

    private lateinit var rvNotifications: RecyclerView
    private lateinit var pbNotifications: View
    private lateinit var tvNoNotifications: TextView
    private lateinit var adapter: NotificationAdapter
    private val notificationList = mutableListOf<Notification>()
    
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().getReference("Notifications")
    
    private var notificationsQuery: Query? = null
    private var notificationListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        rvNotifications = findViewById(R.id.rvNotifications)
        pbNotifications = findViewById(R.id.pbNotifications)
        tvNoNotifications = findViewById(R.id.tvNoNotifications)

        findViewById<View>(R.id.btnBackNotifications).setOnClickListener { finish() }

        rvNotifications.layoutManager = LinearLayoutManager(this)
        adapter = NotificationAdapter(notificationList)
        rvNotifications.adapter = adapter

        loadNotifications()
    }

    private fun loadNotifications() {
        val userId = auth.currentUser?.uid ?: return
        pbNotifications.visibility = View.VISIBLE

        notificationsQuery = database.child(userId).orderByChild("timestamp")
        notificationListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                notificationList.clear()
                val updates = mutableMapOf<String, Any?>()
                
                for (data in snapshot.children) {
                    val notification = data.getValue(Notification::class.java)
                    if (notification != null) {
                        notificationList.add(notification)
                        
                        // Mark as read when viewed
                        if (!notification.isRead) {
                            updates["${data.key}/isRead"] = true
                        }
                    }
                }
                
                // Perform batch update to avoid multiple triggers
                if (updates.isNotEmpty()) {
                    database.child(userId).updateChildren(updates)
                }

                notificationList.reverse() // Newest first
                adapter.notifyDataSetChanged()
                pbNotifications.visibility = View.GONE
                
                if (notificationList.isEmpty()) {
                    tvNoNotifications.visibility = View.VISIBLE
                } else {
                    tvNoNotifications.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                pbNotifications.visibility = View.GONE
                // Only show toast if it's not a permission error due to logout
                if (auth.currentUser != null) {
                    Toast.makeText(this@NotificationActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        notificationsQuery?.addValueEventListener(notificationListener!!)
    }

    override fun onDestroy() {
        notificationListener?.let { notificationsQuery?.removeEventListener(it) }
        super.onDestroy()
    }
}

class NotificationAdapter(private val notifications: List<Notification>) : 
    RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivUser: ImageView = view.findViewById(R.id.ivNotificationUser)
        val tvText: TextView = view.findViewById(R.id.tvNotificationText)
        val tvTime: TextView = view.findViewById(R.id.tvNotificationTime)
        val viewIndicator: View = view.findViewById(R.id.viewUnreadIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        
        holder.tvText.text = "${notification.fromUserName} liked your house: ${notification.propertyTitle}"
        
        val timeAgo = DateUtils.getRelativeTimeSpanString(
            notification.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )
        holder.tvTime.text = timeAgo
        
        if (!notification.fromUserProfilePic.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(notification.fromUserProfilePic)
                .circleCrop()
                .placeholder(R.drawable.ic_person)
                .into(holder.ivUser)
        } else {
            holder.ivUser.setImageResource(R.drawable.ic_person)
        }

        holder.viewIndicator.visibility = if (notification.isRead) View.GONE else View.VISIBLE
    }

    override fun getItemCount(): Int = notifications.size
}
