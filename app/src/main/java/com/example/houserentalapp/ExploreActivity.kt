package com.example.houserentalapp

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ExploreActivity : AppCompatActivity() {

    private lateinit var rvExplore: RecyclerView
    private lateinit var adapter: ExploreAdapter
    private lateinit var database: DatabaseReference
    private val propertyList = mutableListOf<Property>()
    private val auth = FirebaseAuth.getInstance()
    private val favoritesSet = mutableSetOf<String>()

    private var favRef: DatabaseReference? = null
    private var favListener: ValueEventListener? = null
    private var propertiesListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_explore)

        rvExplore = findViewById(R.id.rvExplore)
        rvExplore.layoutManager = LinearLayoutManager(this)
        
        adapter = ExploreAdapter(propertyList, auth.currentUser?.uid, favoritesSet) { property ->
            val intent = Intent(this, PropertyDetailsActivity::class.java)
            intent.putExtra("PROPERTY_ID", property.propertyId)
            startActivity(intent)
        }
        rvExplore.adapter = adapter

        database = FirebaseDatabase.getInstance().getReference("Properties")
        
        setupListeners()

        findViewById<View>(R.id.btnBackExplore).setOnClickListener { finish() }
    }

    private fun setupListeners() {
        val userId = auth.currentUser?.uid
        val pb = findViewById<View>(R.id.pbExplore)
        pb.visibility = View.VISIBLE

        if (userId != null) {
            favRef = FirebaseDatabase.getInstance().getReference("Favorites").child(userId)
            favListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    favoritesSet.clear()
                    for (data in snapshot.children) {
                        data.key?.let { favoritesSet.add(it) }
                    }
                    adapter.notifyDataSetChanged()
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            favRef?.addValueEventListener(favListener!!)
        }

        propertiesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                propertyList.clear()
                for (data in snapshot.children) {
                    val property = data.getValue(Property::class.java)
                    if (property != null) {
                        if (property.propertyId == null) property.propertyId = data.key
                        propertyList.add(property)
                    }
                }
                propertyList.sortByDescending { it.createdAt }
                adapter.notifyDataSetChanged()
                pb.visibility = View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                pb.visibility = View.GONE
                if (auth.currentUser != null) {
                    Toast.makeText(this@ExploreActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        database.addValueEventListener(propertiesListener!!)
    }

    override fun onDestroy() {
        favListener?.let { favRef?.removeEventListener(it) }
        propertiesListener?.let { database.removeEventListener(it) }
        super.onDestroy()
    }
}

class ExploreAdapter(
    private val propertyList: List<Property>,
    private val currentUserId: String?,
    private val favoritesSet: Set<String>,
    private val onDetailsClick: (Property) -> Unit
) : RecyclerView.Adapter<ExploreAdapter.ExploreViewHolder>() {

    class ExploreViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivOwnerAvatar: ImageView = view.findViewById(R.id.ivOwnerAvatar)
        val tvOwnerName: TextView = view.findViewById(R.id.tvOwnerName)
        val tvPostTime: TextView = view.findViewById(R.id.tvPostTime)
        val tvPostTitle: TextView = view.findViewById(R.id.tvPostTitle)
        val tvPostDescription: TextView = view.findViewById(R.id.tvPostDescription)
        val ivPostImage: ImageView = view.findViewById(R.id.ivPostImage)
        val ivVideoIndicator: ImageView = view.findViewById(R.id.ivVideoIndicator)
        val tvPostLocation: TextView = view.findViewById(R.id.tvPostLocation)
        val tvPostPrice: TextView = view.findViewById(R.id.tvPostPrice)
        val btnViewDetails: TextView = view.findViewById(R.id.btnViewDetails)
        val layoutOwnerInfo: View = view.findViewById(R.id.layoutOwnerInfo)
        val btnLike: TextView = view.findViewById(R.id.btnLike)
        val btnShare: TextView = view.findViewById(R.id.btnShare)
        val btnMore: ImageButton = view.findViewById(R.id.btnMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExploreViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_explore_post, parent, false)
        return ExploreViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExploreViewHolder, position: Int) {
        val property = propertyList[position]
        val context = holder.itemView.context
        
        holder.tvPostTitle.text = property.title ?: "No Title"
        holder.tvPostDescription.text = property.description ?: ""
        holder.tvPostLocation.text = property.location ?: "Unknown"
        holder.tvPostPrice.text = "৳ ${property.rentAmount ?: "0"}"
        
        val timeAgo = DateUtils.getRelativeTimeSpanString(
            property.createdAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )
        holder.tvPostTime.text = timeAgo

        holder.ivVideoIndicator.visibility = if (!property.videoUrl.isNullOrEmpty()) View.VISIBLE else View.GONE

        if (!property.imageUrls.isNullOrEmpty()) {
            Glide.with(context).load(property.imageUrls!![0]).placeholder(R.drawable.ic_home).into(holder.ivPostImage)
        } else {
            holder.ivPostImage.setImageResource(R.drawable.ic_home)
        }

        holder.tvOwnerName.text = "Loading..."
        holder.ivOwnerAvatar.setImageResource(R.drawable.ic_person)

        if (!property.ownerId.isNullOrEmpty()) {
            FirebaseDatabase.getInstance().getReference("Users").child(property.ownerId!!)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            val name = snapshot.child("name").value?.toString() ?: "User"
                            val profileUrl = snapshot.child("profileImageUrl").value?.toString()
                            holder.tvOwnerName.text = name
                            if (!profileUrl.isNullOrEmpty()) {
                                Glide.with(context).load(profileUrl).circleCrop().placeholder(R.drawable.ic_person).into(holder.ivOwnerAvatar)
                            }
                        } else {
                            holder.tvOwnerName.text = "Unknown Owner"
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }

        val isLiked = favoritesSet.contains(property.propertyId)
        if (isLiked) {
            holder.btnLike.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_favorite, 0, 0, 0)
            holder.btnLike.setTextColor(Color.RED)
            holder.btnLike.text = "Liked"
        } else {
            holder.btnLike.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_favorite_border, 0, 0, 0)
            holder.btnLike.setTextColor(Color.parseColor("#65676B"))
            holder.btnLike.text = "Like"
        }

        holder.btnLike.setOnClickListener {
            if (currentUserId == null) {
                Toast.makeText(context, "Please sign in to like", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val pId = property.propertyId ?: return@setOnClickListener

            val favRef = FirebaseDatabase.getInstance().getReference("Favorites").child(currentUserId).child(pId)
            
            if (isLiked) {
                favRef.removeValue()
            } else {
                favRef.setValue(true)
                sendNotificationToOwner(currentUserId, property)
            }
        }

        holder.btnViewDetails.setOnClickListener { onDetailsClick(property) }
        holder.layoutOwnerInfo.setOnClickListener {
            val intent = Intent(context, OwnerPublicProfileActivity::class.java)
            intent.putExtra("OWNER_ID", property.ownerId)
            context.startActivity(intent)
        }

        holder.btnMore.setOnClickListener {
            val popup = PopupMenu(context, holder.btnMore)
            popup.menuInflater.inflate(R.menu.post_options_menu, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_save -> { saveProperty(context, property); true }
                    R.id.action_review -> { showReviewDialog(context, property); true }
                    R.id.action_report -> { Toast.makeText(context, "Property Reported", Toast.LENGTH_SHORT).show(); true }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun saveProperty(context: android.content.Context, property: Property) {
        if (currentUserId == null) return
        val pId = property.propertyId ?: return
        val saveRef = FirebaseDatabase.getInstance().getReference("SavedProperties").child(currentUserId).child(pId)
        saveRef.setValue(property).addOnSuccessListener {
            Toast.makeText(context, "House saved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showReviewDialog(context: android.content.Context, property: Property) {
        if (currentUserId == null) return
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_review)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val ratingBar = dialog.findViewById<RatingBar>(R.id.ratingBar)
        val etReview = dialog.findViewById<EditText>(R.id.etReview)
        val tvRatingLabel = dialog.findViewById<TextView>(R.id.tvRatingLabel)
        val btnPost = dialog.findViewById<Button>(R.id.btnPostReview)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancelReview)

        ratingBar.setOnRatingBarChangeListener { _, rating, _ ->
            tvRatingLabel.text = "Rating (${rating.toInt()}/5)"
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnPost.setOnClickListener {
            val rating = ratingBar.rating
            if (rating == 0f) {
                Toast.makeText(context, "Please select stars", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val reviewId = FirebaseDatabase.getInstance().getReference("Reviews").push().key ?: return@setOnClickListener
            val reviewData = hashMapOf(
                "reviewId" to reviewId,
                "propertyId" to property.propertyId,
                "propertyTitle" to property.title,
                "propertyImage" to (property.imageUrls?.firstOrNull() ?: ""),
                "propertyLocation" to (property.location ?: ""),
                "userId" to currentUserId,
                "rating" to rating,
                "review" to etReview.text.toString().trim(),
                "timestamp" to ServerValue.TIMESTAMP
            )

            val rootRef = FirebaseDatabase.getInstance().reference
            val updates = hashMapOf<String, Any>()
            updates["/Reviews/${property.propertyId}/$reviewId"] = reviewData
            updates["/UserReviews/$currentUserId/$reviewId"] = reviewData

            rootRef.updateChildren(updates).addOnSuccessListener {
                Toast.makeText(context, "Review posted!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun sendNotificationToOwner(userId: String, property: Property) {
        val ownerId = property.ownerId ?: return
        if (userId == ownerId) return
        val notifyRef = FirebaseDatabase.getInstance().getReference("Notifications").child(ownerId).push()
        val notification = Notification(id = notifyRef.key, fromUserId = userId, propertyId = property.propertyId, type = "LIKE")
        notifyRef.setValue(notification)
    }

    override fun getItemCount(): Int = propertyList.size
}
