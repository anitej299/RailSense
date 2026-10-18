package org.railsense.mobile

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.io.File

@Entity(tableName="trips")
data class TripEntity(@PrimaryKey val id: String, val startMs: Long, val endMs: Long? = null,
    val device: String, val sensors: String, val model: String, val source: String,
    val status: String = "ACTIVE", val recording: Boolean, val samples: Long = 0,
    val locatedSamples: Long = 0, val drops: Long = 0, val config: String)
@Entity(tableName="events",indices=[Index("tripId")],foreignKeys=[ForeignKey(entity=TripEntity::class,parentColumns=["id"],childColumns=["tripId"],onDelete=ForeignKey.CASCADE)])
data class EventEntity(@PrimaryKey val id: String, val tripId: String, val startMs: Long, val endMs: Long,
    val peak: Double, val mean: Double, val windows: Int, val latitude: Double?, val longitude: Double?,
    val accuracy: Float?, val speed: Float?, val quality: String, val model: String,
    val features: String, val waveform: String, val acknowledged: Boolean = false)
@Entity(tableName="locations",indices=[Index("tripId")],foreignKeys=[ForeignKey(entity=TripEntity::class,parentColumns=["id"],childColumns=["tripId"],onDelete=ForeignKey.CASCADE)])
data class LocationEntity(@PrimaryKey(autoGenerate=true) val id: Long = 0,val tripId: String,val ns: Long,
    val wallMs: Long,val latitude: Double,val longitude: Double,val accuracy: Float,val speed: Float?)
data class TripOverview(@Embedded val trip: TripEntity, val eventCount: Int)
@Dao interface TripDao {
    @Query("SELECT * FROM events ORDER BY startMs DESC LIMIT 1") fun observeLatestEvent(): Flow<EventEntity?>
    @Insert suspend fun insert(trip: TripEntity)
    @Update suspend fun update(trip: TripEntity)
    @Query("SELECT * FROM trips ORDER BY startMs DESC") fun observeTrips(): Flow<List<TripEntity>>
    @Query("SELECT trips.*, (SELECT COUNT(*) FROM events WHERE events.tripId=trips.id) AS eventCount FROM trips ORDER BY startMs DESC") fun observeOverview(): Flow<List<TripOverview>>
    @Query("SELECT * FROM events WHERE id=:id") fun observeEvent(id: String): Flow<EventEntity?>
    @Query("SELECT * FROM trips WHERE id=:id") suspend fun trip(id: String): TripEntity?
    @Query("SELECT * FROM events WHERE tripId=:id ORDER BY startMs DESC") fun observeEvents(id: String): Flow<List<EventEntity>>
    @Query("SELECT * FROM events WHERE tripId=:id ORDER BY startMs") suspend fun events(id: String): List<EventEntity>
    @Query("SELECT * FROM events WHERE id=:id") suspend fun event(id: String): EventEntity?
    @Upsert suspend fun save(event: EventEntity)
    @Insert suspend fun location(fix: LocationEntity)
    @Query("SELECT * FROM locations WHERE tripId=:id ORDER BY ns DESC LIMIT 2000") fun observeLocations(id: String): Flow<List<LocationEntity>>
    @Query("SELECT * FROM locations WHERE tripId=:id AND id>:after ORDER BY id LIMIT 500") suspend fun locationPage(id: String, after: Long): List<LocationEntity>
    @Query("UPDATE events SET acknowledged=1 WHERE id=:id") suspend fun acknowledge(id: String)
    @Query("DELETE FROM trips WHERE id=:id AND status!='ACTIVE'") suspend fun delete(id: String)
    @Query("UPDATE trips SET status='INTERRUPTED', endMs=MIN(COALESCE(endMs,startMs),:now) WHERE status='ACTIVE'") suspend fun recover(now: Long)
}
object Store {
    @Volatile private var instance: RailSenseDatabase? = null
    fun db(context: Context): RailSenseDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(context.applicationContext,RailSenseDatabase::class.java,"railsense.db")
            .addMigrations(MIGRATION_1_2).build().also { instance=it }
    }
    fun recording(context: Context,id: String) = File(File(context.filesDir,"recordings").apply{mkdirs()},"$id.csv")
    suspend fun delete(context: Context,id: String) {
        val dao=db(context).trips(); val trip=dao.trip(id) ?: return
        check(trip.status!="ACTIVE") { "Stop the trip before deleting it" }
        val file=recording(context,id)
        check(!file.exists() || file.delete()) { "Unable to delete recording" }
        dao.delete(id)
    }
    val MIGRATION_1_2 = object: Migration(1,2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS trips (id TEXT NOT NULL PRIMARY KEY, startMs INTEGER NOT NULL, endMs INTEGER, device TEXT NOT NULL, sensors TEXT NOT NULL, model TEXT NOT NULL, source TEXT NOT NULL, status TEXT NOT NULL, recording INTEGER NOT NULL, samples INTEGER NOT NULL, locatedSamples INTEGER NOT NULL, drops INTEGER NOT NULL, config TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS events (id TEXT NOT NULL PRIMARY KEY, tripId TEXT NOT NULL, startMs INTEGER NOT NULL, endMs INTEGER NOT NULL, peak REAL NOT NULL, mean REAL NOT NULL, windows INTEGER NOT NULL, latitude REAL, longitude REAL, accuracy REAL, speed REAL, quality TEXT NOT NULL, model TEXT NOT NULL, features TEXT NOT NULL, waveform TEXT NOT NULL, acknowledged INTEGER NOT NULL, FOREIGN KEY(tripId) REFERENCES trips(id) ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_events_tripId ON events(tripId)")
            db.execSQL("CREATE TABLE IF NOT EXISTS locations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tripId TEXT NOT NULL, ns INTEGER NOT NULL, wallMs INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, accuracy REAL NOT NULL, speed REAL, FOREIGN KEY(tripId) REFERENCES trips(id) ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_locations_tripId ON locations(tripId)")
        }
    }
}

