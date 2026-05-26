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
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class AdminProfileActivity : AppCompatActivity() {

    private lateinit var ivAdminProfile: ImageView
    private lateinit var etAdminName: EditText
    private lateinit var tvAdminEmail: TextView
    private lateinit var btnUpdate: Button
    private lateinit var pbProfile: ProgressBar

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().getReference("Users")
    private var imageUri: Uri? = null

    // Cloudinary Config (Matching your project's settings)
    private val CLOUD_NAME = "dy7wl6yak"
    private val UPLOAD_PRESET = "glsqrqbz"

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            imageUri = result.data?.data
            ivAdminProfile.setImageURI(imageUri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_profile)

        // Initialize Cloudinary safely
        try {
            val config = mapOf("cloud_name" to CLOUD_NAME)
            MediaManager.init(this, config)
        } catch (e: Exception) { }

        ivAdminProfile = findViewById(R.id.ivAdminProfileEdit)
        etAdminName = findViewById(R.id.etAdminNameEdit)
        tvAdminEmail = findViewById(R.id.tvAdminEmailStatic)
        btnUpdate = findViewById(R.id.btnUpdateAdminProfile)
        pbProfile = findViewById(R.id.pbAdminProfile)

        findViewById<View>(R.id.btnBackAdminProfile).setOnClickListener { finish() }

        loadAdminData()

        ivAdminProfile.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(intent)
        }

        btnUpdate.setOnClickListener { updateProfile() }
    }

    private fun loadAdminData() {
        val userId = auth.currentUser?.uid ?: return
        database.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val name = snapshot.child("name").value?.toString() ?: ""
                val email = snapshot.child("email").value?.toString() ?: ""
                val imageUrl = snapshot.child("profileImageUrl").value?.toString()

                etAdminName.setText(name)
                tvAdminEmail.text = email
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(this@AdminProfileActivity).load(imageUrl).circleCrop().into(ivAdminProfile)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateProfile() {
        val newName = etAdminName.text.toString().trim()
        if (newName.isEmpty()) {
            Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        pbProfile.visibility = View.VISIBLE
        btnUpdate.isEnabled = false

        if (imageUri != null) {
            uploadToCloudinary(imageUri!!)
        } else {
            saveToDatabase(null)
        }
    }

    private fun uploadToCloudinary(uri: Uri) {
        MediaManager.get().upload(uri)
            .unsigned(UPLOAD_PRESET)
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url")?.toString()
                    saveToDatabase(url)
                }
                override fun onError(requestId: String?, error: ErrorInfo?) {
                    pbProfile.visibility = View.GONE
                    btnUpdate.isEnabled = true
                    Toast.makeText(this@AdminProfileActivity, "Upload Failed: ${error?.description}", Toast.LENGTH_SHORT).show()
                }
                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
            }).dispatch()
    }

    private fun saveToDatabase(imageUrl: String?) {
        val userId = auth.currentUser?.uid ?: return
        val updates = hashMapOf<String, Any>("name" to etAdminName.text.toString().trim())
        if (imageUrl != null) updates["profileImageUrl"] = imageUrl

        database.child(userId).updateChildren(updates).addOnSuccessListener {
            pbProfile.visibility = View.GONE
            btnUpdate.isEnabled = true
            Toast.makeText(this, "Profile Updated!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
