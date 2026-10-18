package org.railsense.mobile
import androidx.room.*

@Entity(tableName="sensor_records") data class SensorRecord(@PrimaryKey(autoGenerate=true) val id:Long=0,val timestampUtc:String,val latitude:Double?,val longitude:Double?,val accelX:Float,val accelY:Float,val accelZ:Float,val gyroX:Float,val gyroY:Float,val gyroZ:Float,val speed:Float?,val deviceModel:String,val anonymousDeviceId:String,val samplingRate:Int,val journeyId:String,val placementId:String,val routeId:String)
@Dao interface SensorDao { @Insert suspend fun insert(record:SensorRecord); @Query("SELECT * FROM sensor_records WHERE journeyId=:journey ORDER BY id") suspend fun journey(journey:String):List<SensorRecord> }
@Database(entities=[SensorRecord::class,TripEntity::class,EventEntity::class,LocationEntity::class],version=2,exportSchema=true)
abstract class RailSenseDatabase:RoomDatabase(){
    abstract fun sensorDao():SensorDao
    abstract fun trips():TripDao
}
