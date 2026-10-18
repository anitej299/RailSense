package org.railsense.mobile

import kotlin.math.*

object SessionPolicy {
    fun collectLocation(requested: Boolean, synthetic: Boolean, granted: Boolean) = requested && !synthetic && granted
}

data class MonitorConfig(
    val hz: Int = 50, val windowSeconds: Double = 2.0, val overlap: Double = .5,
    val threshold: Double = .65, val confirmations: Int = 2, val cooldownSeconds: Int = 30,
    val gps: Boolean = true, val recording: Boolean = true, val notifications: Boolean = true,
    val model: String = "baseline", val localHistory: Boolean = true,
    val highThreshold: Double = .85
) {
    fun validate(): MonitorConfig {
        require(hz in 20..100) { "Sampling rate must be 20–100 Hz" }
        require(windowSeconds.isFinite() && windowSeconds in 1.0..5.0) { "Window must be 1–5 seconds" }
        require(overlap.isFinite() && overlap in 0.0..0.9) { "Overlap must be 0–0.9" }
        require(threshold.isFinite() && threshold in .05..1.0) { "Threshold must be 0.05–1" }
        require(confirmations in 1..10 && cooldownSeconds in 0..600) { "Confirmation 1–10; cooldown 0–600 seconds" }
        require(model in listOf("baseline", "centroid")) { "Unknown model" }
        require(highThreshold.isFinite() && highThreshold in threshold..1.0) { "High signal threshold must be between the detection threshold and 1" }
        return this
    }
}

data class Vec3(val x: Double, val y: Double, val z: Double) {
    fun norm() = sqrt(x*x + y*y + z*z)
    fun valid() = x.isFinite() && y.isFinite() && z.isFinite()
    operator fun minus(other: Vec3) = Vec3(x-other.x,y-other.y,z-other.z)
}
data class Fix(val ns: Long, val wallMs: Long, val latitude: Double, val longitude: Double,
               val accuracy: Float, val speed: Float?) {
    fun valid() = latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() &&
        longitude in -180.0..180.0 && accuracy.isFinite() && accuracy > 0 &&
        (speed == null || speed.isFinite() && speed >= 0)
}
data class Sample(val ns: Long, val wallMs: Long, val acceleration: Vec3, val gyro: Vec3?,
                  val linear: Vec3?, val fix: Fix?)

/** All streams use elapsedRealtimeNanos. Never attach a future or stale reading. */
class Synchronizer {
    private val gyros = ArrayDeque<Pair<Long, Vec3>>()
    private val linears = ArrayDeque<Pair<Long, Vec3>>()
    private val fixes = ArrayDeque<Fix>()
    fun gyro(ns: Long, value: Vec3) = add(gyros, ns, value)
    fun linear(ns: Long, value: Vec3) = add(linears, ns, value)
    private fun add(q: ArrayDeque<Pair<Long, Vec3>>, ns: Long, v: Vec3) {
        if (!v.valid() || ns <= (q.lastOrNull()?.first ?: -1L)) return
        q.addLast(ns to v); while (q.size > 256) q.removeFirst()
    }
    fun location(fix: Fix) {
        if (!fix.valid() || fix.ns <= (fixes.lastOrNull()?.ns ?: -1L)) return
        fixes.addLast(fix); while (fixes.size > 32) fixes.removeFirst()
    }
    private fun latest(q: ArrayDeque<Pair<Long, Vec3>>, ns: Long) =
        q.lastOrNull { it.first <= ns && ns-it.first <= 100_000_000L }?.second
    fun fix(ns: Long): Fix? {
        val f=fixes.lastOrNull { it.ns <= ns } ?: return null
        val age=(ns-f.ns)/1e9
        if(age > 5.0)return null
        // Include possible travel since the GPS timestamp, not just the provider accuracy.
        val uncertainty=f.accuracy+age*(f.speed?.toDouble() ?: 40.0)
        return if(uncertainty<=100) f.copy(accuracy=uncertainty.toFloat()) else null
    }
    fun sample(ns: Long, wall: Long, a: Vec3) = Sample(ns,wall,a,latest(gyros,ns),latest(linears,ns),fix(ns))
}

