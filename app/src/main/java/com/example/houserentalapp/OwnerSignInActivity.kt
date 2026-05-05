@file:Suppress("DEPRECATION")

package com.example.houserentalapp

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class OwnerSignInActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_owner_sign_in)

        auth = FirebaseAuth.getInstance()

        // Initialize Activity Result Launcher for Google Sign-In
        googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)!!
                    firebaseAuthWithGoogle(account.idToken!!)
                } catch (e: ApiException) {
                    Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val etEmail = findViewById<EditText>(R.id.etOwnerEmail)
        val etPassword = findViewById<EditText>(R.id.etOwnerPassword)
        val btnSignIn = findViewById<Button>(R.id.btnSignIn)
        val btnBack = findViewById<ImageButton>(R.id.btnBackSignIn)
        val tvForgot = findViewById<TextView>(R.id.tvForgotPassword)
        val tvSignUp = findViewById<TextView>(R.id.tvSignUp)
        val btnGoogle = findViewById<ImageButton>(R.id.btnGoogleSignIn)

        btnBack.setOnClickListener { finish() }

        btnSignIn.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Strict Professional Login for Email
            auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        if (user != null && user.isEmailVerified) {
                            checkOwnerRoleAndLogin(false)
                        } else {
                            auth.signOut()
                            Toast.makeText(this, "Please verify your email first! Check your Gmail inbox.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this, "Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        tvForgot.setOnClickListener {
            startActivity(Intent(this, OwnerForgotPass1Activity::class.java))
        }

        tvSignUp.setOnClickListener {
            startActivity(Intent(this, OwnerSignUpActivity::class.java))
        }

        btnGoogle.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun signInWithGoogle() {
        @Suppress("SpellCheckingInspection")
        val webClientId = "1020422336387-ji2ob1smpnmtl9pb0oaiqpae5sacc4dg.apps.googleusercontent.com"
        
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        
        // IMPORTANT: Sign out first to force the Account Picker to show up every time
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        checkOwnerRoleAndLogin(true) // Pass 'true' for Google login
                    }
                } else {
                    Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun checkOwnerRoleAndLogin(isGoogle: Boolean) {
        val user = auth.currentUser
        val userId = user?.uid
        if (userId != null) {
            val userRef = FirebaseDatabase.getInstance().getReference("Users").child(userId)
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // Existing User: Check if they are an Owner
                        val role = snapshot.child("role").value.toString()
                        if (role == "Owner") {
                            startActivity(Intent(this@OwnerSignInActivity, OwnerHomeActivity::class.java))
                            finish()
                        } else {
                            auth.signOut()
                            Toast.makeText(this@OwnerSignInActivity, "Access Denied: You are registered as a User", Toast.LENGTH_LONG).show()
                        }
                    } else if (isGoogle) {
                        // New Google User: Auto-register as Owner
                        val ownerMap = mapOf(
                            "name" to (user.displayName ?: "Owner"),
                            "email" to user.email,
                            "role" to "Owner"
                        )
                        userRef.setValue(ownerMap).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                startActivity(Intent(this@OwnerSignInActivity, OwnerHomeActivity::class.java))
                                finish()
                            }
                        }
                    } else {
                        // New Email User trying to Sign-In without Sign-Up
                        auth.signOut()
                        Toast.makeText(this@OwnerSignInActivity, "Account not found. Please Sign Up first.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    auth.signOut()
                    Toast.makeText(this@OwnerSignInActivity, "Database Error", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
}
