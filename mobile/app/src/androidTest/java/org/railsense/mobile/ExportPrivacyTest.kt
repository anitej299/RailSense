package org.railsense.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExportPrivacyTest {
    @Test fun gpsOptOutRedactsEveryExportSection() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        (context.applicationContext as RailSenseApp).ready.await()
        val id="export-test-${UUID.randomUUID()}";val dao=Store.db(context).trips()
        val trip=TripEntity(id,1000,2000,"test-device","test-sensors","baseline","SYNTHETIC_TEST","STOPPED",true,1,1,0,"test")
        dao.insert(trip)
        val fix=Fix(1_000_000_000,1000,12.34567,76.54321,12f,8f)
        Store.recording(context,id).writeText(RecordingCsv.header+"\n"+RecordingCsv.row(Sample(1_000_000_000,1000,Vec3(0.0,0.0,9.81),null,null,fix),"SYNTHETIC_TEST")+"\n")
        dao.save(EventEntity("$id-event",id,1000,2000,.9,.8,2,fix.latitude,fix.longitude,fix.accuracy,fix.speed,"GOOD","baseline","1,2,3,4,5,6,7","1,2,3"))
        dao.location(LocationEntity(tripId=id,ns=fix.ns,wallMs=fix.wallMs,latitude=fix.latitude,longitude=fix.longitude,accuracy=fix.accuracy,speed=fix.speed))
        try {
            for(json in listOf(true,false)) {
                val file=CsvExporter.create(context,id,ExportOptions(json=json,gps=false))
                try {
                    val text=file.readText()
                    assertFalse(text.contains("12.34567"));assertFalse(text.contains("76.54321"))
                    assertFalse(text.contains("latitude"));assertFalse(text.contains("gps_wall_time_ms"))
                    if(json) { val data=JSONObject(text);assertFalse(data.has("locations"));assertFalse(data.getJSONArray("events").getJSONObject(0).has("speedMps")) }
                } finally { file.delete() }
            }
            val file=CsvExporter.create(context,id,ExportOptions(gps=true))
            try { assertTrue(file.readText().contains("12.34567")) } finally { file.delete() }
        } finally { Store.delete(context,id) }
    }
}
