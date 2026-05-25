package com.example.houserentalapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : AppCompatActivity() {
    
    private var keepSplashScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition.
        val splashScreen = installSplashScreen()
        
        super.onCreate(savedInstanceState)

        // Keep the splash screen on-screen for 2-3 seconds
        splashScreen.setKeepOnScreenCondition { keepSplashScreen }
        Handler(Looper.getMainLooper()).postDelayed({
            keepSplashScreen = false
        }, 2000) // 2000ms = 2 seconds

        // 1. Remove the limits to let the background image fill the entire screen
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        setContentView(R.layout.activity_main)

        // 2. Setup the "Get Started" button logic
        val btnGetStarted = findViewById<Button>(R.id.btnGetStarted)
        btnGetStarted.setOnClickListener {
            val intent = Intent(this, Home2Activity::class.java)
            startActivity(intent)
        }
    }
}
