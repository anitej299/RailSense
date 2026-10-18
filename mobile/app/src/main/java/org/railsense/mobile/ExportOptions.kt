package org.railsense.mobile

/** Location is opt-in for every export, including event coordinates and raw GPS columns. */
data class ExportOptions(val json: Boolean=true,val sensors: Boolean=true,val gps: Boolean=false,
    val events: Boolean=true,val metadata: Boolean=true) {
    fun validate() { require(sensors || gps || events || metadata) { "Choose at least one content type" } }
    fun sensorColumns(): List<Int> = (0..17).filter { gps || it !in 11..16 }
}
