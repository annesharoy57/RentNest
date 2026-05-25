package com.example.houserentalapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.*

class AddPropertyActivity : AppCompatActivity() {

    private lateinit var etTitle: TextInputEditText
    private lateinit var etDescription: TextInputEditText
    private lateinit var etLocation: TextInputEditText
    private lateinit var etRent: TextInputEditText
    private lateinit var switchAvailability: SwitchCompat
    private lateinit var layoutImages: LinearLayout
    private lateinit var tvVideoStatus: TextView
    private lateinit var btnSave: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tilLocation: TextInputLayout

    private val imageUris = mutableListOf<Uri>()
    private var videoUri: Uri? = null
    private var latitude: Double? = null
    private var longitude: Double? = null

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().getReference("Properties")

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
                    addImageToLayout(uri)
                }
            } else if (data?.data != null) {
                addImageToLayout(data.data!!)
            }
        }
    }

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            videoUri = result.data?.data
            tvVideoStatus.text = "Video Selected"
            tvVideoStatus.setTextColor(getColor(R.color.primary_blue))
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
        setContentView(R.layout.activity_add_property)

        initCloudinary()
        initViews()

        findViewById<ImageButton>(R.id.btnBackAddProperty).setOnClickListener { finish() }

        findViewById<View>(R.id.btnAddImage).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            pickImagesLauncher.launch(intent)
        }

        findViewById<View>(R.id.btnSelectVideo).setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            pickVideoLauncher.launch(intent)
        }

        tilLocation.setEndIconOnClickListener {
            val intent = Intent(this, MapActivity::class.java)
            intent.putExtra("PICK_MODE", true)
            mapLauncher.launch(intent)
        }

        btnSave.setOnClickListener {
            saveHouse()
        }
    }

    private fun initCloudinary() {
        try {
            val config = mapOf("cloud_name" to CLOUD_NAME)
            MediaManager.init(this, config)
        } catch (e: Exception) { }
    }

    private fun initViews() {
        etTitle = findViewById(R.id.etPropertyTitle)
        etDescription = findViewById(R.id.etPropertyDescription)
        etLocation = findViewById(R.id.etPropertyLocation)
        etRent = findViewById(R.id.etPropertyRent)
        switchAvailability = findViewById(R.id.switchAvailability)
        layoutImages = findViewById(R.id.layoutImages)
        tvVideoStatus = findViewById(R.id.tvVideoStatus)
        btnSave = findViewById(R.id.btnSaveProperty)
        progressBar = findViewById(R.id.pbAddProperty)
        tilLocation = findViewById(R.id.tilLocation)
    }

    private fun addImageToLayout(uri: Uri) {
        imageUris.add(uri)
        val imageView = ImageView(this)
        val params = LinearLayout.LayoutParams(250, 250)
        params.setMargins(8, 8, 8, 8)
        imageView.layoutParams = params
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.setImageURI(uri)
        layoutImages.addView(imageView, 0)
    }

    private fun saveHouse() {
        // Step 1: Check Login
        if (auth.currentUser == null) {
            Toast.makeText(this, "Error: You must be logged in!", Toast.LENGTH_LONG).show()
            return
        }

        val title = etTitle.text.toString().trim()
        val description = etDescription.text.toString().trim()
        val location = etLocation.text.toString().trim()
        val rent = etRent.text.toString().trim()

        if (title.isEmpty() || description.isEmpty() || location.isEmpty() || rent.isEmpty() || imageUris.isEmpty()) {
            Toast.makeText(this, "Please fill all fields and add at least one photo", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        val uploadedUrls = mutableListOf<String>()
        var count = 0

        for (uri in imageUris) {
            MediaManager.get().upload(uri)
                .unsigned(UPLOAD_PRESET)
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String?) {}
                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                        resultData?.get("secure_url")?.toString()?.let { uploadedUrls.add(it) }
                        count++
                        if (count == imageUris.size) {
                            if (videoUri != null) uploadVideo(title, description, location, rent, uploadedUrls)
                            else saveToDatabase(title, description, location, rent, uploadedUrls, null)
                        }
                    }
                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        count++
                        if (count == imageUris.size) {
                            if (uploadedUrls.isEmpty()) {
                                setLoading(false)
                                Toast.makeText(this@AddPropertyActivity, "Image upload failed!", Toast.LENGTH_SHORT).show()
                            } else {
                                saveToDatabase(title, description, location, rent, uploadedUrls, null)
                            }
                        }
                    }
                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                }).dispatch()
        }
    }

    private fun uploadVideo(title: String, desc: String, loc: String, rent: String, imgs: List<String>) {
        MediaManager.get().upload(videoUri)
            .unsigned(UPLOAD_PRESET)
            .option("resource_type", "video")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val vUrl = resultData?.get("secure_url")?.toString()
                    saveToDatabase(title, desc, loc, rent, imgs, vUrl)
                }
                override fun onError(requestId: String?, error: ErrorInfo?) {
                    saveToDatabase(title, desc, loc, rent, imgs, null)
                }
                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
            }).dispatch()
    }

    private fun saveToDatabase(title: String, desc: String, loc: String, rent: String, imgs: List<String>, video: String?) {
        val id = database.push().key ?: return
        val house = Property(id, auth.currentUser?.uid, title, desc, loc, rent, imgs, video, latitude, longitude, switchAvailability.isChecked)
        
        database.child(id).setValue(house).addOnCompleteListener {
            setLoading(false)
            if (it.isSuccessful) {
                Toast.makeText(this, "House Added Successfully!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                // This message appears if your Rules in Step 1 are not updated!
                Toast.makeText(this, "Database Permission Denied. Check Firebase Rules.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.GONE else View.GONE // Error in original code? Visiblity should be isLoading?
        // Wait, original code was: progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        // I will fix it to be correct.
        findViewById<ProgressBar>(R.id.pbAddProperty).visibility = if (isLoading) View.VISIBLE else View.GONE
        btnSave.isEnabled = !isLoading
    }
}
