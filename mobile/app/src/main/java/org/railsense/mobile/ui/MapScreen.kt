package org.railsense.mobile.ui

import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.railsense.mobile.*
import java.io.File

@Composable fun MapScreen(id: String,locations: LoadResult<List<LocationEntity>>,events: LoadResult<List<EventEntity>>,
    permission: Boolean,highThreshold: Double,onEvent: (String)->Unit,onPermissions: ()->Unit) {
    val context=LocalContext.current
    var enabled by rememberSaveable { mutableStateOf(false) }
    var recenter by remember { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    var online by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while(true) { online=context.getSystemService(ConnectivityManager::class.java).activeNetwork!=null;delay(5000) } }
    val points=locations.data.orEmpty().sortedBy{it.ns}
    val candidates=events.data.orEmpty()
    ScreenColumn {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Journey overview",style=MaterialTheme.typography.titleMedium)
            StatusBadge(if(online)"Map online" else "Offline",if(online)RailColors.Cyan else RailColors.Amber)
        }
        if(!permission && points.isEmpty())EmptyState("Location permission is off","Detection still works. Enable location for future trip coordinates.",Icons.Outlined.LocationOff,"Manage permission",onPermissions)
        if(locations.loading)LoadingState("Loading trip locations…")
        locations.error?.let{EmptyState("Location history unavailable",it,Icons.Outlined.ErrorOutline)}
        if(!enabled)EmptyState("Your route, in context","Load OpenStreetMap tiles to view recorded points. Tile requests use the network; detection remains offline-capable.",Icons.Outlined.Map,"Load optional map"){enabled=true}
        else {
            DashboardCard {
                key(id) { TripMapView(points,candidates,highThreshold,recenter,retry,onEvent) }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={recenter++},enabled=points.isNotEmpty(),modifier=Modifier.weight(1f).heightIn(min=48.dp)){Icon(Icons.Outlined.MyLocation,null);Spacer(Modifier.width(6.dp));Text("Recenter")}
                    TextButton(onClick={retry++},modifier=Modifier.heightIn(min=48.dp)){Text("Retry tiles")}
                }
                TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.openstreetmap.org/copyright")))}) {
                    Text("© OpenStreetMap contributors",style=MaterialTheme.typography.bodySmall)
                }
            }
            if(!online)Notice("Tiles may be unavailable offline. Stored coordinates and event details remain available below.",true)
            else Notice("If tiles cannot load, use Retry tiles. Map availability never controls sensor monitoring.")
        }
        DashboardCard {
            SectionHeader("Map legend")
            StatusBadge("Latest recorded GPS",RailColors.Blue,Icons.Outlined.MyLocation)
            StatusBadge("Elevated signal",RailColors.Amber,Icons.Outlined.WarningAmber)
            StatusBadge("High signal",RailColors.Red,Icons.Outlined.WarningAmber)
            Text("Bands use configured thresholds, not calibrated railway severity.",style=MaterialTheme.typography.bodySmall,color=RailColors.Subtext)
        }
        if(points.isEmpty())EmptyState("No recorded locations","No coordinates are invented. A trip with usable GPS fixes will display its recorded route here.",Icons.Outlined.LocationSearching)
        else points.last().let { p -> DashboardCard {
            SectionHeader("Latest recorded location")
            Text("${decimal(p.latitude,5)}, ${decimal(p.longitude,5)}",style=MaterialTheme.typography.titleMedium)
            Text("${timeLabel(p.wallMs,"dd MMM • HH:mm:ss")} · provider accuracy ±${decimal(p.accuracy.toDouble(),0)} m",color=RailColors.Subtext,style=MaterialTheme.typography.bodySmall)
            Text("Showing up to 2,000 recorded fixes. Lines join GPS points; they are not railway geometry.",color=RailColors.Subtext,style=MaterialTheme.typography.bodySmall)
        } }
        SectionHeader("Detected events (${candidates.size})")
        if(events.loading)LoadingState("Loading events…")
        events.error?.let{EmptyState("Events unavailable",it)}
        if(candidates.isEmpty())EmptyState("No candidates to show","Events with usable GPS appear as markers. Events without GPS remain in this list.",Icons.Outlined.CheckCircle)
        candidates.forEach{e->AnomalyEventCard(e,highThreshold){onEvent(e.id)}}
    }
}
@Composable private fun TripMapView(points: List<LocationEntity>,events: List<EventEntity>,highThreshold: Double,recenter: Int,retry: Int,onEvent: (String)->Unit) {
    val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
    val latestClick by rememberUpdatedState(onEvent)
    val map=remember {
        Configuration.getInstance().apply {
            userAgentValue="org.railsense.mobile/3.0"
            osmdroidBasePath=File(context.cacheDir,"maps").apply{mkdirs()}
            osmdroidTileCache=File(osmdroidBasePath,"tiles").apply{mkdirs()}
        }
        MapView(context).apply {
            setMultiTouchControls(true);controller.setZoom(14.0)
            overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                -.24f,0f,0f,0f,65f, 0f,-.23f,0f,0f,76f, 0f,0f,-.20f,0f,91f, 0f,0f,0f,1f,0f))))
        }
    }
    var initialCenter by remember{mutableStateOf(false)}
    DisposableEffect(map,lifecycle) {
        val observer=LifecycleEventObserver { _,event -> when(event) { Lifecycle.Event.ON_RESUME->map.onResume();Lifecycle.Event.ON_PAUSE->map.onPause();else->Unit } }
        lifecycle.addObserver(observer);if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))map.onResume()
        onDispose { lifecycle.removeObserver(observer);map.onPause();map.onDetach() }
    }
    LaunchedEffect(recenter) { points.lastOrNull()?.let{map.controller.animateTo(GeoPoint(it.latitude,it.longitude))} }
    LaunchedEffect(retry) { if(retry>0){map.tileProvider.clearTileCache();map.invalidate()} }
    AndroidView(factory={map},modifier=Modifier.fillMaxWidth().height(360.dp).semantics { contentDescription="Map of recorded GPS points and vibration candidates. The same events are available in the list below." },update={ view ->
        view.overlays.clear()
        if(points.isNotEmpty()) {
            view.overlays.add(Polyline(view).apply { setPoints(points.map{GeoPoint(it.latitude,it.longitude)});outlinePaint.color=RailColors.Cyan.toArgb();outlinePaint.strokeWidth=5f })
            val last=points.last()
            view.overlays.add(Marker(view).apply { position=GeoPoint(last.latitude,last.longitude);title="Latest recorded GPS";snippet="${timeLabel(last.wallMs)} ±${last.accuracy} m";icon=markerIcon(RailColors.Blue) })
            if(!initialCenter){view.controller.setCenter(GeoPoint(last.latitude,last.longitude));initialCenter=true}
        }
        events.filter{it.latitude!=null && it.longitude!=null}.take(200).forEach { event -> view.overlays.add(Marker(view).apply {
            position=GeoPoint(event.latitude!!,event.longitude!!);title="Potential vibration anomaly";snippet=timeLabel(event.startMs)
            icon=markerIcon(if(event.peak>=highThreshold)RailColors.Red else RailColors.Amber)
            setOnMarkerClickListener { _,_->latestClick(event.id);true }
        }) }
        view.invalidate()
    })
}
private fun markerIcon(color: Color)=GradientDrawable().apply { shape=GradientDrawable.OVAL;setSize(36,36);setColor(color.toArgb());setStroke(3,android.graphics.Color.WHITE) }
