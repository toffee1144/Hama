package com.example.hama2

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.findNavController
import androidx.navigation.navOptions
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import com.example.hama2.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(s: Bundle?) {

        val splashScreen = installSplashScreen()

        // Keep splash for 2 seconds using a flag
        var keepSplash = true
        splashScreen.setKeepOnScreenCondition { keepSplash }

        // Delay in a coroutine
        CoroutineScope(Dispatchers.Main).launch {
            delay(2000) // 2-second delay
            keepSplash = false // Allow transition
        }

        super.onCreate(s)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(setOf(
            R.id.FirstFragment,
            R.id.SecondFragment,
            R.id.ThirdFragment
        ))

        // Set the color of the active indicator (pill) in the BottomNavigationView
        binding.bottomNav.itemActiveIndicatorColor =
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_primary))


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
                    launchSingleTop = false
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
