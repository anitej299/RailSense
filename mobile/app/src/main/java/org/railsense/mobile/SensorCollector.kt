package org.railsense.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.*
import android.location.*
import android.os.*
import androidx.core.content.ContextCompat

class SensorCollector(private val context: Context, private val config: MonitorConfig,
    private val onSample: (Sample) -> Unit, private val onFix: (Fix) -> Unit,
    private val onStatus: (String) -> Unit) : SensorEventListener, LocationListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val locations = context.getSystemService(LocationManager::class.java)
    private val thread = HandlerThread("railsense-sensors")
    private val sync = Synchronizer()
    private var wallOrigin = 0L
    private var monoOrigin = 0L
    @Volatile private var running = false
    fun start() {
        check(manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) { "Accelerometer unavailable; monitoring cannot start" }
        thread.start(); running=true
        wallOrigin=System.currentTimeMillis(); monoOrigin=SystemClock.elapsedRealtimeNanos()
        val handler=Handler(thread.looper)
        for(type in listOf(Sensor.TYPE_ACCELEROMETER,Sensor.TYPE_GYROSCOPE,Sensor.TYPE_LINEAR_ACCELERATION)) {
            val sensor=manager.getDefaultSensor(type)
            if(sensor != null) {
                val registered=manager.registerListener(this,sensor,1_000_000/config.hz,handler)
                if(type == Sensor.TYPE_ACCELEROMETER) check(registered) { "Accelerometer registration failed" }
            }
        }
        if(config.gps && hasLocationPermission(context)) {
            try {
                val provider=if(locations.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
                if(locations.isProviderEnabled(provider)) locations.requestLocationUpdates(provider,1000L,0f,this,thread.looper)
                else onStatus("GPS unavailable: location providers disabled")
            } catch(e: SecurityException) { onStatus("Location permission denied; detection continues without GPS") }
              catch(e: Exception) { onStatus("GPS unavailable: ${e.message}") }
        } else onStatus(if(config.gps) "Location permission denied; detection continues without GPS" else "GPS disabled")
    }
    fun stop() {
        running=false; manager.unregisterListener(this); locations.removeUpdates(this)
        if(thread.isAlive) thread.quitSafely()
    }
    override fun onSensorChanged(event: SensorEvent) {
        if(!running || event.values.size < 3) return
        val v=Vec3(event.values[0].toDouble(),event.values[1].toDouble(),event.values[2].toDouble())
        if(!v.valid()) return
        when(event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> sync.gyro(event.timestamp,v)
            Sensor.TYPE_LINEAR_ACCELERATION -> sync.linear(event.timestamp,v)
            Sensor.TYPE_ACCELEROMETER -> onSample(sync.sample(event.timestamp,wallOrigin+(event.timestamp-monoOrigin)/1_000_000,v))
        }
    }
    override fun onLocationChanged(location: Location) {
        if(!running) return
        val fix=Fix(location.elapsedRealtimeNanos,location.time,location.latitude,location.longitude,
            if(location.hasAccuracy())location.accuracy else Float.POSITIVE_INFINITY,if(location.hasSpeed())location.speed else null)
        sync.location(fix); if(fix.valid())onFix(fix)
    }
    override fun onProviderDisabled(provider: String) { onStatus("$provider disabled; detection continues") }
    override fun onProviderEnabled(provider: String) { onStatus("$provider enabled; waiting for fix") }
    @Deprecated("Legacy Android callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if(accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) onStatus("Sensor reports unreliable accuracy")
    }
    companion object {
        fun hasLocationPermission(context: Context) = listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED }
        fun availability(context: Context): String {
            val sm=context.getSystemService(SensorManager::class.java)
            return listOf("Accelerometer" to Sensor.TYPE_ACCELEROMETER,"Gyroscope" to Sensor.TYPE_GYROSCOPE,"Linear acceleration" to Sensor.TYPE_LINEAR_ACCELERATION)
                .joinToString(" • ") { (name,type) -> "$name: ${if(sm.getDefaultSensor(type)!=null)"available" else "missing"}" }
        }
    }
}

