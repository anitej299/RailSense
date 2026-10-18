package org.railsense.mobile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.railsense.mobile.ui.RailSenseAppUi
import org.railsense.mobile.ui.RailSenseTheme

class MainActivity : ComponentActivity() {
    private val model: RailSenseViewModel by viewModels()
    private var pendingDemo=false
    private var pendingGps=true
    private val permission=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { model.refreshPermissions();launchMonitoring() }
    private val csvDocument=registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { model.saveExport(it) }
    private val jsonDocument=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { model.saveExport(it) }
    override fun onSaveInstanceState(out: Bundle) {
        out.putBoolean("pendingDemo",pendingDemo);out.putBoolean("pendingGps",pendingGps);super.onSaveInstanceState(out)
    }
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        pendingDemo=saved?.getBoolean("pendingDemo")?:false;pendingGps=saved?.getBoolean("pendingGps")?:true
        WindowCompat.setDecorFitsSystemWindows(window,false)
        window.statusBarColor=android.graphics.Color.TRANSPARENT
        window.navigationBarColor=android.graphics.Color.rgb(2,11,22)
        WindowCompat.getInsetsController(window,window.decorView).apply { isAppearanceLightStatusBars=false;isAppearanceLightNavigationBars=false }
        if(Build.VERSION.SDK_INT>=29)window.isNavigationBarContrastEnforced=false
        lifecycleScope.launch {
            for((uri,json) in model.shareRequests) {
                val share=Intent(Intent.ACTION_SEND).apply { type=if(json)"application/json" else "text/csv";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);clipData=android.content.ClipData.newRawUri("RailSense export",uri) }
                runCatching{startActivity(Intent.createChooser(share,"Share RailSense export"))}.onFailure{model.messages.send("No sharing app is available: ${it.message}")}
            }
        }
        setContent { RailSenseTheme { RailSenseAppUi(model,::requestStart,
            { startService(Intent(this,MonitoringService::class.java).setAction("STOP")) },
            { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName"))) },
            { id,options -> model.rememberExport(id,options)
                if(options.json)jsonDocument.launch("RailSense-${id.take(8)}.json") else csvDocument.launch("RailSense-${id.take(8)}.csv") }) } }
    }
    override fun onResume() { super.onResume();model.refreshPermissions() }
    private fun requestStart(demo: Boolean) {
        lifecycleScope.launch {
            runCatching {
                val config=Preferences(this@MainActivity).config.first()
                pendingDemo=demo;pendingGps=config.gps
                val permissions=mutableListOf<String>()
                if(!demo && config.gps && !SensorCollector.hasLocationPermission(this@MainActivity)) {
                    permissions+=Manifest.permission.ACCESS_FINE_LOCATION;permissions+=Manifest.permission.ACCESS_COARSE_LOCATION
                }
                if(config.notifications && Build.VERSION.SDK_INT>=33 && !model.permissions.value.notifications)permissions+=Manifest.permission.POST_NOTIFICATIONS
                if(permissions.isEmpty())launchMonitoring() else permission.launch(permissions.toTypedArray())
            }.onFailure{model.messages.send("Unable to start: ${it.message}")}
        }
    }
    private fun launchMonitoring() {
        runCatching { ContextCompat.startForegroundService(this,Intent(this,MonitoringService::class.java).putExtra("demo",pendingDemo).putExtra("gps",pendingGps)) }
            .onFailure{lifecycleScope.launch{model.messages.send("Unable to start: ${it.message}")}}
    }
}
