package com.example.houserentalapp

import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class UserProfileActivity : AppCompatActivity() {

    private var isEditMode = false
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var userId: String
    private var selectedImageUri: Uri? = null

    private lateinit var ivUserProfileImage: ImageView
    private lateinit var btnEditToggle: MaterialButton
    private lateinit var btnSaveProfile: Button
    private lateinit var fabChangePic: FloatingActionButton
    
    private lateinit var etName: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etAddress: TextInputEditText
    private lateinit var etBio: TextInputEditText

    // Cloudinary Config (Matching Owner Profile)
    private val CLOUD_NAME = "dy7wl6yak" 
    private val UPLOAD_PRESET = "glsqrqbz" 

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            ivUserProfileImage.setImageURI(it)
            ivUserProfileImage.colorFilter = null
            ivUserProfileImage.imageTintList = null
            selectedImageUri = it
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        // Initialize Cloudinary safely
        try {
            val config = mapOf("cloud_name" to CLOUD_NAME)
            MediaManager.init(this, config)
        } catch (e: Exception) { }

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Users")
        userId = auth.currentUser?.uid ?: ""

        val btnBack = findViewById<ImageButton>(R.id.btnBackUserProfile)
        val navHome = findViewById<LinearLayout>(R.id.nav_user_profile_to_home)
        
        ivUserProfileImage = findViewById(R.id.ivUserProfileImage)
        btnEditToggle = findViewById(R.id.btnEditUserToggle)
        btnSaveProfile = findViewById(R.id.btnSaveUserProfile)
        fabChangePic = findViewById(R.id.fabChangeUserPic)

        etName = findViewById(R.id.etUserProfileName)
        etPhone = findViewById(R.id.etUserProfilePhone)
        etAddress = findViewById(R.id.etUserProfileAddress)
        etBio = findViewById(R.id.etUserProfileBio)

        val fields = listOf(etName, etPhone, etAddress, etBio)

        loadProfileData()

        btnBack.setOnClickListener { finish() }

        navHome.setOnClickListener {
            val intent = Intent(this, UserHomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        btnEditToggle.setOnClickListener {
            isEditMode = !isEditMode
            toggleEditMode(fields)
        }

        fabChangePic.setOnClickListener { pickImage.launch("image/*") }
        ivUserProfileImage.setOnClickListener { if (isEditMode) pickImage.launch("image/*") }

        btnSaveProfile.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) {
                etName.error = getString(R.string.name_required)
                return@setOnClickListener
            }

            if (selectedImageUri != null) {
                uploadToCloudinary(name, etPhone.text.toString(), etAddress.text.toString(), etBio.text.toString())
            } else {
                saveProfileData(name, etPhone.text.toString(), etAddress.text.toString(), etBio.text.toString(), null)
            }
        }
    }

    private fun toggleEditMode(fields: List<TextInputEditText>) {
        if (isEditMode) {
            btnEditToggle.text = getString(R.string.cancel)
            btnSaveProfile.visibility = View.VISIBLE
            fabChangePic.visibility = View.VISIBLE
            enableFields(fields, true)
        } else {
            btnEditToggle.text = getString(R.string.edit)
            btnSaveProfile.visibility = View.GONE
            fabChangePic.visibility = View.GONE
            enableFields(fields, false)
            selectedImageUri = null
            loadProfileData()
        }
    }

    private fun uploadToCloudinary(name: String, phone: String, address: String, bio: String) {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Saving Information...")
            setCancelable(false)
            show()
        }

        MediaManager.get().upload(selectedImageUri)
            .unsigned(UPLOAD_PRESET)
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    progressDialog.dismiss()
                    val imageUrl = resultData?.get("secure_url")?.toString()
                    saveProfileData(name, phone, address, bio, imageUrl)
                }
                override fun onError(requestId: String?, error: ErrorInfo?) {
                    progressDialog.dismiss()
                    Toast.makeText(this@UserProfileActivity, "Upload Failed: " + error?.description, Toast.LENGTH_LONG).show()
                }
                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
            }).dispatch()
    }

    private fun saveProfileData(name: String, phone: String, address: String, bio: String, imageUrl: String?) {
        val userUpdate = mutableMapOf<String, Any>(
            "name" to name, "phone" to phone, "address" to address, "bio" to bio
        )
        if (imageUrl != null) userUpdate["profileImageUrl"] = imageUrl

        database.child(userId).updateChildren(userUpdate).addOnSuccessListener {
            Toast.makeText(this, "Profile Updated!", Toast.LENGTH_SHORT).show()
            isEditMode = false
            btnEditToggle.text = getString(R.string.edit)
            btnSaveProfile.visibility = View.GONE
            fabChangePic.visibility = View.GONE
            enableFields(listOf(etName, etPhone, etAddress, etBio), false)
            loadProfileData()
        }
    }

    private fun loadProfileData() {
        database.child(userId).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                etName.setText(snapshot.child("name").value?.toString() ?: "")
                etPhone.setText(snapshot.child("phone").value?.toString() ?: "")
                etAddress.setText(snapshot.child("address").value?.toString() ?: "")
                etBio.setText(snapshot.child("bio").value?.toString() ?: "")
                val imageUrl = snapshot.child("profileImageUrl").value?.toString()
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(this).load(imageUrl)
                        .signature(ObjectKey(System.currentTimeMillis().toString()))
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.ic_person).circleCrop().into(ivUserProfileImage)
                    ivUserProfileImage.imageTintList = null
                }
            }
        }
    }

    private fun enableFields(fields: List<TextInputEditText>, enabled: Boolean) {
        for (field in fields) field.isEnabled = enabled
    }
}
