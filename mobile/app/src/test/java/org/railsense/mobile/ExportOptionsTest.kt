package org.railsense.mobile

import org.junit.Assert.*
import org.junit.Test

class ExportOptionsTest {
    @Test fun locationIsNeverIncludedByDefault() {
        val options=ExportOptions()
        assertFalse(options.gps)
        assertFalse(options.sensorColumns().any{it in 11..16})
        assertTrue(options.sensorColumns().containsAll(listOf(0,1,2,3,4,5,6,7,8,9,10,17)))
    }
    @Test fun explicitLocationSelectionIncludesAllGpsColumns() {
        assertEquals((0..17).toList(),ExportOptions(gps=true).sensorColumns())
    }
    @Test(expected=IllegalArgumentException::class) fun emptyExportIsRejected() {
        ExportOptions(sensors=false,gps=false,events=false,metadata=false).validate()
    }
    @Test(expected=IllegalArgumentException::class) fun invalidSignalBandIsRejected() {
        MonitorConfig(threshold=.8,highThreshold=.7).validate()
    }
    @Test fun historyAndRawRecordingAreSeparatePreferences() {
        val config=MonitorConfig(localHistory=false,recording=true).validate()
        assertFalse(config.localHistory && config.recording)
        assertTrue(config.recording)
    }
}
