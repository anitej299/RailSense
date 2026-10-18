package org.railsense.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun tripEventPersistenceAcknowledgementAndCascade() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,RailSenseDatabase::class.java).build()
        try {
            val dao=db.trips()
            dao.insert(TripEntity("test",1,device="test",sensors="test",model="baseline",source="SYNTHETIC_TEST",recording=false,config="test"))
            dao.save(EventEntity("event","test",1,2,.8,.7,2,null,null,null,null,"GOOD","baseline","1,2,3,4,5,6,7","1,2,3"))
            dao.acknowledge("event");assertTrue(dao.event("event")!!.acknowledged)
            dao.recover(3);assertEquals("INTERRUPTED",dao.trip("test")!!.status)
            dao.delete("test");assertNull(dao.event("event"))
        } finally { db.close() }
    }
    @Test fun missingModelFailsClearly() {
        assertTrue(runCatching{ModelLoader.load(context,MonitorConfig(model="centroid"),"definitely-missing-model.json")}.isFailure)
    }

    @Test fun versionOneMigrationPreservesLegacySamples() = runBlocking {
        val name="migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name,0,null).use { old ->
            old.execSQL("CREATE TABLE sensor_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestampUtc TEXT NOT NULL, latitude REAL, longitude REAL, accelX REAL NOT NULL, accelY REAL NOT NULL, accelZ REAL NOT NULL, gyroX REAL NOT NULL, gyroY REAL NOT NULL, gyroZ REAL NOT NULL, speed REAL, deviceModel TEXT NOT NULL, anonymousDeviceId TEXT NOT NULL, samplingRate INTEGER NOT NULL, journeyId TEXT NOT NULL, placementId TEXT NOT NULL, routeId TEXT NOT NULL)")
            old.execSQL("INSERT INTO sensor_records VALUES (1,'2026-01-01',NULL,NULL,0,0,9.81,0,0,0,NULL,'test','anonymous',50,'legacy','seat','none')")
            old.version=1
        }
        val db=Room.databaseBuilder(context,RailSenseDatabase::class.java,name).addMigrations(Store.MIGRATION_1_2).build()
        try {
            assertEquals(1,db.sensorDao().journey("legacy").size)
            assertEquals(0,db.trips().events("legacy").size)
        } finally { db.close();context.deleteDatabase(name) }
    }
}
