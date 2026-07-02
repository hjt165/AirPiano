package com.google.mediapipe.examples.gesturerecognizer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.mediapipe.examples.gesturerecognizer.fragment.HistoryFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottom_nav)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_piano -> {
                    // Launch PianoActivity (landscape, full-screen)
                    val intent = Intent(this, PianoActivity::class.java)
                    startActivity(intent)
                    false  // Don't check this item, keep previous selection
                }
                R.id.nav_history -> {
                    loadFragment(HistoryFragment())
                    true
                }
                else -> false
            }
        }

        // Default to history page since piano opens in separate activity
        bottomNav.selectedItemId = R.id.nav_history
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onBackPressed() {
        // If on history page, go to piano; otherwise exit
        if (bottomNav.selectedItemId == R.id.nav_history) {
            bottomNav.selectedItemId = R.id.nav_piano
        } else {
            super.onBackPressed()
        }
    }
}
