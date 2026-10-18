package org.railsense.mobile

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.settings by preferencesDataStore("monitoring")
class Preferences(private val context: Context) {
    val config=context.settings.data.map { p ->
        runCatching { MonitorConfig(p[intPreferencesKey("hz")]?:50,p[doublePreferencesKey("window")]?:2.0,
            p[doublePreferencesKey("overlap")]?:.5,p[doublePreferencesKey("threshold")]?:.65,
            p[intPreferencesKey("confirmation")]?:2,p[intPreferencesKey("cooldown")]?:30,
            p[booleanPreferencesKey("gps")]?:true,p[booleanPreferencesKey("recording")]?:true,
            p[booleanPreferencesKey("notifications")]?:true,p[stringPreferencesKey("model")]?:"baseline",
            p[booleanPreferencesKey("localHistory")]?:true,p[doublePreferencesKey("highThreshold")]?:maxOf(.85,p[doublePreferencesKey("threshold")]?:.65)).validate() }.getOrDefault(MonitorConfig())
    }
    suspend fun save(c: MonitorConfig) {
        c.validate()
        context.settings.edit { p ->
            p[intPreferencesKey("hz")]=c.hz; p[doublePreferencesKey("window")]=c.windowSeconds
            p[doublePreferencesKey("overlap")]=c.overlap; p[doublePreferencesKey("threshold")]=c.threshold
            p[intPreferencesKey("confirmation")]=c.confirmations; p[intPreferencesKey("cooldown")]=c.cooldownSeconds
            p[booleanPreferencesKey("gps")]=c.gps; p[booleanPreferencesKey("recording")]=c.recording
            p[booleanPreferencesKey("notifications")]=c.notifications; p[stringPreferencesKey("model")]=c.model
            p[booleanPreferencesKey("localHistory")]=c.localHistory; p[doublePreferencesKey("highThreshold")]=c.highThreshold
        }
    }
}

