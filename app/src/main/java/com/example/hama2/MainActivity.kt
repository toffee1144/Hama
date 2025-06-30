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
import com.ismaeldivita.chipnavigation.ChipNavigationBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(s: Bundle?) {
        val splashScreen = installSplashScreen()
        var keepSplash = true
        splashScreen.setKeepOnScreenCondition { keepSplash }

        CoroutineScope(Dispatchers.Main).launch {
            delay(2000)
            keepSplash = false
        }

        super.onCreate(s)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bottomNav = binding.bottomNav
        val navController = findNavController(R.id.nav_host_fragment_content_main)

        appBarConfiguration = AppBarConfiguration(setOf(
            R.id.FirstFragment,
            R.id.SecondFragment,
            R.id.ThirdFragment
        ))

        // ✅ Set default selected item on startup
        bottomNav.setItemSelected(R.id.SecondFragment)

        bottomNav.setOnItemSelectedListener { id ->
            val destId = when (id) {
                R.id.FirstFragment -> R.id.FirstFragment
                R.id.SecondFragment -> R.id.SecondFragment
                R.id.ThirdFragment -> R.id.ThirdFragment
                else -> return@setOnItemSelectedListener
            }

            navController.navigate(
                destId,
                null,
                navOptions {
                    popUpTo(R.id.nav_graph) { inclusive = false }
                    launchSingleTop = true
                }
            )
        }

        binding.centerIcon.setOnClickListener {
            if (navController.currentDestination?.id == R.id.videoFragment) {
                navController.navigateUp()
            } else {
                navController.navigate(R.id.videoFragment)
            }
        }
    }
}
