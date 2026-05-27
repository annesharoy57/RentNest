package com.example.houserentalapp

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class AdminLoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_login)

        val nameField = findViewById<EditText>(R.id.etAdminName)
        val passField = findViewById<EditText>(R.id.etAdminPassword)
        val loginBtn = findViewById<Button>(R.id.btnAdminLogin)
        val backBtn = findViewById<ImageButton>(R.id.btnBackLogin)

        backBtn.setOnClickListener { finish() }

        loginBtn.setOnClickListener {
            val name = nameField.text.toString().trim()
            val pass = passField.text.toString().trim()

            // New Admin Credentials
            if (name == "ar" && pass == "1234") {
                // Background Firebase Login
                FirebaseAuth.getInstance().signInWithEmailAndPassword("admin@rentnest.com", "admin1234")
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = task.result?.user
                            if (user != null) {
                                val adminRef = FirebaseDatabase.getInstance().getReference("Users").child(user.uid)
                                
                                // FIX: Check if name already exists before overwriting it
                                adminRef.child("name").get().addOnSuccessListener { snapshot ->
                                    val updates = mutableMapOf<String, Any>(
                                        "email" to "admin@rentnest.com",
                                        "role" to "Admin"
                                    )
                                    
                                    // Only set name to "Admin" if it's currently empty/null
                                    if (!snapshot.exists() || snapshot.value == null) {
                                        updates["name"] = "Admin"
                                    }
                                    
                                    adminRef.updateChildren(updates).addOnCompleteListener {
                                        val intent = Intent(this, AdminHomeActivity::class.java)
                                        startActivity(intent)
                                        finish()
                                    }
                                }.addOnFailureListener {
                                    // Fallback: just go to home if read fails
                                    val intent = Intent(this, AdminHomeActivity::class.java)
                                    startActivity(intent)
                                    finish()
                                }
                            }
                        } else {
                            Toast.makeText(this, "Login Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Wrong Name or Password!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}