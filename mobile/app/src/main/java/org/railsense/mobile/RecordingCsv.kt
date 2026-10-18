package org.railsense.mobile

object RecordingCsv {
    const val header="monotonic_ns,wall_time_ms,accel_x,accel_y,accel_z,gyro_x,gyro_y,gyro_z,linear_x,linear_y,linear_z,latitude,longitude,gps_accuracy_m,speed_m_s,gps_monotonic_ns,gps_wall_time_ms,data_source"
    fun cell(value: Any?): String {
        val s=value?.toString()?:""
        return if(s.any{it==',' || it=='\"' || it=='\n' || it=='\r'}) "\"${s.replace("\"","\"\"")}\"" else s
    }
    fun row(s: Sample,source: String)=listOf(s.ns,s.wallMs,s.acceleration.x,s.acceleration.y,s.acceleration.z,
        s.gyro?.x,s.gyro?.y,s.gyro?.z,s.linear?.x,s.linear?.y,s.linear?.z,s.fix?.latitude,s.fix?.longitude,s.fix?.accuracy,s.fix?.speed,s.fix?.ns,s.fix?.wallMs,source).joinToString(","){cell(it)}
}
