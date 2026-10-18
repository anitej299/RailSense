package org.railsense.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.railsense.mobile.*

@Composable fun HistoryScreen(result: LoadResult<List<TripOverview>>,onTrip: (String)->Unit,onRetry: ()->Unit,onHome: ()->Unit) {
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text("Your journeys, recorded locally.",style=MaterialTheme.typography.bodyLarge,color=RailColors.Subtext) }
        if(result.loading)item{LoadingState()}
        result.error?.let { item{EmptyState("History unavailable",it,Icons.Outlined.ErrorOutline,"Try again",onRetry)} }
        if(result.data?.isEmpty()==true)item { EmptyState("Your first journey starts here","Trips, signal quality and detected events will appear after you start monitoring with local history enabled.",Icons.Outlined.Route,"Go to dashboard",onHome) }
        items(result.data?:emptyList(),key={it.trip.id}) { overview ->
            val trip=overview.trip
            DashboardCard {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Train,null,tint=RailColors.Cyan,modifier=Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp));Text(timeLabel(trip.startMs,"dd MMM yyyy"),style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                }
                Text("${timeLabel(trip.startMs,"HH:mm")} – ${trip.endMs?.let{timeLabel(it,"HH:mm")}?:"In progress"}",color=RailColors.Subtext)
                StatusBadge(if(trip.source=="SYNTHETIC_TEST")"Synthetic demonstration" else trip.status.lowercase().replaceFirstChar{it.uppercase()},
                    if(trip.status=="ACTIVE")RailColors.Green else if(trip.status in listOf("ERROR","INTERRUPTED"))RailColors.Amber else RailColors.Cyan)
                Text("${durationLabel(((trip.endMs?:trip.startMs)-trip.startMs)/1000)}  •  ${overview.eventCount} candidate events",style=MaterialTheme.typography.bodyMedium)
                Text("GPS coverage ${if(trip.samples>0)100*trip.locatedSamples/trip.samples else 0}% · ${trip.samples} samples",style=MaterialTheme.typography.bodySmall,color=RailColors.Subtext)
                OutlinedButton(onClick={onTrip(trip.id)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("View trip details");Spacer(Modifier.width(8.dp));Icon(Icons.Outlined.ArrowForward,null)}
            }
        }
    }
}
@Composable fun TripScreen(overview: TripOverview?,events: LoadResult<List<EventEntity>>,highThreshold: Double,
    onEvent: (String)->Unit,onMap: ()->Unit,onExport: ()->Unit,onDelete: ()->Unit) {
    var confirm by remember { mutableStateOf(false) }
    ScreenColumn {
        if(overview==null) { EmptyState("Trip unavailable","This trip may have been deleted. Return to History and select another trip.");return@ScreenColumn }
        val trip=overview.trip
        DashboardCard {
            Text(timeLabel(trip.startMs,"EEEE, dd MMM yyyy"),style=MaterialTheme.typography.titleLarge)
            StatusBadge(trip.status,if(trip.status=="ACTIVE")RailColors.Green else RailColors.Cyan)
            DetailRow("START / END","${timeLabel(trip.startMs)} / ${trip.endMs?.let{timeLabel(it)}?:"In progress"}")
            DetailRow("DATA SOURCE",if(trip.source=="SYNTHETIC_TEST")"Synthetic demonstration" else "Real phone sensors")
        }
        MetricGrid(listOf(Metric("Duration",durationLabel(((trip.endMs?:trip.startMs)-trip.startMs)/1000),icon=Icons.Outlined.Timer),
            Metric("Detected events",overview.eventCount.toString(),"candidates",icon=Icons.Outlined.Insights,color=RailColors.Amber)))
        DashboardCard {
            SectionHeader("Recording quality")
            DetailRow("GPS COVERAGE","${if(trip.samples>0)100*trip.locatedSamples/trip.samples else 0}% of sensor samples")
            DetailRow("SAMPLES / DROPPED MESSAGES","${trip.samples} / ${trip.drops}")
            DetailRow("RAW DATA",if(trip.recording)"Recorded locally" else "Raw recording was off")
            DetailRow("DEVICE",trip.device);DetailRow("SENSORS",trip.sensors);DetailRow("MODEL",trip.model)
        }
        OutlinedButton(onClick=onMap,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Icon(Icons.Outlined.Map,null);Spacer(Modifier.width(8.dp));Text("View trip on map")}
        PrimaryActionButton("Export & share",onExport,Icons.Outlined.IosShare,enabled=trip.status!="ACTIVE")
        if(trip.status=="ACTIVE")Notice("Stop monitoring before exporting or deleting this trip.",true)
        SectionHeader("Detected events (${overview.eventCount})")
        if(events.loading)LoadingState("Loading events…")
        events.error?.let{EmptyState("Unable to load events",it,Icons.Outlined.ErrorOutline)}
        if(events.data?.isEmpty()==true)EmptyState("No candidate events","No windows met the configured confirmation criteria during this trip.",Icons.Outlined.CheckCircle)
        events.data?.forEach{e->AnomalyEventCard(e,highThreshold){onEvent(e.id)}}
        if(trip.status!="ACTIVE")TextButton(onClick={confirm=true},modifier=Modifier.heightIn(min=48.dp)){Icon(Icons.Outlined.DeleteOutline,null,tint=RailColors.Red);Spacer(Modifier.width(8.dp));Text("Delete local trip",color=RailColors.Red)}
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Delete this trip?")},text={Text("The local recording, GPS history and events will be removed. Copies you exported are unaffected.")},confirmButton={TextButton(onClick={confirm=false;onDelete()}){Text("Delete",color=RailColors.Red)}},dismissButton={TextButton(onClick={confirm=false}){Text("Keep trip")}})
}
@Composable fun EventScreen(result: LoadResult<EventEntity>,highThreshold: Double,alert: Boolean,
    excerpt: LoadResult<List<SignalPoint>>,onAcknowledge: (String)->Unit,onDetails: (String)->Unit,onContext: (EventEntity)->Unit,onMap: (String)->Unit) {
    ScreenColumn {
        if(result.loading) { LoadingState("Loading event…");return@ScreenColumn }
        val event=result.data
        if(event==null) { EmptyState("Event unavailable",result.error?:"Select an event from trip history.",Icons.Outlined.SearchOff);return@ScreenColumn }
        DashboardCard(accent=RailColors.Red) {
            Icon(Icons.Outlined.WarningAmber,"Potential vibration anomaly",Modifier.size(42.dp),tint=RailColors.Red)
            Text("Potential vibration anomaly detected",style=MaterialTheme.typography.headlineMedium)
            Text("Unusual motion was detected across consecutive sensor windows. Review the recorded signal and its quality.",color=RailColors.Subtext)
            StatusBadge(signalBand(event.peak,highThreshold),if(event.peak>=highThreshold)RailColors.Red else RailColors.Amber,Icons.Outlined.Insights)
            Text(timeLabel(event.startMs,"dd MMM yyyy • HH:mm:ss"),style=MaterialTheme.typography.bodyMedium)
        }
        MetricGrid(listOf(Metric("Experimental index",decimal(event.peak,3),"of 1.000","Uncalibrated • not probability",Icons.Outlined.Insights,RailColors.Amber),
            Metric("Event duration",decimal((event.endMs-event.startMs)/1000.0,1),"seconds","${event.windows} confirmed windows",Icons.Outlined.Timer)))
        DashboardCard {
            SectionHeader("Recorded event signal")
            LiveWaveformChart(event.waveform.split(",").mapNotNull{it.toDoubleOrNull()},highlightAll=true,description="Stored peak-window vibration excerpt")
            Text("Stored peak-window excerpt • DC-removed acceleration magnitude",color=RailColors.Subtext,style=MaterialTheme.typography.bodySmall)
        }
        DashboardCard {
            SectionHeader("Event context")
            DetailRow("APPROXIMATE LOCATION",if(event.latitude!=null && event.longitude!=null)"${decimal(event.latitude,5)}, ${decimal(event.longitude,5)}" else "Unavailable · no usable GPS fix")
            DetailRow("LOCATION UNCERTAINTY",event.accuracy?.let{"Approx. ±${decimal(it.toDouble(),0)} m"}?:"Unavailable")
            DetailRow("SPEED",event.speed?.let{"${decimal(it*3.6,1)} km/h"}?:"Unavailable")
            DetailRow("SIGNAL QUALITY",event.quality.replace('_',' '))
            if(!alert) { DetailRow("END TIME",timeLabel(event.endMs,"dd MMM yyyy • HH:mm:ss"));DetailRow("MODEL VERSION",event.model);DetailRow("MEAN INDEX",decimal(event.mean,3)) }
        }
        Notice("Signal bands use your configured thresholds. Neither the score nor the band confirms a railway defect.",true)
        if(!alert) {
            val f=event.features.split(",").map{it.toDoubleOrNull()}
            SectionHeader("Feature summary")
            MetricGrid(listOf(Metric("RMS",decimal(f.getOrNull(0)),"m/s²"),Metric("Standard deviation",decimal(f.getOrNull(1)),"m/s²"),
                Metric("Peak-to-peak",decimal(f.getOrNull(2)),"m/s²"),Metric("Jerk RMS",decimal(f.getOrNull(3),1),"m/s³")))
            DashboardCard {
                DetailRow("GYROSCOPE RMS","${decimal(f.getOrNull(4))} rad/s")
                DetailRow("DOMINANT MAGNITUDE FREQUENCY","${decimal(f.getOrNull(5))} Hz")
                DetailRow("HIGH-BAND ENERGY RATIO",decimal(f.getOrNull(6),3))
                OutlinedButton(onClick={onContext(event)},enabled=!excerpt.loading,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Load surrounding samples")}
                if(excerpt.loading)LoadingState("Reading local recording…")
                excerpt.error?.let{Text(it,color=RailColors.Subtext,style=MaterialTheme.typography.bodySmall)}
                excerpt.data?.let { points ->
                    LiveWaveformChart(points.map{it.value},times=points.map{it.wallMs},eventRange=event.startMs..event.endMs,description="Surrounding raw acceleration magnitude")
                    Text("Raw magnitude includes gravity. Up to 1,000 samples with 2 seconds of context where available.",color=RailColors.Subtext,style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        if(event.acknowledged)StatusBadge("Acknowledged",RailColors.Green,Icons.Outlined.CheckCircle)
        else PrimaryActionButton("Acknowledge event",{onAcknowledge(event.id)},Icons.Outlined.Check)
        if(alert)OutlinedButton(onClick={onDetails(event.id)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("View full details")}
        else OutlinedButton(onClick={onMap(event.tripId)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Icon(Icons.Outlined.Map,null);Spacer(Modifier.width(8.dp));Text("View trip map")}
    }
}
