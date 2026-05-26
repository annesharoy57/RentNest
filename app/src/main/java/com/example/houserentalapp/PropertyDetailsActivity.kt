package com.example.houserentalapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class PropertyDetailsActivity : AppCompatActivity() {

    private lateinit var ivHeader: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvPrice: TextView
    private lateinit var ivOwnerAvatar: ImageView
    private lateinit var tvOwnerName: TextView
    private lateinit var tvOwnerProfession: TextView
    private lateinit var tvOwnerBio: TextView
    private lateinit var rvPhotos: RecyclerView
    private lateinit var layoutVideoSection: View
    private lateinit var btnPlayVideo: View
    private lateinit var btnCallOwner: View
    private lateinit var tvViewProfile: TextView
    private lateinit var btnBookNow: Button
    
    private lateinit var database: DatabaseReference
    private var propertyId: String? = null
    private var ownerPhoneNumber: String? = null
    private var latitude: Double? = null
    private var longitude: Double? = null
    private var currentProperty: Property? = null
    private val auth = FirebaseAuth.getInstance()
    
    private var propertyListener: ValueEventListener? = null
    private var bookingListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_property_details)

        propertyId = intent.getStringExtra("PROPERTY_ID")
        if (propertyId == null) {
            finish()
            return
        }

        initViews()
        database = FirebaseDatabase.getInstance().getReference("Properties").child(propertyId!!)
        loadPropertyDetails()
        checkBookingStatus()

        findViewById<View>(R.id.btnBackDetails).setOnClickListener { finish() }
        
        tvViewProfile.setOnClickListener {
            val ownerId = tvOwnerName.tag as? String
            if (ownerId != null) {
                val intent = Intent(this, OwnerPublicProfileActivity::class.java)
                intent.putExtra("OWNER_ID", ownerId)
                startActivity(intent)
            }
        }

        btnCallOwner.setOnClickListener {
            if (!ownerPhoneNumber.isNullOrEmpty()) {
                val intent = Intent(Intent.ACTION_DIAL)
                intent.data = Uri.parse("tel:$ownerPhoneNumber")
                startActivity(intent)
            } else {
                Toast.makeText(this, "Phone number not available", Toast.LENGTH_SHORT).show()
            }
        }

        tvLocation.setOnClickListener {
            if (latitude != null && longitude != null && latitude != 0.0 && longitude != 0.0) {
                val intent = Intent(this, MapActivity::class.java)
                intent.putExtra("PICK_MODE", false)
                intent.putExtra("LAT", latitude)
                intent.putExtra("LNG", longitude)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Exact location coordinates not available", Toast.LENGTH_SHORT).show()
            }
        }

        btnBookNow.setOnClickListener {
            bookProperty()
        }
    }

    private fun initViews() {
        ivHeader = findViewById(R.id.ivDetailHeader)
        tvTitle = findViewById(R.id.tvDetailTitle)
        tvLocation = findViewById(R.id.tvDetailLocation)
        tvDescription = findViewById(R.id.tvDetailDescription)
        tvPrice = findViewById(R.id.tvDetailPrice)
        ivOwnerAvatar = findViewById(R.id.ivDetailOwnerAvatar)
        tvOwnerName = findViewById(R.id.tvDetailOwnerName)
        tvOwnerProfession = findViewById(R.id.tvDetailOwnerProfession)
        tvOwnerBio = findViewById(R.id.tvDetailOwnerBio)
        rvPhotos = findViewById(R.id.rvDetailPhotos)
        layoutVideoSection = findViewById(R.id.layoutVideoSection)
        btnPlayVideo = findViewById(R.id.btnPlayVideo)
        btnCallOwner = findViewById(R.id.btnCallOwner)
        tvViewProfile = findViewById(R.id.tvViewProfile)
        btnBookNow = findViewById(R.id.btnBookNow)

        rvPhotos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun loadPropertyDetails() {
        propertyListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val property = snapshot.getValue(Property::class.java)
                if (property != null) {
                    if (property.propertyId == null) property.propertyId = snapshot.key
                    currentProperty = property
                    
                    tvTitle.text = property.title
                    tvLocation.text = property.location
                    tvDescription.text = property.description
                    tvPrice.text = "৳ ${property.rentAmount} / mo"
                    
                    this@PropertyDetailsActivity.latitude = property.latitude
                    this@PropertyDetailsActivity.longitude = property.longitude

                    if (!property.isAvailable) {
                        btnBookNow.isEnabled = false
                        btnBookNow.text = "Occupied"
                        btnBookNow.alpha = 0.5f
                    } else if (property.ownerId == auth.currentUser?.uid) {
                        btnBookNow.visibility = View.GONE
                    } else {
                        btnBookNow.visibility = View.VISIBLE
                    }

                    if (!property.imageUrls.isNullOrEmpty()) {
                        Glide.with(this@PropertyDetailsActivity).load(property.imageUrls!![0]).into(ivHeader)
                        rvPhotos.adapter = PhotosAdapter(property.imageUrls!!)
                    }

                    if (!property.videoUrl.isNullOrEmpty()) {
                        layoutVideoSection.visibility = View.VISIBLE
                        btnPlayVideo.setOnClickListener { _ ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(property.videoUrl))
                            startActivity(intent)
                        }
                    } else {
                        layoutVideoSection.visibility = View.GONE
                    }

                    loadOwnerInfo(property.ownerId)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        database.addValueEventListener(propertyListener!!)
    }

    private fun checkBookingStatus() {
        val userId = auth.currentUser?.uid ?: return
        val pId = propertyId ?: return

        bookingListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && currentProperty?.ownerId != userId) {
                    val status = snapshot.child("status").value?.toString() ?: "PENDING"
                    when (status) {
                        "PENDING" -> {
                            btnBookNow.isEnabled = false
                            btnBookNow.text = "Booking Pending"
                            btnBookNow.alpha = 0.7f
                        }
                        "ACCEPTED" -> {
                            btnBookNow.isEnabled = false
                            btnBookNow.text = "Booking Accepted"
                            btnBookNow.alpha = 0.7f
                        }
                        "PAID" -> {
                            btnBookNow.isEnabled = false
                            btnBookNow.text = "Property Rented"
                            btnBookNow.alpha = 0.7f
                        }
                        "DECLINED" -> {
                            btnBookNow.isEnabled = true
                            btnBookNow.text = "Re-book Property"
                            btnBookNow.alpha = 1.0f
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        FirebaseDatabase.getInstance().getReference("Bookings").child(userId).child(pId)
            .addValueEventListener(bookingListener!!)
    }

    private fun loadOwnerInfo(ownerId: String?) {
        if (ownerId == null) {
            tvOwnerName.text = "Unknown Owner"
            return
        }
        tvOwnerName.tag = ownerId
        FirebaseDatabase.getInstance().getReference("Users").child(ownerId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val name = snapshot.child("name").value?.toString() ?: "User"
                        val profession = snapshot.child("profession").value?.toString() ?: "Property Owner"
                        val bio = snapshot.child("bio").value?.toString() ?: "No bio available."
                        val profileUrl = snapshot.child("profileImageUrl").value?.toString()
                        ownerPhoneNumber = snapshot.child("phone").value?.toString()

                        tvOwnerName.text = name
                        tvOwnerProfession.text = profession
                        tvOwnerBio.text = bio
                        
                        if (!profileUrl.isNullOrEmpty()) {
                            Glide.with(this@PropertyDetailsActivity)
                                .load(profileUrl)
                                .circleCrop()
                                .placeholder(R.drawable.ic_person)
                                .into(ivOwnerAvatar)
                        } else {
                            ivOwnerAvatar.setImageResource(R.drawable.ic_person)
                        }
                    } else {
                        tvOwnerName.text = "Profile incomplete"
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun bookProperty() {
        val user = auth.currentUser
        val property = currentProperty
        if (user == null) {
            Toast.makeText(this, "Please sign in to book", Toast.LENGTH_SHORT).show()
            return
        }
        if (property == null) return
        
        if (property.ownerId == user.uid) {
            Toast.makeText(this, "You cannot book your own property", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = user.uid
        val ownerId = property.ownerId ?: return
        val pId = property.propertyId ?: return
        
        val userRef = FirebaseDatabase.getInstance().getReference("Users").child(userId)
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userName = snapshot.child("name").value?.toString() ?: "User"
                val userProfilePic = snapshot.child("profileImageUrl").value?.toString() ?: ""

                val bookingId = FirebaseDatabase.getInstance().getReference("Bookings").child(userId).push().key ?: return
                
                val bookingData = Booking(
                    bookingId = bookingId,
                    propertyId = pId,
                    propertyTitle = property.title,
                    propertyPrice = property.rentAmount,
                    propertyLocation = property.location,
                    propertyImage = (property.imageUrls?.firstOrNull() ?: ""),
                    ownerId = ownerId,
                    userId = userId,
                    userName = userName,
                    userProfilePic = userProfilePic,
                    status = "PENDING",
                    timestamp = ServerValue.TIMESTAMP
                )

                val updates = hashMapOf<String, Any>()
                updates["/Bookings/$userId/$pId"] = bookingData
                updates["/OwnerRequests/$ownerId/$bookingId"] = bookingData

                FirebaseDatabase.getInstance().reference.updateChildren(updates).addOnSuccessListener {
                    sendBookingNotificationToOwner(ownerId, property, userName, userProfilePic, bookingId)
                    Toast.makeText(this@PropertyDetailsActivity, "Booking request sent!", Toast.LENGTH_SHORT).show()
                    
                    val intent = Intent(this@PropertyDetailsActivity, UserBookingsActivity::class.java)
                    startActivity(intent)
                    finish()
                }.addOnFailureListener {
                    Toast.makeText(this@PropertyDetailsActivity, "Error: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun sendBookingNotificationToOwner(ownerId: String, property: Property, userName: String, userProfilePic: String, bookingId: String) {
        val notifyRef = FirebaseDatabase.getInstance().getReference("Notifications").child(ownerId)
        val notifyId = notifyRef.push().key ?: return
        
        val notification = Notification(
            id = notifyId,
            fromUserId = auth.currentUser?.uid,
            fromUserName = userName,
            fromUserProfilePic = userProfilePic,
            propertyId = property.propertyId,
            propertyTitle = property.title,
            propertyImage = (property.imageUrls?.firstOrNull() ?: ""),
            propertyPrice = property.rentAmount,
            bookingId = bookingId,
            type = "BOOKING_REQUEST",
            timestamp = System.currentTimeMillis()
        )
        notifyRef.child(notifyId).setValue(notification)
    }

    override fun onDestroy() {
        propertyListener?.let { database.removeEventListener(it) }
        bookingListener?.let { 
            val userId = auth.currentUser?.uid
            val pId = propertyId
            if (userId != null && pId != null) {
                FirebaseDatabase.getInstance().getReference("Bookings").child(userId).child(pId).removeEventListener(it)
            }
        }
        super.onDestroy()
    }
}

class PhotosAdapter(private val photos: List<String>) : RecyclerView.Adapter<PhotosAdapter.PhotoViewHolder>() {
    class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPhoto: ImageView = view.findViewById(R.id.ivPhotoItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        Glide.with(holder.itemView.context)
            .load(photos[position])
            .centerCrop()
            .placeholder(R.drawable.ic_home)
            .into(holder.ivPhoto)
    }

    override fun getItemCount(): Int = photos.size
}
