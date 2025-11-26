package com.example.foresteye

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity
import android.view.ViewGroup.LayoutParams
import android.view.animation.AlphaAnimation

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🌈 Gradient background layout
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    ContextCompat.getColor(this@MainActivity, R.color.teal_700),
                    ContextCompat.getColor(this@MainActivity, R.color.teal_200)
                )
            )
        }

        // 🌿 App name text
        val title = TextView(this).apply {
            text = "🌲 ForestEye"
            textSize = 34f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            gravity = Gravity.CENTER
            startAnimation(AlphaAnimation(0f, 1f).apply {
                duration = 1500
                fillAfter = true
            })
        }

        layout.addView(title)
        setContentView(layout)

        // 🔥 Initialize Firebase
        auth = FirebaseAuth.getInstance()

        // ⏳ Splash delay (2 seconds)
        Handler(Looper.getMainLooper()).postDelayed({
            checkUserLogin()
        }, 2000)
    }

    private fun checkUserLogin() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // User already logged in → go to Dashboard
            startActivity(Intent(this, DashboardActivity::class.java))
        } else {
            // Not logged in → go to LoginActivity
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}
