package com.example.houserentalapp

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.format.DateUtils
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
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
        adapter = NotificationAdapter(notificationList) { notification ->
            processPayment(notification)
        }
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
                        notification.id = data.key
                        notificationList.add(notification)
                        
                        if (!notification.isRead) {
                            updates["${data.key}/isRead"] = true
                        }
                    }
                }
                
                if (updates.isNotEmpty() && auth.currentUser != null) {
                    database.child(userId).updateChildren(updates)
                }

                notificationList.reverse()
                adapter.notifyDataSetChanged()
                pbNotifications.visibility = View.GONE
                tvNoNotifications.visibility = if (notificationList.isEmpty()) View.VISIBLE else View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                pbNotifications.visibility = View.GONE
                // SILENT: No Toast here to prevent logout annoyance
                Log.e("NotificationActivity", "Database error: ${error.message}")
            }
        }
        notificationsQuery?.addValueEventListener(notificationListener!!)
    }

    private fun processPayment(notification: Notification) {
        val userId = auth.currentUser?.uid ?: return
        val ownerId = notification.fromUserId ?: return
        val bookingId = notification.bookingId ?: ""
        val propertyId = notification.propertyId ?: ""
        
        val priceStr = notification.propertyPrice?.replace("[^0-9]".toRegex(), "") ?: "0"
        val amount = priceStr.toLongOrNull() ?: 0L
        
        if (amount <= 0) {
            Toast.makeText(this, "Invalid payment amount", Toast.LENGTH_SHORT).show()
            return
        }

        val revenueRef = FirebaseDatabase.getInstance().getReference("Revenue").child(ownerId)
        revenueRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentRevenue = currentData.getValue(Long::class.java) ?: 0L
                currentData.value = currentRevenue + amount
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (committed) {
                    val rootRef = FirebaseDatabase.getInstance().reference
                    val updates = hashMapOf<String, Any?>()
                    updates["/Notifications/$userId/${notification.id}/type"] = "BOOKING_PAID"
                    if (bookingId.isNotEmpty()) updates["/OwnerRequests/$ownerId/$bookingId/status"] = "PAID"
                    if (propertyId.isNotEmpty()) updates["/Bookings/$userId/$propertyId/status"] = "PAID"
                    
                    rootRef.updateChildren(updates)
                    Toast.makeText(this@NotificationActivity, "Payment Successful!", Toast.LENGTH_LONG).show()
                } else {
                    Log.e("NotificationActivity", "Payment failed: ${error?.message}")
                }
            }
        })
    }

    override fun onDestroy() {
        notificationListener?.let { notificationsQuery?.removeEventListener(it) }
        super.onDestroy()
    }
}

class NotificationAdapter(
    private val notifications: List<Notification>,
    private val onPayClick: (Notification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivUser: ImageView = view.findViewById(R.id.ivNotificationUser)
        val tvText: TextView = view.findViewById(R.id.tvNotificationText)
        val tvTime: TextView = view.findViewById(R.id.tvNotificationTime)
        val viewIndicator: View = view.findViewById(R.id.viewUnreadIndicator)
        val divider: View = view.findViewById(R.id.dividerNotification)
        val layoutProperty: View = view.findViewById(R.id.layoutNotificationPropertySection)
        val ivProperty: ImageView = view.findViewById(R.id.ivNotificationProperty)
        val tvPropertyTitle: TextView = view.findViewById(R.id.tvNotificationPropertyTitle)
        val tvPropertyPrice: TextView = view.findViewById(R.id.tvNotificationPropertyPrice)
        val layoutReview: View = view.findViewById(R.id.layoutReviewDetails)
        val ratingBar: RatingBar = view.findViewById(R.id.notificationRatingBar)
        val tvReviewText: TextView = view.findViewById(R.id.tvNotificationReviewText)
        val btnPayNow: Button = view.findViewById(R.id.btnPayNow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        val context = holder.itemView.context
        
        holder.btnPayNow.visibility = View.GONE
        holder.layoutProperty.visibility = View.GONE
        holder.divider.visibility = View.GONE
        holder.layoutReview.visibility = View.GONE

        val userName = notification.fromUserName ?: "User"
        
        when (notification.type) {
            "LIKE" -> {
                setStyledText(holder.tvText, userName, " liked your house")
                showPropertyDetails(holder, notification)
            }
            "REVIEW" -> {
                setStyledText(holder.tvText, userName, " reviewed your house")
                holder.layoutReview.visibility = View.VISIBLE
                holder.ratingBar.rating = notification.rating
                holder.tvReviewText.text = notification.reviewText
                showPropertyDetails(holder, notification)
            }
            "BOOKING_REQUEST" -> {
                setStyledText(holder.tvText, userName, " sent a booking request")
                showPropertyDetails(holder, notification)
            }
            "BOOKING_ACCEPTED" -> {
                setStyledText(holder.tvText, userName, " accepted your booking for ${notification.propertyTitle}")
                holder.btnPayNow.visibility = View.VISIBLE
                holder.btnPayNow.text = "Pay Now"
                holder.btnPayNow.isEnabled = true
                holder.btnPayNow.alpha = 1.0f
                holder.btnPayNow.setOnClickListener { onPayClick(notification) }
                showPropertyDetails(holder, notification)
            }
            "BOOKING_PAID" -> {
                setStyledText(holder.tvText, userName, "'s payment confirmed!")
                holder.btnPayNow.visibility = View.VISIBLE
                holder.btnPayNow.text = "Paid Successfully"
                holder.btnPayNow.isEnabled = false
                holder.btnPayNow.alpha = 0.7f
                showPropertyDetails(holder, notification)
            }
            "BOOKING_DECLINED" -> {
                setStyledText(holder.tvText, userName, " declined your booking for ${notification.propertyTitle}")
                showPropertyDetails(holder, notification)
            }
            else -> {
                holder.tvText.text = userName
            }
        }
        
        val timeAgo = DateUtils.getRelativeTimeSpanString(
            notification.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )
        holder.tvTime.text = timeAgo
        
        if (!notification.fromUserProfilePic.isNullOrEmpty()) {
            Glide.with(context).load(notification.fromUserProfilePic)
                .circleCrop().placeholder(R.drawable.ic_person).into(holder.ivUser)
        } else {
            holder.ivUser.setImageResource(R.drawable.ic_person)
        }

        holder.viewIndicator.visibility = if (notification.isRead) View.GONE else View.VISIBLE
    }

    private fun setStyledText(textView: TextView, name: String, action: String) {
        val spannable = SpannableString("$name$action")
        spannable.setSpan(StyleSpan(Typeface.BOLD), 0, name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        textView.text = spannable
    }

    private fun showPropertyDetails(holder: NotificationViewHolder, notification: Notification) {
        holder.divider.visibility = View.VISIBLE
        holder.layoutProperty.visibility = View.VISIBLE
        holder.tvPropertyTitle.text = notification.propertyTitle ?: "Property"
        val price = notification.propertyPrice
        holder.tvPropertyPrice.text = if (price == null || price == "null" || price.isEmpty()) "৳ 0 / mo" 
                                      else if (price.startsWith("৳")) price 
                                      else "৳ $price / mo"
        if (!notification.propertyImage.isNullOrEmpty()) {
            Glide.with(holder.itemView.context).load(notification.propertyImage)
                .placeholder(R.drawable.ic_home).into(holder.ivProperty)
        } else {
            holder.ivProperty.setImageResource(R.drawable.ic_home)
        }
    }

    override fun getItemCount(): Int = notifications.size
}