class WindowBuffer(private val config: MonitorConfig) {
    private val samples = ArrayDeque<Sample>()
    private var lastNs = -1L
    private var nextEnd = -1L
    var rejected = 0L; private set
    fun add(sample: Sample): List<Sample>? {
        if (sample.ns <= lastNs || !sample.acceleration.valid()) { rejected++; return null }
        if (lastNs >= 0 && sample.ns-lastNs > 500_000_000L) { samples.clear(); nextEnd = -1 }
        lastNs = sample.ns
        val duration = (config.windowSeconds*1e9).toLong()
        if (nextEnd < 0) nextEnd = sample.ns+duration
        samples.addLast(sample)
        while (samples.size > 1 && samples.first().ns < sample.ns-duration) samples.removeFirst()
        while (samples.size > 2048) { samples.removeFirst(); rejected++ }
        if (sample.ns < nextEnd) return null
        nextEnd = sample.ns+(duration*(1-config.overlap)).toLong()
        return samples.toList()
    }
}

data class Features(val rms: Double, val std: Double, val peakToPeak: Double,
    val jerkRms: Double, val gyroRms: Double, val dominantHz: Double, val highBandRatio: Double,
    val actualHz: Double, val quality: String, val waveform: List<Double>) {
    fun vector() = doubleArrayOf(rms,std,peakToPeak,jerkRms,gyroRms,dominantHz,highBandRatio)
}
object FeatureExtractor {
    val order = listOf("rms_m_s2","std_m_s2","peak_to_peak_m_s2","jerk_rms_m_s3","gyro_rms_rad_s","dominant_hz","high_band_ratio")
    fun extract(w: List<Sample>, config: MonitorConfig): Features {
        require(w.size >= 2 && w.zipWithNext().all { (a,b) -> b.ns > a.ns })
        require(w.all { it.acceleration.valid() && (it.gyro?.valid() != false) })
        // Remove only the per-window DC vector. Retain vibration amplitude; do not normalize it away.
        val mean = Vec3(w.map{it.acceleration.x}.average(),w.map{it.acceleration.y}.average(),w.map{it.acceleration.z}.average())
        val mag = w.map { (it.acceleration-mean).norm() }
        val avg = mag.average()
        val rms = sqrt(mag.sumOf { it*it }/mag.size)
        val std = sqrt(mag.sumOf { (it-avg).pow(2) }/mag.size)
        val dts = w.zipWithNext { a,b -> (b.ns-a.ns)/1e9 }
        val hz = 1/dts.average()
        val irregular = dts.any { abs(it-dts.average()) > dts.average()*.5 }
        val coverage = (w.last().ns-w.first().ns)/1e9/config.windowSeconds
        val quality = when {
            coverage < .8 || hz < config.hz*.7 || dts.max() > .2 -> "INSUFFICIENT_SAMPLES"
            irregular -> "IRREGULAR_SAMPLING"
            w.count { it.gyro == null } > w.size/5 -> "ACCEL_ONLY"
            else -> "GOOD"
        }
        val jerk = sqrt(w.zipWithNext { a,b -> ((b.acceleration-a.acceleration).norm()/((b.ns-a.ns)/1e9)).pow(2) }.average())
        val gyro = w.mapNotNull { it.gyro?.norm() }.let { if(it.isEmpty()) 0.0 else sqrt(it.sumOf { g -> g*g }/it.size) }
        // Small bounded DFT on worker thread, only on sufficiently regular data; no interpolation claims.
        var peak = 0.0; var dominant = 0.0; var total = 0.0; var high = 0.0
        if (!irregular && w.size >= 32 && coverage >= .8) {
            for (k in 1..min(w.size/2,128)) {
                var re = 0.0; var im = 0.0
                for (i in mag.indices) {
                    val value = (mag[i]-avg)*(.5-.5*cos(2*PI*i/(mag.size-1)))
                    val angle = 2*PI*k*i/mag.size
                    re += value*cos(angle); im -= value*sin(angle)
                }
                val power = re*re+im*im; val frequency = k*hz/mag.size
                total += power; if(frequency >= 8) high += power
                if(power > peak) { peak = power; dominant = frequency }
            }
        }
        return Features(rms,std,mag.max()-mag.min(),jerk,gyro,dominant,if(total>1e-12)high/total else 0.0,hz,quality,mag)
    }
}
interface AnomalyModel { val version: String; fun score(features: Features): Double }
class StatisticalBaseline : AnomalyModel {
    override val version = "experimental-baseline-v2"
    override fun score(features: Features): Double {
        require(features.vector().all { it.isFinite() && it >= 0 })
        return (max(features.rms/4.0,features.peakToPeak/12.0)).coerceIn(0.0,1.0)
    }
}
/** Trained normal-only diagonal distance model; portable alternative to a Python Isolation Forest. */
class CentroidModel(override val version: String, private val means: DoubleArray,
    private val scales: DoubleArray, private val distanceScale: Double) : AnomalyModel {
    init { require(version.isNotBlank() && means.size == 7 && scales.size == 7 && means.all{it.isFinite()} && scales.all{it.isFinite() && it>0} && distanceScale.isFinite() && distanceScale>0) }
    override fun score(features: Features): Double {
        require(features.quality == "GOOD") { "Centroid model requires regular synchronized accelerometer and gyroscope windows" }
        val x = features.vector(); require(x.all{it.isFinite()})
        return (sqrt(x.indices.sumOf { ((x[it]-means[it])/scales[it]).pow(2) }/x.size)/distanceScale).coerceIn(0.0,1.0)
    }
}
data class Candidate(val startNs: Long, val endNs: Long, val startMs: Long, val endMs: Long,
    val peak: Double, val sum: Double, val count: Int, val fix: Fix?, val features: Features) {
    val mean get() = sum/count
}
data class EventUpdate(val candidate: Candidate, val newEvent: Boolean)
class EventDetector(private val config: MonitorConfig) {
    private var streak = 0
    private var pending: Candidate? = null
    private var active: Candidate? = null
    private var lastAlert = Long.MIN_VALUE
    fun evaluate(w: List<Sample>, f: Features, score: Double): EventUpdate? {
        val end = w.last()
        if (!score.isFinite() || f.quality !in listOf("GOOD","ACCEL_ONLY") || score < config.threshold) {
            streak=0; pending=null
            if(active != null && end.ns-active!!.endNs > (config.windowSeconds*1e9).toLong()) active=null
            return null
        }
        fun extend(c: Candidate) = c.copy(endNs=end.ns,endMs=end.wallMs,peak=max(c.peak,score),sum=c.sum+score,count=c.count+1,
            fix=if(score>c.peak)end.fix else c.fix,features=if(score>c.peak)f else c.features)
        active?.let {
            if (w.first().ns <= it.endNs) { val next=extend(it); active=next; return EventUpdate(next,false) }
            active=null
        }
        if(pending != null && w.first().ns > pending!!.endNs) { pending=null; streak=0 }
        val current = Candidate(w.first().ns,end.ns,w.first().wallMs,end.wallMs,score,score,1,end.fix,f)
        pending = pending?.let { extend(it) } ?: current
        streak++
        if(streak < config.confirmations) return null
        if(lastAlert != Long.MIN_VALUE && end.ns-lastAlert < config.cooldownSeconds*1_000_000_000L) return null
        val confirmed=pending!!; active=confirmed; pending=null; streak=0; lastAlert=end.ns
        return EventUpdate(confirmed,true)
    }
}
object SyntheticSource {
    fun sample(i: Long, hz: Int, originNs: Long, originMs: Long): Sample {
        val t=i.toDouble()/hz; val amplitude=if(t%20 in 7.0..13.0) 5.0 else .12
        return Sample(originNs+(t*1e9).toLong(),originMs+(t*1000).toLong(),
            Vec3(amplitude*sin(2*PI*6*t),.1*cos(t),9.81+amplitude*sin(2*PI*9*t)),Vec3(.01,0.0,.01),null,null)
    }
}
