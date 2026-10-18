package org.railsense.mobile

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class LoadResult<T>(val data: T?=null,val loading: Boolean=false,val error: String?=null)
data class DeviceSensors(val accelerometer: Boolean,val gyro: Boolean,val linear: Boolean,val maxHz: Int)
data class PermissionState(val location: Boolean=false,val notifications: Boolean=false)
data class ExportState(val busy: Boolean=false,val message: String="",val uri: Uri?=null,val error: Boolean=false)

class RailSenseViewModel(application: Application,private val saved: SavedStateHandle): AndroidViewModel(application) {
    private val context=application
    private val dao=Store.db(application).trips()
    private val preferences=Preferences(application)
    val live=MonitorState.live.asStateFlow()
    val latestEvent=dao.observeLatestEvent().catch { emit(null) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    val messages=Channel<String>(Channel.BUFFERED)
    val shareRequests=Channel<Pair<Uri,Boolean>>(Channel.BUFFERED)
    val permissions=MutableStateFlow(PermissionState())
    val exportState=MutableStateFlow(ExportState())
    val excerpt=MutableStateFlow(LoadResult<List<SignalPoint>>())
    private val reload=MutableStateFlow(0)
    val config: StateFlow<MonitorConfig?> = preferences.config.map { it as MonitorConfig? }
        .catch { messages.send("Settings could not be loaded. Retry after reopening the app.");emit(null) }
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val trips=reload.flatMapLatest { dao.observeOverview().map{LoadResult(data=it)}
        .onStart{emit(LoadResult(loading=true))}.catch{emit(LoadResult(error=it.message?:"Unable to load trips"))} }
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),LoadResult(loading=true))
    val sensors: DeviceSensors
    init {
        val manager=application.getSystemService(SensorManager::class.java)
        val accel=manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensors=DeviceSensors(accel!=null,manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)!=null,
            manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)!=null,
            if(accel!=null && accel.minDelay>0)(1_000_000/accel.minDelay).coerceAtMost(100) else 100)
        refreshPermissions()
    }
    fun refreshPermissions() {
        permissions.value=PermissionState(SensorCollector.hasLocationPermission(context),Build.VERSION.SDK_INT<33 ||
            ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
    }
    fun retry() { reload.value++ }
    fun events(id: String): Flow<LoadResult<List<EventEntity>>> = combine(dao.observeEvents(id),live) { stored,state ->
        LoadResult(data=if(stored.isEmpty() && state.tripId==id)state.sessionEvents.sortedByDescending{it.startMs} else stored)
    }.distinctUntilChanged().catch{emit(LoadResult(error=it.message?:"Unable to load events"))}
    fun locations(id: String): Flow<LoadResult<List<LocationEntity>>> = combine(dao.observeLocations(id),live) { stored,state ->
        LoadResult(data=if(state.tripId==id && state.running)state.route else stored)
    }.distinctUntilChanged().catch{emit(LoadResult(error=it.message?:"Unable to load locations"))}
    fun event(id: String): Flow<LoadResult<EventEntity>> = combine(dao.observeEvent(id),live) { stored,state ->
        val item=stored ?: state.sessionEvents.find{it.id==id}
        LoadResult(data=item?.let{if(MonitorState.acknowledged.contains(id))it.copy(acknowledged=true) else it},error=if(item==null)"This event is no longer available on this phone." else null)
    }.distinctUntilChanged().catch{emit(LoadResult(error=it.message?:"Unable to load event"))}
    fun acknowledge(id: String) {
        viewModelScope.launch {
            runCatching {
                dao.acknowledge(id);MonitorState.acknowledged.add(id)
                MonitorState.live.update { current -> current.copy(
                    event=current.event?.let{if(it.id==id)it.copy(acknowledged=true) else it},
                    sessionEvents=current.sessionEvents.map{if(it.id==id)it.copy(acknowledged=true) else it}) }
            }
                .onSuccess{messages.send("Event acknowledged")}.onFailure{messages.send("Acknowledgement failed: ${it.message}")}
        }
    }
    fun saveConfig(value: MonitorConfig) {
        viewModelScope.launch {
            runCatching {
                check(!live.value.running) { "Stop monitoring before changing settings" }
                require(value.hz<=sensors.maxHz) { "This accelerometer advertises a maximum of ${sensors.maxHz} Hz" }
                preferences.save(value.validate())
            }.onSuccess{messages.send("Settings saved for your next trip")}.onFailure{messages.send(it.message?:"Unable to save settings")}
        }
    }
    fun deleteTrip(id: String,onSuccess: ()->Unit) {
        viewModelScope.launch {
            runCatching{withContext(Dispatchers.IO){Store.delete(context,id)}}
                .onSuccess{messages.send("Trip deleted from this phone");onSuccess()}.onFailure{messages.send(it.message?:"Unable to delete trip")}
        }
    }
    fun rememberExport(id: String,options: ExportOptions) {
        saved["exportTrip"]=id;saved["exportJson"]=options.json;saved["exportSensors"]=options.sensors
        saved["exportGps"]=options.gps;saved["exportEvents"]=options.events;saved["exportMetadata"]=options.metadata
    }
    fun saveExport(uri: Uri?) {
        if(uri==null) { exportState.value=ExportState(message="Save cancelled. No file was exported.");return }
        val id=saved.get<String>("exportTrip") ?: return
        val options=ExportOptions(saved["exportJson"]?:true,saved["exportSensors"]?:true,saved["exportGps"]?:false,saved["exportEvents"]?:true,saved["exportMetadata"]?:true)
        performExport(id,options,uri)
    }
    fun shareExport(id: String,options: ExportOptions) = performExport(id,options,null)
    private fun performExport(id: String,options: ExportOptions,destination: Uri?) {
        if(exportState.value.busy)return
        exportState.value=ExportState(true,"Preparing export…")
        viewModelScope.launch {
            runCatching {
                val file=CsvExporter.create(context,id,options){exportState.value=ExportState(true,it)}
                if(destination!=null) {
                    try { CsvExporter.save(context,file,destination) } finally { file.delete() }
                    exportState.value=ExportState(message="Saved successfully to your chosen destination.",uri=destination)
                } else {
                    val uri=FileProvider.getUriForFile(context,"${context.packageName}.exports",file)
                    exportState.value=ExportState(message="Export ready. Choose a sharing app.",uri=uri)
                    shareRequests.send(uri to options.json)
                }
            }.onFailure { exportState.value=ExportState(message="Export failed: ${it.message}. Review the selection and try again.",error=true) }
        }
    }
    fun loadExcerpt(event: EventEntity) {
        excerpt.value=LoadResult(loading=true)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val file=Store.recording(context,event.tripId)
                check(file.exists()) { "No raw sensor file was recorded for this trip. The stored peak-window excerpt is shown above." }
                val points=ArrayList<SignalPoint>()
                file.bufferedReader().use { reader ->
                    reader.readLine();var line=reader.readLine();var scanned=0
                    while(line!=null && points.size<1000) {
                        if(scanned++%1000==0)ensureActive()
                        val cells=line.split(",")
                        if(cells.size==18) { val ms=cells[1].toLong()
                            if(ms>event.endMs+2000)break
                            if(ms>=event.startMs-2000)points+=SignalPoint(ms,Vec3(cells[2].toDouble(),cells[3].toDouble(),cells[4].toDouble()).norm())
                        };line=reader.readLine()
                    }
                };points.toList()
            } }.onSuccess{excerpt.value=LoadResult(data=it)}.onFailure{excerpt.value=LoadResult(error=it.message)}
        }
    }
}
