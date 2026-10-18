package org.railsense.mobile.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import org.railsense.mobile.*

private data class Destination(val route: String,val title: String,val icon: ImageVector)
private val destinations=listOf(Destination("home","Home",Icons.Outlined.Home),Destination("map","Map",Icons.Outlined.Map),
    Destination("history","History",Icons.Outlined.History),Destination("settings","Settings",Icons.Outlined.Settings))

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RailSenseAppUi(vm: RailSenseViewModel,onStart: (Boolean)->Unit,onStop: ()->Unit,onPermissions: ()->Unit,onExport: (String,ExportOptions)->Unit) {
    val nav=rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route=backStack?.destination?.route?:"home"
    val live by vm.live.collectAsStateWithLifecycle()
    val latestSavedEvent by vm.latestEvent.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    val exportState by vm.exportState.collectAsStateWithLifecycle()
    val snack=remember{SnackbarHostState()}
    var startDialog by rememberSaveable{mutableStateOf(false)}
    var shownEvent by rememberSaveable{mutableStateOf<String?>(null)}
    val haptic=LocalHapticFeedback.current
    val high=config?.highThreshold?:.85
    fun root(destination: String) { nav.navigate(destination) {
        popUpTo(nav.graph.findStartDestination().id){saveState=true};launchSingleTop=true;restoreState=true
    } }
    fun event(id: String) { vm.excerpt.value=LoadResult();nav.navigate("event/$id"){launchSingleTop=true} }
    LaunchedEffect(Unit) { for(message in vm.messages)snack.showSnackbar(message) }
    LaunchedEffect(live.event?.id) {
        val e=live.event
        if(live.running && e!=null && e.id!=shownEvent && !e.acknowledged && !MonitorState.acknowledged.contains(e.id)) {
            shownEvent=e.id;haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            nav.navigate("alert/${e.id}") { launchSingleTop=true
                if(route.startsWith("alert/"))popUpTo(route){inclusive=true}
            }
        }
    }
    val rootRoute=when { route in listOf("home","live") || route.startsWith("alert/")->"home";route.startsWith("map")->"map";route=="settings"->"settings";else->"history" }
    val title=when { route=="home"->"RailSense";route=="live"->"Live monitoring";route.startsWith("map")->"Anomaly map";route=="history"->"Trip history";route=="settings"->"Settings";route.startsWith("trip/")->"Trip details";route.startsWith("export/")->"Export & share";route.startsWith("alert/")->"Vibration alert";else->"Event details" }
    Scaffold(containerColor=RailColors.Background,snackbarHost={SnackbarHost(snack)},
        topBar={Column {
            TopAppBar(title={Column { Text(title,style=MaterialTheme.typography.headlineMedium)
                if(route=="home")Text("Track Anomaly Detection",style=MaterialTheme.typography.bodySmall,color=RailColors.Subtext)
            }},navigationIcon={
                if(route !in destinations.map{it.route})IconButton(onClick={nav.popBackStack()}){Icon(Icons.Outlined.ArrowBack,"Back")}
                else if(route=="home")Icon(Icons.Outlined.Train,null,Modifier.padding(start=20.dp,end=8.dp).size(28.dp),tint=RailColors.Cyan)
            },colors=TopAppBarDefaults.topAppBarColors(containerColor=RailColors.Background))
            if(live.running && route!="live")Surface(color=RailColors.Elevated) {
                Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Sensors,null,Modifier.size(16.dp),tint=RailColors.Green)
                    Text("${if(live.source=="SYNTHETIC_TEST")"Demo" else "Monitoring"} · ${durationLabel(live.elapsedSeconds)}",Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={nav.navigate("live"){launchSingleTop=true}}){Text("Live")}
                    IconButton(onClick=onStop){Icon(Icons.Outlined.Stop,"Stop monitoring",tint=RailColors.Red)}
                }
            }
        }},bottomBar={
            if(!route.startsWith("alert/"))NavigationBar(containerColor=RailColors.Secondary,tonalElevation=0.dp) {
                destinations.forEach { d -> NavigationBarItem(selected=rootRoute==d.route,onClick={root(d.route)},
                    icon={Icon(d.icon,null)},label={Text(d.title)},colors=NavigationBarItemDefaults.colors(
                        selectedIconColor=RailColors.Blue,selectedTextColor=RailColors.Blue,indicatorColor=RailColors.Blue.copy(alpha=.12f),
                        unselectedIconColor=RailColors.Subtext,unselectedTextColor=RailColors.Subtext)) }
            }
        }) { padding ->
        NavHost(nav,startDestination="home",modifier=Modifier.padding(padding),enterTransition={fadeIn(tween(180))},exitTransition={fadeOut(tween(120))}) {
            composable("home") { HomeScreen(live,config,vm.sensors,permissions,{startDialog=true},onStop,{nav.navigate("live"){launchSingleTop=true}},{root("settings")},onPermissions,::event,latestSavedEvent) }
            composable("live") { LiveScreen(live,vm.sensors,high,{startDialog=true},onStop,::event) }
            composable("history") { HistoryScreen(trips,{nav.navigate("trip/$it")},vm::retry,{root("home")}) }
            composable("settings") { SettingsScreen(config,live.running,vm.sensors.maxHz,permissions,vm::saveConfig,
                {onStart(true);nav.navigate("live"){launchSingleTop=true}},{root("history")},onPermissions) }
            composable("map") {
                val id=live.tripId?:trips.data?.firstOrNull()?.trip?.id?:""
                val locations by remember(id){vm.locations(id)}.collectAsStateWithLifecycle(LoadResult(loading=true))
                val events by remember(id){vm.events(id)}.collectAsStateWithLifecycle(LoadResult(loading=true))
                MapScreen(id,locations,events,permissions.location,high,::event,onPermissions)
            }
            composable("maptrip/{id}") { entry ->
                val id=entry.arguments?.getString("id")?:""
                val locations by remember(id){vm.locations(id)}.collectAsStateWithLifecycle(LoadResult(loading=true))
                val events by remember(id){vm.events(id)}.collectAsStateWithLifecycle(LoadResult(loading=true))
                MapScreen(id,locations,events,permissions.location,high,::event,onPermissions)
            }
            composable("trip/{id}") { entry ->
                val id=entry.arguments?.getString("id")?:""
                val events by remember(id){vm.events(id)}.collectAsStateWithLifecycle(LoadResult(loading=true))
                TripScreen(trips.data?.find{it.trip.id==id},events,high,::event,{nav.navigate("maptrip/$id")},
                    {vm.exportState.value=ExportState();nav.navigate("export/$id")},{vm.deleteTrip(id){nav.popBackStack()}})
            }
            for(destination in listOf("event/{id}","alert/{id}"))composable(destination) { entry ->
                val id=entry.arguments?.getString("id")?:""
                val data by remember(id){vm.event(id)}.collectAsStateWithLifecycle(LoadResult(loading=true))
                val excerpt by vm.excerpt.collectAsStateWithLifecycle()
                EventScreen(data,high,destination.startsWith("alert"),excerpt,{ vm.acknowledge(it);if(destination.startsWith("alert"))nav.popBackStack() },
                    {nav.navigate("event/$it"){popUpTo(entry.destination.id){inclusive=true}}},vm::loadExcerpt,{nav.navigate("maptrip/$it")})
            }
            composable("export/{id}") { entry ->
                val id=entry.arguments?.getString("id")?:""
                ExportScreen(trips.data?.find{it.trip.id==id}?.trip,exportState,{onExport(id,it)},{vm.shareExport(id,it)})
            }
        }
    }
    if(startDialog)AlertDialog(onDismissRequest={startDialog=false},icon={Icon(Icons.Outlined.Sensors,null,tint=RailColors.Cyan)},title={Text("Start live monitoring?")},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Your phone's motion sensors will record vibration. Optional location provides approximate positions and speed; denying it does not block detection.")
            Text(if(config?.localHistory==false)"Local history is off. This session remains in memory only." else "Trip history is saved locally. Raw data ${if(config?.recording==true)"will also be recorded" else "recording is off"}.")
            Notice("Use a secured phone as a passenger. Never place it on tracks or enter restricted areas.",true)
        }},confirmButton={TextButton(onClick={startDialog=false;onStart(false);nav.navigate("live"){launchSingleTop=true}}){Text("Start monitoring")}},
        dismissButton={TextButton(onClick={startDialog=false}){Text("Cancel")}})
}
