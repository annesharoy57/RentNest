package com.example.houserentalapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.database.*

class EditPropertyActivity : AppCompatActivity() {

    private lateinit var etTitle: TextInputEditText
    private lateinit var etDescription: TextInputEditText
    private lateinit var etLocation: TextInputEditText
    private lateinit var etRent: TextInputEditText
    private lateinit var switchAvailability: SwitchCompat
    private lateinit var layoutImages: LinearLayout
    private lateinit var btnAddImage: View
    private lateinit var tvVideoStatus: TextView
    private lateinit var layoutVideoActions: LinearLayout
    private lateinit var btnPlayVideo: Button
    private lateinit var btnRemoveVideo: Button
    private lateinit var btnUpdate: Button
    private lateinit var btnDelete: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tilLocation: TextInputLayout

    private var propertyId: String? = null
    private lateinit var database: DatabaseReference
    
    private val existingImageUrls = mutableListOf<String>()
    private val newImageUris = mutableListOf<Uri>()
    private var existingVideoUrl: String? = null
    private var newVideoUri: Uri? = null
    private var latitude: Double? = null
    private var longitude: Double? = null

    // Cloudinary Config
    private val CLOUD_NAME = "dy7wl6yak"
    private val UPLOAD_PRESET = "glsqrqbz"

    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data?.clipData != null) {
                val count = data.clipData!!.itemCount
                for (i in 0 until count) {
                    val uri = data.clipData!!.getItemAt(i).uri
                    addNewImageToLayout(uri)
                }
            } else if (data?.data != null) {
                addNewImageToLayout(data.data!!)
            }
        }
    }

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            newVideoUri = result.data?.data
            tvVideoStatus.text = "New Video Selected"
            tvVideoStatus.setTextColor(getColor(R.color.primary_blue))
            layoutVideoActions.visibility = View.VISIBLE
            btnPlayVideo.visibility = View.GONE
        }
    }

    private val mapLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            latitude = data?.getDoubleExtra("LAT", 0.0)
            longitude = data?.getDoubleExtra("LNG", 0.0)
            val address = data?.getStringExtra("ADDRESS")
            if (address != null) {
                etLocation.setText(address)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_property)

        propertyId = intent.getStringExtra("PROPERTY_ID")
        if (propertyId == null) {
            Toast.makeText(this, "Error: Property ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        database = FirebaseDatabase.getInstance().getReference("Properties").child(propertyId!!)

        initCloudinary()
        initViews()
        loadPropertyData()

        findViewById<ImageButton>(R.id.btnBackEditProperty).setOnClickListener { finish() }

        btnAddImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            pickImagesLauncher.launch(intent)
        }

        findViewById<View>(R.id.btnEditSelectVideo).setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            pickVideoLauncher.launch(intent)
        }

        tilLocation.setEndIconOnClickListener {
            val intent = Intent(this, MapActivity::class.java)
            intent.putExtra("PICK_MODE", true)
            // Pass current coords if they exist
            if (latitude != null && longitude != null) {
                intent.putExtra("LAT", latitude)
                intent.putExtra("LNG", longitude)
            }
            mapLauncher.launch(intent)
        }

        btnPlayVideo.setOnClickListener {
            existingVideoUrl?.let { url ->
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(Uri.parse(url), "video/*")
                startActivity(intent)
            }
        }

        btnRemoveVideo.setOnClickListener {
            existingVideoUrl = null
            newVideoUri = null
            tvVideoStatus.text = "Select/Change Video"
            tvVideoStatus.setTextColor(android.graphics.Color.GRAY)
            layoutVideoActions.visibility = View.GONE
        }

        btnUpdate.setOnClickListener { updateProperty() }
        btnDelete.setOnClickListener { showDeleteConfirmation() }
    }

    private fun initCloudinary() {
        try {
            val config = mapOf("cloud_name" to CLOUD_NAME)
            MediaManager.init(this, config)
        } catch (e: Exception) { }
    }

    private fun initViews() {
        etTitle = findViewById(R.id.etEditPropertyTitle)
        etDescription = findViewById(R.id.etEditPropertyDescription)
        etLocation = findViewById(R.id.etEditPropertyLocation)
        etRent = findViewById(R.id.etEditPropertyRent)
        switchAvailability = findViewById(R.id.switchEditAvailability)
        layoutImages = findViewById(R.id.layoutImagesEdit)
        btnAddImage = findViewById(R.id.btnAddImageEdit)
        tvVideoStatus = findViewById(R.id.tvEditVideoStatus)
        layoutVideoActions = findViewById(R.id.layoutVideoActions)
        btnPlayVideo = findViewById(R.id.btnPlayVideo)
        btnRemoveVideo = findViewById(R.id.btnRemoveVideo)
        btnUpdate = findViewById(R.id.btnUpdateProperty)
        btnDelete = findViewById(R.id.btnDeleteProperty)
        progressBar = findViewById(R.id.pbEditProperty)
        tilLocation = findViewById(R.id.tilEditLocation)
    }

    private fun loadPropertyData() {
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val property = snapshot.getValue(Property::class.java)
                property?.let {
                    etTitle.setText(it.title)
                    etDescription.setText(it.description)
                    etLocation.setText(it.location)
                    etRent.setText(it.rentAmount)
                    switchAvailability.isChecked = it.isAvailable
                    updateAvailabilityText(it.isAvailable)
                    
                    latitude = it.latitude
                    longitude = it.longitude

                    // Load Images
                    existingImageUrls.clear()
                    it.imageUrls?.let { urls -> existingImageUrls.addAll(urls) }
                    refreshImagesLayout()

                    // Load Video
                    existingVideoUrl = it.videoUrl
                    if (!existingVideoUrl.isNullOrEmpty()) {
                        tvVideoStatus.text = "Video Uploaded"
                        tvVideoStatus.setTextColor(getColor(R.color.primary_blue))
                        layoutVideoActions.visibility = View.VISIBLE
                        btnPlayVideo.visibility = View.VISIBLE
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        switchAvailability.setOnCheckedChangeListener { _, isChecked ->
            updateAvailabilityText(isChecked)
        }
    }

    private fun refreshImagesLayout() {
        layoutImages.removeAllViews()
        
        // Add the "Add Button" back first
        if (btnAddImage.parent != null) {
            (btnAddImage.parent as ViewGroup).removeView(btnAddImage)
        }
        layoutImages.addView(btnAddImage)

        // Show Existing Images
        existingImageUrls.forEach { url -> addImageThumbnail(url, true) }

        // Show New Selected Images
        newImageUris.forEach { uri -> addImageThumbnail(uri, false) }
    }

    private fun addImageThumbnail(source: Any, isExisting: Boolean) {
        val frame = FrameLayout(this)
        val params = LinearLayout.LayoutParams(250, 250)
        params.setMargins(8, 8, 8, 8)
        frame.layoutParams = params

        val imageView = ImageView(this)
        imageView.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        
        if (source is String) Glide.with(this).load(source).into(imageView)
        else if (source is Uri) imageView.setImageURI(source)

        val deleteBtn = ImageButton(this)
        deleteBtn.setImageResource(android.R.drawable.ic_menu_delete)
        val btnParams = FrameLayout.LayoutParams(70, 70)
        btnParams.gravity = android.view.Gravity.TOP or android.view.Gravity.END
        deleteBtn.layoutParams = btnParams
        deleteBtn.setBackgroundColor(android.graphics.Color.parseColor("#80000000"))
        deleteBtn.setColorFilter(android.graphics.Color.WHITE)

        deleteBtn.setOnClickListener {
            if (isExisting) existingImageUrls.remove(source as String)
            else newImageUris.remove(source as Uri)
            refreshImagesLayout()
        }

        frame.addView(imageView)
        frame.addView(deleteBtn)
        
        layoutImages.addView(frame)
    }

    private fun addNewImageToLayout(uri: Uri) {
        newImageUris.add(uri)
        refreshImagesLayout()
    }

    private fun updateAvailabilityText(isAvailable: Boolean) {
        switchAvailability.text = if (isAvailable) "Available" else "Occupied"
    }

    private fun updateProperty() {
        val title = etTitle.text.toString().trim()
        val description = etDescription.text.toString().trim()
        val location = etLocation.text.toString().trim()
        val rent = etRent.text.toString().trim()

        if (title.isEmpty() || description.isEmpty() || location.isEmpty() || rent.isEmpty() || (existingImageUrls.isEmpty() && newImageUris.isEmpty())) {
            Toast.makeText(this, "Please fill all fields and keep at least one photo", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        if (newImageUris.isNotEmpty()) {
            uploadNewImages(title, description, location, rent)
        } else if (newVideoUri != null) {
            uploadNewVideo(title, description, location, rent, existingImageUrls)
        } else {
            saveUpdatesToDatabase(title, description, location, rent, existingImageUrls, existingVideoUrl)
        }
    }

    private fun uploadNewImages(title: String, desc: String, loc: String, rent: String) {
        val allUrls = mutableListOf<String>()
        allUrls.addAll(existingImageUrls)
        var count = 0

        for (uri in newImageUris) {
            MediaManager.get().upload(uri)
                .unsigned(UPLOAD_PRESET)
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String?) {}
                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                        resultData?.get("secure_url")?.toString()?.let { allUrls.add(it) }
                        count++
                        if (count == newImageUris.size) {
                            if (newVideoUri != null) uploadNewVideo(title, desc, loc, rent, allUrls)
                            else saveUpdatesToDatabase(title, desc, loc, rent, allUrls, existingVideoUrl)
                        }
                    }
                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        count++
                        if (count == newImageUris.size) {
                            if (newVideoUri != null) uploadNewVideo(title, desc, loc, rent, allUrls)
                            else saveUpdatesToDatabase(title, desc, loc, rent, allUrls, existingVideoUrl)
                        }
                    }
                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                }).dispatch()
        }
    }

    private fun uploadNewVideo(title: String, desc: String, loc: String, rent: String, imgs: List<String>) {
        MediaManager.get().upload(newVideoUri)
            .unsigned(UPLOAD_PRESET)
            .option("resource_type", "video")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val vUrl = resultData?.get("secure_url")?.toString()
                    saveUpdatesToDatabase(title, desc, loc, rent, imgs, vUrl)
                }
                override fun onError(requestId: String?, error: ErrorInfo?) {
                    saveUpdatesToDatabase(title, desc, loc, rent, imgs, existingVideoUrl)
                }
                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
            }).dispatch()
    }

    private fun saveUpdatesToDatabase(title: String, desc: String, loc: String, rent: String, imgs: List<String>, video: String?) {
        val updates = HashMap<String, Any?>()
        updates["title"] = title
        updates["description"] = desc
        updates["location"] = loc
        updates["rentAmount"] = rent
        updates["available"] = switchAvailability.isChecked
        updates["imageUrls"] = imgs
        updates["videoUrl"] = video
        updates["latitude"] = latitude
        updates["longitude"] = longitude

        database.updateChildren(updates).addOnCompleteListener { task ->
            setLoading(false)
            if (task.isSuccessful) {
                Toast.makeText(this, "Property updated successfully", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnUpdate.isEnabled = !isLoading
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Delete Property")
            .setMessage("Are you sure you want to delete this property listing?")
            .setPositiveButton("Delete") { _, _ ->
                database.removeValue().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Property deleted", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
