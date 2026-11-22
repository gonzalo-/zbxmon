package dev.zbxmon

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import dev.zbxmon.data.AppLanguage
import dev.zbxmon.data.PrefsStore
import dev.zbxmon.data.ThemeStyle
import dev.zbxmon.ui.MainViewModel
import dev.zbxmon.ui.Root
import dev.zbxmon.ui.ZbxTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private lateinit var appliedLanguage: AppLanguage

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Applies the in-app language to every resource lookup of this activity. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Localized.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appliedLanguage = PrefsStore(this).load().language

        // Ask for the notification permission only once
        val misc = getSharedPreferences("misc", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !misc.getBoolean("askedNotif", false)
        ) {
            misc.edit().putBoolean("askedNotif", true).apply()
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        Sched.applyService(this)

        setContent {
            // Recreate the activity when the language changes so every string is re-resolved.
            LaunchedEffect(vm.prefs.language) {
                if (vm.prefs.language != appliedLanguage) recreate()
            }
            // The terminal theme is always dark, so force light system bar icons.
            LaunchedEffect(vm.prefs.theme) {
                if (vm.prefs.theme == ThemeStyle.TERMINAL) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
                    )
                } else {
                    enableEdgeToEdge()
                }
            }
            ZbxTheme(vm.prefs.theme) {
                Surface(Modifier.fillMaxSize()) { Root(vm) }
            }
        }
    }
}
