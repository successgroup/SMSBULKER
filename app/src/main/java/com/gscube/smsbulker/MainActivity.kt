package com.gscube.smsbulker

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.gscube.smsbulker.databinding.ActivityMainBinding
import com.gscube.smsbulker.ui.auth.AuthViewModel
import com.gscube.smsbulker.ui.auth.LoginActivity
import com.gscube.smsbulker.SmsBulkerApplication
import javax.inject.Inject
import androidx.navigation.fragment.NavHostFragment

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    
    private val authViewModel: AuthViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[AuthViewModel::class.java]
    }
    private var isNavigating = false

    // Add this import at the top with other imports
    

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as SmsBulkerApplication).appComponent.inject(this)
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

        // Check login state before setting up UI
        if (!authViewModel.isLoggedIn()) {
            Log.d(TAG, "User not logged in, redirecting to login")
            startLoginActivity()
            return
        }

        Log.d(TAG, "User is logged in, setting up UI")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        // Get the NavHostFragment first
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHostFragment.navController
        
        // Set up top-level destinations (these won't show back button)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home,
                R.id.nav_contacts,
                R.id.nav_templates,
                R.id.nav_analytics,
                R.id.nav_account
            )
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.bottomNavigation.setupWithNavController(navController)
    }

    // Remove the setupNavigation() method since we've moved its logic directly into onCreate
    private fun setupNavigation() {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home,
                R.id.nav_contacts,
                R.id.nav_templates,
                R.id.nav_analytics,
                R.id.nav_account
            )
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.bottomNavigation.setupWithNavController(navController)
    }

    private fun handleLogout() {
        if (isNavigating) {
            Log.d(TAG, "Already navigating, ignoring logout")
            return
        }

        Log.d(TAG, "Handling logout")
        isNavigating = true
        authViewModel.logout()
        startLoginActivity()
    }

    private fun startLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onDestroy() {
        super.onDestroy()
        isNavigating = false
    }
}