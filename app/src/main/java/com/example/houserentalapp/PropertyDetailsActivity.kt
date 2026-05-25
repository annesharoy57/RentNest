package com.example.houserentalapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
    
    private lateinit var database: DatabaseReference
    private var propertyId: String? = null
    private var ownerPhoneNumber: String? = null
    private var latitude: Double? = null
    private var longitude: Double? = null

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

        findViewById<View>(R.id.btnBookNow).setOnClickListener {
            Toast.makeText(this, "Booking request sent to owner!", Toast.LENGTH_SHORT).show()
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

        rvPhotos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun loadPropertyDetails() {
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val property = snapshot.getValue(Property::class.java)
                property?.let {
                    tvTitle.text = it.title
                    tvLocation.text = it.location
                    tvDescription.text = it.description
                    tvPrice.text = "৳ ${it.rentAmount} / mo"
                    
                    this@PropertyDetailsActivity.latitude = it.latitude
                    this@PropertyDetailsActivity.longitude = it.longitude

                    if (!it.imageUrls.isNullOrEmpty()) {
                        Glide.with(this@PropertyDetailsActivity).load(it.imageUrls!![0]).into(ivHeader)
                        rvPhotos.adapter = PhotosAdapter(it.imageUrls!!)
                    }

                    if (!it.videoUrl.isNullOrEmpty()) {
                        layoutVideoSection.visibility = View.VISIBLE
                        btnPlayVideo.setOnClickListener { _ ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it.videoUrl))
                            startActivity(intent)
                        }
                    } else {
                        layoutVideoSection.visibility = View.GONE
                    }

                    // Crucial: Load the actual name from the Users node
                    loadOwnerInfo(it.ownerId)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
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
