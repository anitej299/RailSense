package org.railsense.mobile

import android.content.Context
import android.net.Uri
import android.util.JsonWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object CsvExporter {
    suspend fun create(context: Context,id: String,options: ExportOptions,onProgress: (String)->Unit={}): File=withContext(Dispatchers.IO) {
        options.validate()
        val dao=Store.db(context).trips()
        val trip=requireNotNull(dao.trip(id)) { "Trip not found" }
        check(trip.status!="ACTIVE") { "Stop monitoring before exporting this trip" }
        val raw=Store.recording(context,id)
        if(options.sensors)check(raw.exists()) { "Raw sensor recording was off. Deselect sensor data to export the remaining content." }
        val dir=File(context.cacheDir,"exports").apply{mkdirs()}
        dir.listFiles()?.filter { it.name.startsWith("RailSense-") && it.lastModified()<System.currentTimeMillis()-86_400_000 }?.forEach{it.delete()}
        val file=File(dir,"RailSense-${id.take(8)}-${UUID.randomUUID().toString().take(6)}.${if(options.json)"json" else "csv"}")
        try {
            onProgress("Preparing selected data…")
            val events=if(options.events)dao.events(id) else emptyList()
            if(options.json)JsonWriter(file.writer()).use { out ->
                out.beginObject();out.name("schema").value("railsense-export-v3");out.name("source").value(trip.source)
                out.name("locationIncluded").value(options.gps)
                if(options.metadata) {
                    out.name("trip").beginObject();out.name("id").value(trip.id)
                    out.name("startMs").value(trip.startMs);out.name("endMs").value(trip.endMs)
                    out.name("device").value(trip.device);out.name("sensors").value(trip.sensors)
                    out.name("status").value(trip.status);out.name("model").value(trip.model)
                    out.name("config").value(trip.config);out.name("samples").value(trip.samples)
                    out.name("droppedMessages").value(trip.drops);out.endObject()
                }
                if(options.events) {
                    out.name("events").beginArray()
                    events.forEach { e ->
                        out.beginObject();out.name("id").value(e.id);out.name("startMs").value(e.startMs);out.name("endMs").value(e.endMs)
                        out.name("peakScore").value(e.peak);out.name("meanScore").value(e.mean);out.name("windows").value(e.windows)
                        if(options.gps) { out.name("latitude").value(e.latitude);out.name("longitude").value(e.longitude);out.name("accuracyM").value(e.accuracy);out.name("speedMps").value(e.speed) }
                        out.name("quality").value(e.quality);out.name("model").value(e.model);out.name("acknowledged").value(e.acknowledged)
                        out.name("features").beginObject();FeatureExtractor.order.zip(e.features.split(",")).forEach{(key,value)->out.name(key).value(value.toDouble())};out.endObject()
                        out.name("waveform").beginArray();e.waveform.split(",").mapNotNull{it.toDoubleOrNull()}.forEach{out.value(it)};out.endArray();out.endObject()
                    };out.endArray()
                }
                if(options.gps) {
                    out.name("locations").beginArray();var after=0L
                    while(true) {
                        val page=dao.locationPage(id,after);if(page.isEmpty())break
                        page.forEach { f ->
                            out.beginObject();out.name("wallMs").value(f.wallMs);out.name("monotonicNs").value(f.ns)
                            out.name("latitude").value(f.latitude);out.name("longitude").value(f.longitude);out.name("accuracyM").value(f.accuracy);out.name("speedMps").value(f.speed);out.endObject()
                        };after=page.last().id;onProgress("Writing location records…")
                    };out.endArray()
                }
                if(options.sensors) {
                    val columns=options.sensorColumns()
                    out.name("sensorColumns").beginArray();val header=RecordingCsv.header.split(",");columns.forEach{out.value(header[it])};out.endArray()
                    out.name("sensorData").beginArray();var count=0L
                    raw.bufferedReader().use { reader -> reader.readLine();reader.lineSequence().forEach { line ->
                        val row=line.split(",");if(row.size==18) {
                            out.beginArray();columns.forEach { index -> val v=row[index]
                                when { v.isEmpty()->out.nullValue();index==17->out.value(v);index in listOf(0,1,15,16)->out.value(v.toLong());else->out.value(v.toDouble()) }
                            };out.endArray();count++;if(count%500==0L)onProgress("Writing $count sensor samples…")
                        }
                    } };out.endArray()
                };out.endObject()
            } else file.bufferedWriter().use { out ->
                val rawHeader=RecordingCsv.header.split(",")
                val gpsNames=rawHeader.slice(11..16).toSet()
                val names=(listOf("record_type")+rawHeader+listOf("trip_id","event_id","end_ms","peak_score","mean_score","quality","model","device","status","sample_count","features","waveform","acknowledged")).filter { options.gps || it !in gpsNames }
                out.appendLine(names.joinToString(","))
                fun write(values: Map<String,Any?>) { out.appendLine(names.joinToString(","){RecordingCsv.cell(values[it])}) }
                if(options.metadata)write(mapOf("record_type" to "trip","trip_id" to id,"wall_time_ms" to trip.startMs,"end_ms" to trip.endMs,"data_source" to trip.source,"model" to trip.model,"device" to trip.device,"status" to trip.status,"sample_count" to trip.samples))
                events.forEach { e -> write(mapOf("record_type" to "event","event_id" to e.id,"wall_time_ms" to e.startMs,"end_ms" to e.endMs,"peak_score" to e.peak,"mean_score" to e.mean,"quality" to e.quality,"model" to e.model,"latitude" to e.latitude,"longitude" to e.longitude,"gps_accuracy_m" to e.accuracy,"speed_m_s" to e.speed,"data_source" to trip.source,"features" to e.features,"waveform" to e.waveform,"acknowledged" to e.acknowledged)) }
                if(options.gps) { var after=0L
                    while(true) { val page=dao.locationPage(id,after);if(page.isEmpty())break
                        page.forEach { f -> write(mapOf("record_type" to "location","monotonic_ns" to f.ns,"wall_time_ms" to f.wallMs,"latitude" to f.latitude,"longitude" to f.longitude,"gps_accuracy_m" to f.accuracy,"speed_m_s" to f.speed,"data_source" to trip.source)) };after=page.last().id
                    }
                }
                if(options.sensors) { var count=0L
                    raw.bufferedReader().use { reader -> reader.readLine();reader.lineSequence().forEach { line ->
                        val cells=line.split(",");if(cells.size==18) { write(rawHeader.zip(cells).toMap()+mapOf("record_type" to "sensor"));count++;if(count%500==0L)onProgress("Writing $count sensor samples…") }
                    } }
                }
            }
            onProgress("Export ready");file
        } catch(e: Exception) { file.delete();throw e }
    }
    suspend fun save(context: Context,file: File,uri: Uri)=withContext(Dispatchers.IO) {
        requireNotNull(context.contentResolver.openOutputStream(uri,"wt")) { "Unable to open selected destination" }.use { output -> file.inputStream().use{it.copyTo(output)} }
    }
    // Retained for callers that explicitly request the legacy full export.
    suspend fun export(context: Context,id: String,uri: Uri,json: Boolean) {
        val file=create(context,id,ExportOptions(json=json,gps=true));try { save(context,file,uri) } finally { file.delete() }
    }
}
