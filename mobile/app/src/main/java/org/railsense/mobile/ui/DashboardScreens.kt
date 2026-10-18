package org.railsense.mobile.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.railsense.mobile.*

private fun currentSample(live: LiveState): Sample? = live.sample?.takeIf {
    live.running && SystemClock.elapsedRealtimeNanos()-it.ns in 0..3_000_000_000L
}
@Composable fun HomeScreen(live: LiveState,config: MonitorConfig?,sensors: DeviceSensors,permissions: PermissionState,
    onStart: ()->Unit,onStop: ()->Unit,onLive: ()->Unit,onSettings: ()->Unit,onPermissions: ()->Unit,onEvent: (String)->Unit,lastEvent: EventEntity?=null) {
    val sample=currentSample(live)
    ScreenColumn {
        DashboardCard {
            StatusBadge(if(live.running)"Monitoring active" else "Monitoring inactive",if(live.running)RailColors.Green else RailColors.Subtext)
            Text(if(live.running)"Your journey,\nin focus." else "Ready for your\nnext journey.",style=MaterialTheme.typography.headlineLarge)
            Text("Live motion. Local intelligence.",color=RailColors.Subtext,style=MaterialTheme.typography.bodyMedium)
            TrackIllustration()
            if(live.running) {
                DetailRow("TRIP DURATION",durationLabel(live.elapsedSeconds),RailColors.Green)
                PrimaryActionButton("Open live monitoring",onLive,Icons.Outlined.GraphicEq)
                OutlinedButton(onClick=onStop,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Icon(Icons.Outlined.Stop,null);Spacer(Modifier.width(8.dp));Text("Stop monitoring")}
            } else PrimaryActionButton("Start monitoring",onStart,enabled=config!=null && sensors.accelerometer)
        }
        if(live.source=="SYNTHETIC_TEST" && live.tripId!=null)Notice("Synthetic demonstration session. These readings do not come from phone sensors.",true)
        if(!sensors.accelerometer)EmptyState("Accelerometer unavailable","Live monitoring needs an accelerometer. You can still inspect history or use the clearly labeled demonstration in Settings.",Icons.Outlined.SensorsOff)
        if(live.status.startsWith("Stopped:") || live.status.startsWith("Cannot"))EmptyState("Monitoring needs attention",live.status,Icons.Outlined.ErrorOutline,"Review settings",onSettings)
        SectionHeader("Device overview","Settings",onSettings)
        MetricGrid(listOf(
            Metric("GPS status",if(sample?.fix!=null)"Located" else if(!permissions.location)"Permission off" else "No live fix",
                detail=sample?.fix?.let{"Approx. ±${decimal(it.accuracy.toDouble(),0)} m"}?:"Location is optional",icon=Icons.Outlined.MyLocation,color=if(sample?.fix!=null)RailColors.Green else RailColors.Amber),
            Metric("Current speed",decimal(sample?.fix?.speed?.times(3.6),1),"km/h",if(sample?.fix?.speed==null)"Waiting for GPS speed" else "GPS estimate",Icons.Outlined.Speed),
            Metric("Motion sensors","${listOf(sensors.accelerometer,sensors.gyro).count{it}} / 2","available","Accelerometer + gyroscope",Icons.Outlined.Sensors),
            Metric("Session events",live.eventCount.toString(),"candidates","Potential vibration anomalies",Icons.Outlined.Insights,color=RailColors.Amber)
        ))
        if(config?.gps==true && !permissions.location)EmptyState("Location permission is off","Monitoring still works. Allow location to attach approximate positions and speed to future readings.",Icons.Outlined.LocationOff,"Manage permissions",onPermissions)
        SectionHeader("Latest event")
        (live.event?:lastEvent)?.let { AnomalyEventCard(it,config?.highThreshold?:.85){onEvent(it.id)} }
            ?:EmptyState("No detected events yet","Confirmed candidate windows will appear here with their signal quality and approximate location.",Icons.Outlined.CheckCircle)
        Notice("Experimental vibration monitoring. A candidate is not confirmed rail damage or a replacement for official inspection.")
    }
}
@Composable private fun TrackIllustration() {
    Canvas(Modifier.fillMaxWidth().height(80.dp)) {
        val mid=size.width*.65f;val top=0f;val bottom=size.height
        val brush=Brush.verticalGradient(listOf(RailColors.Blue.copy(alpha=.08f),RailColors.Cyan.copy(alpha=.65f)))
        drawLine(brush,Offset(mid-8,top),Offset(mid-size.width*.36f,bottom),2.dp.toPx())
        drawLine(brush,Offset(mid+8,top),Offset(mid+size.width*.36f,bottom),2.dp.toPx())
        for(i in 1..7) { val f=i/7f; val y=bottom*f*f;val half=8+(size.width*.36f-8)*f*f
            drawLine(RailColors.Blue.copy(alpha=.15f+f*.25f),Offset(mid-half-6,y),Offset(mid+half+6,y),2.dp.toPx()) }
        drawCircle(RailColors.Cyan,3.dp.toPx(),Offset(mid,top+5.dp.toPx()))
    }
}
@Composable fun LiveScreen(live: LiveState,sensors: DeviceSensors,highThreshold: Double,onStart: ()->Unit,onStop: ()->Unit,onEvent: (String)->Unit) {
    val sample=currentSample(live)
    ScreenColumn {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            StatusBadge(if(live.running)"Live monitoring" else "Session inactive",if(live.running)RailColors.Green else RailColors.Subtext)
            Text(durationLabel(live.elapsedSeconds),style=MaterialTheme.typography.titleMedium)
        }
        if(!live.running)EmptyState("Monitoring is stopped","Start a session to receive live readings. Previous waveforms below are retained for review.",Icons.Outlined.PauseCircle,"Start monitoring",onStart)
        if(live.source=="SYNTHETIC_TEST")Notice("SYNTHETIC DEMONSTRATION • generated signals, not phone measurements.",true)
        if(live.note.isNotEmpty())Notice(live.note,true)
        MetricGrid(listOf(
            Metric("Speed",decimal(sample?.fix?.speed?.times(3.6),1),"km/h",icon=Icons.Outlined.Speed),
            Metric("Delivered rate",decimal(live.features?.actualHz,1),"Hz",if(live.features==null)"Warming up" else "Measured from timestamps",Icons.Outlined.Sensors)
        ))
        DashboardCard {
            SectionHeader("Live acceleration")
            StatusBadge(if(sample!=null)"Receiving samples" else "No recent samples",if(sample!=null)RailColors.Green else RailColors.Amber)
            LiveWaveformChart(live.waveform.map{it.value},times=live.waveform.map{it.wallMs},
                eventRange=live.event?.let{it.startMs..it.endMs},description="Live acceleration magnitude, including gravity")
            Text("6-second rolling magnitude • includes gravity",style=MaterialTheme.typography.bodySmall,color=RailColors.Subtext)
        }
        MetricGrid(listOf(
            Metric("Rolling RMS",decimal(live.features?.rms),"m/s²",icon=Icons.Outlined.ShowChart),
            Metric("Standard deviation",decimal(live.features?.std),"m/s²",icon=Icons.Outlined.Insights),
            Metric("Peak-to-peak",decimal(live.features?.peakToPeak),"m/s²",icon=Icons.Outlined.Height),
            Metric("Jerk RMS",decimal(live.features?.jerkRms,1),"m/s³",icon=Icons.Outlined.Timeline)
        ))
        Notice("Window metrics use acceleration after DC/gravity offset removal. The live plot above shows raw acceleration magnitude.")
        SectionHeader("Sensor stream")
        SensorStatusCard("Accelerometer",sensors.accelerometer,sample?.acceleration,"m/s² • device X / Y / Z",live.running)
        SensorStatusCard("Gyroscope",sensors.gyro,sample?.gyro,"rad/s • device X / Y / Z",live.running)
        if(sensors.linear || sample?.linear!=null)SensorStatusCard("Linear acceleration",sensors.linear,sample?.linear,"m/s² • sensor-provided",live.running)
        DashboardCard {
            SectionHeader("Detection pipeline")
            DetailRow("SIGNAL QUALITY",live.features?.quality?.replace('_',' ')?:"Waiting for a complete window")
            DetailRow("EXPERIMENTAL SCORE",if(live.features==null)"Unavailable" else "${decimal(live.score,3)} / 1 · not a probability")
            LiveWaveformChart(live.scores,unit="Uncalibrated score • successive analysis windows",description="Anomaly score history")
            DetailRow("MODEL",live.model.ifEmpty{"Not started"})
            DetailRow("MODEL INFERENCE",if(live.features==null)"Not measured" else "${decimal(live.latencyMs,3)} ms on this device")
            DetailRow("DROPPED MESSAGES",live.drops.toString())
            DetailRow("GPS",sample?.fix?.let{"${decimal(it.latitude,5)}, ${decimal(it.longitude,5)} · approx. ±${decimal(it.accuracy.toDouble(),0)} m"}?:"Unavailable or stale")
        }
        live.event?.let { AnomalyEventCard(it,highThreshold){onEvent(it.id)} }
        if(live.running)PrimaryActionButton("Stop monitoring",onStop,Icons.Outlined.Stop,danger=true)
    }
}
