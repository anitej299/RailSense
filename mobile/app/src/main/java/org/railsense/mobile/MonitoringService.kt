package org.railsense.mobile

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import java.io.BufferedWriter
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

data class SignalPoint(val wallMs: Long, val value: Double)
data class LiveState(val running: Boolean=false, val tripId: String?=null, val startMs: Long=0,
    val elapsedSeconds: Long=0, val source: String="REAL_WORLD", val sample: Sample?=null,
    val features: Features?=null, val score: Double=0.0, val scores: List<Double> = emptyList(),
    val event: EventEntity?=null, val eventCount: Int=0, val latencyMs: Double=0.0,
    val drops: Long=0, val status: String="Stopped", val note: String="", val model: String="",
    val localHistory: Boolean=true, val route: List<LocationEntity> = emptyList(),
    val sessionEvents: List<EventEntity> = emptyList(), val waveform: List<SignalPoint> = emptyList())
object MonitorState {
    val live=MutableStateFlow(LiveState())
    val acknowledged=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
}
private sealed interface Input {
    data class Motion(val sample: Sample): Input
    data class Position(val fix: Fix): Input
}
class MonitoringService : Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val queue=Channel<Input>(1024)
    private val dropped=AtomicLong()
    private var collector: SensorCollector?=null
    private var demoJob: Job?=null
    private var ticker: Job?=null
    private var worker: Job?=null
    private var writer: BufferedWriter?=null
    private var wake: PowerManager.WakeLock?=null
    private var started=false
    @Volatile private var stopping=false
    @Volatile private var latest=LiveState()
    @Volatile private var lastSampleNs=0L
    @Volatile private var note=""
    private var trip: TripEntity?=null
    private var failure: String?=null
    private var persist=true
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action=="STOP") { stopMonitoring(); return START_NOT_STICKY }
        if(started || intent==null) return START_NOT_STICKY
        started=true
        val demo=intent.getBooleanExtra("demo",false)
        channels()
        try {
            val location=SessionPolicy.collectLocation(intent.getBooleanExtra("gps",true),demo,SensorCollector.hasLocationPermission(this))
            val type=if(Build.VERSION.SDK_INT>=34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                (if(location) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0) else if(location && Build.VERSION.SDK_INT>=29)ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            ServiceCompat.startForeground(this,1,ongoing(),type)
        } catch(e: Exception) {
            MonitorState.live.value=LiveState(status="Cannot start monitoring: ${e.message}")
            stopSelf(); return START_NOT_STICKY
        }
        MonitorState.live.value=LiveState(running=true,status="Starting…")
        worker=scope.launch {
            try { runSession(demo) }
            catch(e: Exception) { failure=e.message ?: e.javaClass.simpleName }
            finally {
                withContext(NonCancellable) {
                    collector?.stop(); demoJob?.cancel(); ticker?.cancel()
                    runCatching { writer?.close() }.onFailure { failure="Recording close failed: ${it.message}" }
                    writer=null
                    trip?.takeIf { persist }?.let { t -> runCatching { Store.db(this@MonitoringService).trips().update(t.copy(
                        endMs=System.currentTimeMillis(),status=if(failure==null)"STOPPED" else "ERROR",drops=dropped.get())) }
                        .onFailure { failure="Storage failure: ${it.message}" } }
                    wake?.let { if(it.isHeld)it.release() }
                    MonitorState.live.value=latest.copy(running=false,status=failure?.let{"Stopped: $it"}?:"Stopped",note=note)
                    withContext(Dispatchers.Main) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
                }
            }
        }
        return START_NOT_STICKY
    }
    private suspend fun runSession(demo: Boolean) {
        val config=Preferences(this).config.first().validate()
        persist=config.localHistory
        MonitorState.acknowledged.clear()
        val model=ModelLoader.load(this,config)
        val dao=Store.db(this).trips()
        (application as RailSenseApp).ready.await()
        val id=UUID.randomUUID().toString()
        val origin=SystemClock.elapsedRealtimeNanos()
        val start=System.currentTimeMillis()
        trip=TripEntity(id,start,device="${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE}",
            sensors=SensorCollector.availability(this),model=model.version,source=if(demo)"SYNTHETIC_TEST" else "REAL_WORLD",
            recording=config.recording && persist,config=config.toString())
        if(persist)dao.insert(trip!!)
        if(config.recording && persist) {
            check(filesDir.usableSpace > 20*1024*1024) { "Less than 20 MB free storage" }
            writer=Store.recording(this,id).bufferedWriter()
            writer!!.appendLine(RecordingCsv.header)
        }
        val window=WindowBuffer(config); val detector=EventDetector(config)
        latest=LiveState(true,id,start,source=trip!!.source,status="Monitoring",model=model.version,localHistory=persist)
        wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"RailSense:monitoring").apply { setReferenceCounted(false); acquire(60*60*1000L) }
        var lastRenew=origin
        ticker=scope.launch {
            while(isActive) {
                val now=SystemClock.elapsedRealtimeNanos()
                if(now-lastRenew>30*60*1_000_000_000L) { wake?.acquire(60*60*1000L); lastRenew=now }
                val stale=now-(if(lastSampleNs==0L)origin else lastSampleNs)>3_000_000_000L
                val power=getSystemService(PowerManager::class.java)
                val battery=getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                MonitorState.live.value=latest.copy(elapsedSeconds=(now-origin)/1_000_000_000L,drops=dropped.get(),
                    status=if(stale)"Waiting for sensor callbacks" else "Monitoring",
                    note=listOf(note,if(stale)"No recent samples; no inference" else "",if(power.isPowerSaveMode || battery in 0..15)"Low battery / battery saver: sampling may be restricted" else "").filter{it.isNotEmpty()}.joinToString(" • "))
                delay(200)
            }
        }
        fun enqueue(input: Input) { if(!queue.trySend(input).isSuccess)dropped.incrementAndGet() }
        if(!stopping) {
            if(demo) demoJob=scope.launch {
                var i=0L
                while(isActive) {
                    val target=origin+i*1_000_000_000L/config.hz
                    val wait=(target-SystemClock.elapsedRealtimeNanos())/1_000_000
                    if(wait>0)delay(wait)
                    enqueue(Input.Motion(SyntheticSource.sample(i++,config.hz,origin,start)))
                }
            } else {
                collector=SensorCollector(this,config,{enqueue(Input.Motion(it))},{enqueue(Input.Position(it))},{note=it})
                collector!!.start()
            }
        }
        var lastFlush=origin
        var lastWave=origin
        val history=ArrayDeque<SignalPoint>()
        val scores=ArrayDeque<Double>()
        for(input in queue) {
            when(input) {
                is Input.Position -> {
                    val f=input.fix
                    val location=LocationEntity(tripId=id,ns=f.ns,wallMs=f.wallMs,latitude=f.latitude,longitude=f.longitude,accuracy=f.accuracy,speed=f.speed)
                    if(persist)dao.location(location)
                    latest=latest.copy(route=(latest.route+location).takeLast(2000))
                }
                is Input.Motion -> {
                    val s=input.sample; lastSampleNs=s.ns
                    trip=trip!!.copy(endMs=s.wallMs,samples=trip!!.samples+1,locatedSamples=trip!!.locatedSamples+if(s.fix!=null)1 else 0,drops=dropped.get())
                    writer?.appendLine(RecordingCsv.row(s,trip!!.source))
                    latest=latest.copy(sample=s)
                    history.addLast(SignalPoint(s.wallMs,s.acceleration.norm()))
                    while(history.size>600 || (history.size>1 && history.first().wallMs<s.wallMs-6000))history.removeFirst()
                    if(s.ns-lastWave>=200_000_000L) { latest=latest.copy(waveform=history.toList());lastWave=s.ns }
                    val samples=window.add(s)
                    if(samples!=null && samples.size>=2) {
                        val extracted=FeatureExtractor.extract(samples,config)
                        val f=if(config.model!="baseline" && kotlin.math.abs(extracted.actualHz-config.hz)>config.hz*.2)
                            extracted.copy(quality="MODEL_RATE_MISMATCH") else extracted
                        val before=System.nanoTime()
                        val eligible=f.quality in listOf("GOOD","ACCEL_ONLY") && (config.model=="baseline" || f.quality=="GOOD")
                        val score=if(eligible)model.score(f) else 0.0
                        val latency=(System.nanoTime()-before)/1e6
                        scores.addLast(score); while(scores.size>120)scores.removeFirst()
                        latest=latest.copy(features=f,score=score,latencyMs=latency,scores=scores.toList())
                        detector.evaluate(samples,f,score)?.let { update ->
                            val c=update.candidate; val eventId="$id-${c.startNs}"
                            val event=EventEntity(eventId,id,c.startMs,c.endMs,c.peak,c.mean,c.count,c.fix?.latitude,c.fix?.longitude,
                                c.fix?.accuracy,c.fix?.speed,c.features.quality,model.version,
                                c.features.vector().joinToString(","),c.features.waveform.joinToString(","),
                                MonitorState.acknowledged.contains(eventId) || (if(persist)dao.event(eventId)?.acknowledged else false)==true)
                            if(persist)dao.save(event)
                            latest=latest.copy(event=event,eventCount=latest.eventCount+if(update.newEvent)1 else 0,
                                sessionEvents=(latest.sessionEvents.filterNot{it.id==event.id}+event).takeLast(200))
                            if(update.newEvent && config.notifications) alert(event)
                        }
                    }
                    if(s.ns-lastFlush>=2_000_000_000L) {
                        if(persist)check(filesDir.usableSpace>10*1024*1024) { "Storage low; monitoring stopped to protect recording" }
                        writer?.flush(); if(persist)dao.update(trip!!); lastFlush=s.ns
                    }
                }
            }
        }
    }
    private fun stopMonitoring() {
        if(stopping)return
        stopping=true; collector?.stop(); demoJob?.cancel(); queue.close()
        if(worker==null)stopSelf()
    }
    override fun onDestroy() {
        stopMonitoring()
        worker?.invokeOnCompletion { scope.cancel() } ?: scope.cancel()
        super.onDestroy()
    }
    private fun channels() {
        getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(NotificationChannel("monitor","Active monitoring",NotificationManager.IMPORTANCE_LOW))
            createNotificationChannel(NotificationChannel("candidates","Vibration candidates",NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
    private fun openIntent()=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun ongoing(): Notification {
        val stop=PendingIntent.getService(this,1,Intent(this,MonitoringService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this,"monitor").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("RailSense • recording active").setContentText("Experimental vibration monitoring • tap to review")
            .setContentIntent(openIntent()).setOngoing(true).addAction(0,"Stop",stop).build()
    }
    private fun alert(event: EventEntity) {
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        val location=if(event.latitude==null)"GPS unavailable" else "Approx. ${event.latitude}, ${event.longitude} ±${event.accuracy} m"
        val text="${java.time.Instant.ofEpochMilli(event.startMs)} • score ${"%.2f".format(event.peak)} • $location"
        runCatching { getSystemService(NotificationManager::class.java).notify(2,NotificationCompat.Builder(this,"candidates")
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Potential vibration anomaly detected")
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText("$text. Review the event and its approximate location."))
            .setContentIntent(openIntent()).setAutoCancel(true).build()) }.onFailure { note="Notification unavailable: ${it.message}" }
    }
}

