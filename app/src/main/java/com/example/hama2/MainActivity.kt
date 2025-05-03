package com.example.hama2

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.widget.Toast
import androidx.navigation.findNavController
import androidx.navigation.navOptions
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import com.example.hama2.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(setOf(
            R.id.FirstFragment,
            R.id.SecondFragment,
            R.id.ThirdFragment
        ))

        // Hook BottomNav into NavController
        binding.bottomNav.setOnItemSelectedListener { item ->
            // Determine which destination to go to
            val destId = when (item.itemId) {
                R.id.FirstFragment  -> R.id.FirstFragment
                R.id.SecondFragment -> R.id.SecondFragment
                R.id.ThirdFragment  -> R.id.ThirdFragment
                else -> return@setOnItemSelectedListener false
            }

            // Pop everything up to the graph root (this clears VideoFragment +
            // any other intermediates), then navigate
            navController.navigate(
                destId,
                null,
                navOptions {
                    // popUpTo the nav_graph start and clear anything above it
                    popUpTo(R.id.nav_graph) { inclusive = false }
                    // avoid multiple copies if tapping the same tab twice
                    launchSingleTop = true
                }
            )
            true
        }

        // Now just intercept the center button
        binding.centerIcon.setOnClickListener {
            if (navController.currentDestination?.id == R.id.videoFragment) {
                navController.navigateUp()
            } else {
                navController.navigate(R.id.videoFragment)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
