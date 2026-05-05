package com.example.houserentalapp

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class  OwnerForgotPass1Activity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_owner_forgot_pass1)

        val btnBack = findViewById<ImageButton>(R.id.btnBackForgot1)
        val btnSend = findViewById<Button>(R.id.btnSendCode)
        val etEmail = findViewById<EditText>(R.id.etForgotEmail)

        btnBack.setOnClickListener { finish() }

        btnSend.setOnClickListener {
            val email = etEmail.text.toString().trim()

            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Firebase logic to send password reset email
            FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Reset link sent to your email!", Toast.LENGTH_LONG).show()
                        
                        // Proceed to the next screen as per your UI flow
                        val intent = Intent(this, OwnerForgotPass2Activity::class.java)
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "Failed to send email: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }
}