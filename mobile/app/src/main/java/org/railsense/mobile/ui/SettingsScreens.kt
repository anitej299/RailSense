package org.railsense.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import org.railsense.mobile.*

@Composable fun SettingToggle(title: String,description: String,checked: Boolean,onChange: (Boolean)->Unit,enabled: Boolean=true) {
    Row(Modifier.fillMaxWidth().padding(vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,style=MaterialTheme.typography.bodyLarge)
            Text(description,style=MaterialTheme.typography.bodySmall,color=RailColors.Subtext)
        }
        Switch(checked,onCheckedChange=onChange,enabled=enabled,modifier=Modifier.semantics { contentDescription=title })
    }
}
@Composable private fun NumericField(label: String,value: String,description: String,enabled: Boolean,onChange: (String)->Unit) {
    OutlinedTextField(value,onChange,modifier=Modifier.fillMaxWidth(),enabled=enabled,singleLine=true,label={Text(label)},
        supportingText={Text(description)},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
}
@Composable fun SettingsScreen(config: MonitorConfig?,running: Boolean,maxHz: Int,permissions: PermissionState,
    onSave: (MonitorConfig)->Unit,onDemo: ()->Unit,onExport: ()->Unit,onPermissions: ()->Unit) {
    if(config==null) { ScreenColumn { LoadingState("Loading preferences…") };return }
    var hz by rememberSaveable(config){mutableStateOf(config.hz.toString())}
    var window by rememberSaveable(config){mutableStateOf(config.windowSeconds.toString())}
    var overlap by rememberSaveable(config){mutableStateOf(config.overlap.toString())}
    var threshold by rememberSaveable(config){mutableStateOf(config.threshold.toString())}
    var high by rememberSaveable(config){mutableStateOf(config.highThreshold.toString())}
    var confirmation by rememberSaveable(config){mutableStateOf(config.confirmations.toString())}
    var cooldown by rememberSaveable(config){mutableStateOf(config.cooldownSeconds.toString())}
    var gps by rememberSaveable(config){mutableStateOf(config.gps)}
    var local by rememberSaveable(config){mutableStateOf(config.localHistory)}
    var raw by rememberSaveable(config){mutableStateOf(config.recording)}
    var notifications by rememberSaveable(config){mutableStateOf(config.notifications)}
    var model by rememberSaveable(config){mutableStateOf(config.model)}
    var error by rememberSaveable{mutableStateOf<String?>(null)}
    ScreenColumn {
        Notice(if(running)"Monitoring is active. Stop the session before changing settings." else "Settings are saved on this phone and apply to the next session.",running)
        DashboardCard {
            SectionHeader("Sensor configuration")
            NumericField("Requested sampling rate · Hz",hz,"20–${maxHz.coerceAtMost(100)} Hz. Higher rates consume more battery; delivered rate may differ.",!running){hz=it}
            NumericField("Window duration · seconds",window,"1–5 seconds. Longer windows increase detection delay.",!running){window=it}
            NumericField("Window overlap",overlap,"0–0.9. A higher fraction increases analysis work.",!running){overlap=it}
        }
        DashboardCard {
            SectionHeader("Detection & signal bands")
            NumericField("Detection threshold",threshold,"0.05–1. Lower values produce more candidates.",!running){threshold=it}
            NumericField("High signal threshold",high,"From detection threshold to 1. This controls the visual signal band.",!running){high=it}
            NumericField("Temporal confirmation",confirmation,"1–10 consecutive eligible windows before an event.",!running){confirmation=it}
            Notice("Thresholds are experimental settings, not scientifically calibrated railway risk levels.",true)
        }
        DashboardCard {
            SectionHeader("Location & recording")
            SettingToggle("GPS collection","Optional coordinates and speed. Works independently of map display.",gps,{gps=it},!running)
            HorizontalDivider(color=RailColors.Border)
            SettingToggle("Save session history","Keep trips, events and GPS locally. Off keeps only the current session in memory.",local,{local=it},!running)
            HorizontalDivider(color=RailColors.Border)
            SettingToggle("Store raw sensor data","Needed for raw CSV/JSON and surrounding-signal review. Uses more storage.",raw && local,{raw=it},!running && local)
            if(!permissions.location)OutlinedButton(onClick=onPermissions,modifier=Modifier.heightIn(min=48.dp)){Text("Manage location permission")}
        }
        DashboardCard {
            SectionHeader("Notifications")
            SettingToggle("Anomaly notifications","In-app alerts remain available. The active-session notification is always required by Android.",notifications,{notifications=it},!running)
            NumericField("Alert cooldown · seconds",cooldown,"0–600 seconds between separate candidate alerts. Overlapping events are merged.",!running){cooldown=it}
            if(!permissions.notifications)OutlinedButton(onClick=onPermissions,modifier=Modifier.heightIn(min=48.dp)){Text("Enable system notifications")}
        }
        DashboardCard {
            SectionHeader("On-device model")
            Row(verticalAlignment=Alignment.CenterVertically) { RadioButton(model=="baseline",{model="baseline"},enabled=!running);Text("Statistical baseline",style=MaterialTheme.typography.bodyLarge) }
            Text("Works offline without a model file. Uses provisional vibration thresholds.",style=MaterialTheme.typography.bodySmall,color=RailColors.Subtext)
            Row(verticalAlignment=Alignment.CenterVertically) { RadioButton(model=="centroid",{model="centroid"},enabled=!running);Text("Trained centroid",style=MaterialTheme.typography.bodyLarge) }
            Text("Requires a compatible centroid.json asset and matching rate/window. A missing model prevents starting and shows an error.",style=MaterialTheme.typography.bodySmall,color=RailColors.Subtext)
        }
        error?.let{Text(it,color=RailColors.Red)}
        PrimaryActionButton("Save settings",{
            runCatching { MonitorConfig(hz.toInt(),window.toDouble(),overlap.toDouble(),threshold.toDouble(),confirmation.toInt(),cooldown.toInt(),gps,raw,notifications,model,local,high.toDouble()).validate().also{require(it.hz<=maxHz){"Requested rate exceeds this sensor's advertised $maxHz Hz limit"}} }
                .onSuccess{error=null;onSave(it)}.onFailure{error=it.message?:"Check the numeric settings"}
        },Icons.Outlined.Save,enabled=!running)
        DashboardCard {
            SectionHeader("Data & diagnostics")
            OutlinedButton(onClick=onExport,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Icon(Icons.Outlined.IosShare,null);Spacer(Modifier.width(8.dp));Text("Choose a trip to export")}
            OutlinedButton(onClick=onDemo,enabled=!running,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Icon(Icons.Outlined.Science,null);Spacer(Modifier.width(8.dp));Text("Run synthetic demonstration")}
            Notice("Demonstration signals are explicitly labeled synthetic. They do not measure your phone or establish railway accuracy.")
        }
        Text("RailSense 3.0 • Local-first research tool",style=MaterialTheme.typography.bodySmall,color=RailColors.Subtext)
    }
}

@Composable fun ExportScreen(trip: TripEntity?,state: ExportState,onSave: (ExportOptions)->Unit,onShare: (ExportOptions)->Unit) {
    var json by rememberSaveable{mutableStateOf(true)}
    var sensors by rememberSaveable(trip?.id){mutableStateOf(trip?.recording==true)}
    var gps by rememberSaveable{mutableStateOf(false)}
    var events by rememberSaveable{mutableStateOf(true)}
    var metadata by rememberSaveable{mutableStateOf(true)}
    ScreenColumn {
        if(trip==null) { EmptyState("Select a saved trip","Open History, choose a trip, then Export & share.",Icons.Outlined.FolderOpen);return@ScreenColumn }
        DashboardCard {
            Icon(Icons.Outlined.IosShare,null,Modifier.size(32.dp),tint=RailColors.Cyan)
            Text("Your data. Your choice.",style=MaterialTheme.typography.headlineMedium)
            Text("${timeLabel(trip.startMs,"dd MMM yyyy • HH:mm")} · ${trip.source.replace('_',' ')}",color=RailColors.Subtext)
            Text("Choose the content to include, then save a file or open Android's share sheet.",style=MaterialTheme.typography.bodyMedium,color=RailColors.Subtext)
        }
        DashboardCard {
            SectionHeader("File format")
            Row(verticalAlignment=Alignment.CenterVertically) { RadioButton(json,{json=true},enabled=!state.busy);Column { Text("JSON",style=MaterialTheme.typography.titleMedium);Text("Structured records and metadata",style=MaterialTheme.typography.bodySmall,color=RailColors.Subtext) } }
            Row(verticalAlignment=Alignment.CenterVertically) { RadioButton(!json,{json=false},enabled=!state.busy);Column { Text("CSV",style=MaterialTheme.typography.titleMedium);Text("Tables with a record_type column",style=MaterialTheme.typography.bodySmall,color=RailColors.Subtext) } }
        }
        DashboardCard {
            SectionHeader("Include in this export")
            SettingToggle("Sensor data",if(trip.recording)"Recorded accelerometer, gyroscope and linear acceleration." else "Unavailable: raw recording was disabled.",sensors,{sensors=it},!state.busy && trip.recording)
            HorizontalDivider(color=RailColors.Border)
            SettingToggle("GPS & location","Sensitive: coordinates, accuracy and GPS speed in all selected records.",gps,{gps=it},!state.busy)
            HorizontalDivider(color=RailColors.Border)
            SettingToggle("Detected events","Event timestamps, experimental scores and stored waveform excerpts.",events,{events=it},!state.busy)
            HorizontalDivider(color=RailColors.Border)
            SettingToggle("Trip metadata","Device, trip time, model and recording information.",metadata,{metadata=it},!state.busy)
        }
        Notice(if(gps)"Location is included. The recipient can see where this trip and its events occurred." else "Location is excluded. Coordinates, GPS timestamps and speed are removed from sensor rows and events.",gps)
        if(state.busy)DashboardCard { LinearProgressIndicator(Modifier.fillMaxWidth());Text(state.message,color=RailColors.Subtext) }
        else if(state.message.isNotEmpty())DashboardCard(accent=if(state.error)RailColors.Red else RailColors.Green) {
            Icon(if(state.error)Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,null,tint=if(state.error)RailColors.Red else RailColors.Green)
            Text(state.message)
        }
        val enabled=!state.busy && trip.status!="ACTIVE" && (sensors || gps || events || metadata)
        PrimaryActionButton("Save to device",{onSave(ExportOptions(json,sensors,gps,events,metadata))},Icons.Outlined.SaveAlt,enabled)
        OutlinedButton(onClick={onShare(ExportOptions(json,sensors,gps,events,metadata))},enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Icon(Icons.Outlined.Share,null);Spacer(Modifier.width(8.dp));Text("Share exported file")}
        if(trip.status=="ACTIVE")Notice("Stop this trip before exporting a consistent recording.",true)
        Notice("Nothing is uploaded automatically. Share opens Android's chooser; you select the recipient or app.")
    }
}
