package org.railsense.mobile

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class PipelineTest {
    private val config=MonitorConfig()
    private fun sample(i: Int,a: Double=0.0, ns: Long=i*20_000_000L)=
        Sample(ns,1_700_000_000_000L+ns/1_000_000,Vec3(a,0.0,9.81),Vec3(0.0,0.0,0.0),null,null)
    private fun window(offset: Int=0)=(offset..offset+100).map{sample(it,sin(it*.6)*4)}
    @Test fun deniedLocationAndSyntheticSessionsDoNotRequestLocationService() {
        assertFalse(SessionPolicy.collectLocation(true,false,false))
        assertFalse(SessionPolicy.collectLocation(true,true,true))
        assertFalse(SessionPolicy.collectLocation(false,false,true))
        assertTrue(SessionPolicy.collectLocation(true,false,true))
        // Optional location is independent of the live acceleration pipeline.
        val w=window();assertTrue(StatisticalBaseline().score(FeatureExtractor.extract(w,config))>0)
    }
    @Test fun gpsTravelUncertaintySuppressesOldMovingFix() {
        val sync=Synchronizer();sync.location(Fix(0,0,12.0,77.0,10f,40f))
        assertEquals(50f,sync.fix(1_000_000_000)!!.accuracy,0f)
        assertNull(sync.fix(3_000_000_000))
    }
    @Test fun windowsUseTimeAndOverlap() {
        val buffer=WindowBuffer(config)
        val ends=(0..200).mapNotNull{buffer.add(sample(it))?.last()?.ns}
        assertEquals(listOf(2_000_000_000L,3_000_000_000L,4_000_000_000L),ends)
    }
    @Test fun duplicateAndBackwardsTimeRejected() {
        val buffer=WindowBuffer(config)
        buffer.add(sample(2));buffer.add(sample(2));buffer.add(sample(1))
        assertEquals(2,buffer.rejected.toInt())
    }
    @Test fun callbackInterruptionRestartsWindow() {
        val buffer=WindowBuffer(config)
        (0..100).forEach{buffer.add(sample(it))}
        assertNull(buffer.add(sample(300)))
        assertNull(buffer.add(sample(301)))
    }
    @Test fun sparseSamplesCannotTrigger() {
        val w=(0..20).map{sample(it,ns=it*100_000_000L)}
        val f=FeatureExtractor.extract(w,config)
        assertEquals("INSUFFICIENT_SAMPLES",f.quality)
        assertNull(EventDetector(config.copy(confirmations=1)).evaluate(w,f,1.0))
    }
    @Test fun irregularSamplingSuppressesFrequencyAndEvents() {
        var ns=0L
        val w=(0..100).map{ns+=if(it%2==0)5_000_000L else 35_000_000L;sample(it,sin(it.toDouble()),ns)}
        val f=FeatureExtractor.extract(w,config)
        assertEquals("IRREGULAR_SAMPLING",f.quality);assertEquals(0.0,f.dominantHz,0.0)
    }
    @Test fun constantGravityHasZeroDynamicEnergy() {
        val f=FeatureExtractor.extract((0..100).map{sample(it)},config)
        assertEquals(0.0,f.rms,1e-10);assertEquals(50.0,f.actualHz,1e-6)
        assertEquals(0.0,StatisticalBaseline().score(f),1e-10)
    }
    @Test fun sinusoidalFeaturesHaveExpectedEnergyAndMagnitudeFrequency() {
        val w=(0 until 100).map { sample(it,2*sin(2*PI*5*it/50.0)) }
        val f=FeatureExtractor.extract(w,config)
        assertEquals(sqrt(2.0),f.rms,1e-6)
        assertEquals(10.0,f.dominantHz,.51) // magnitude doubles a symmetric sine frequency
        assertTrue(f.jerkRms>40); assertTrue(f.highBandRatio>.9)
    }
    @Test fun missingGyroIsReportedWithoutInventingMeasurements() {
        val f=FeatureExtractor.extract(window().map{it.copy(gyro=null)},config)
        assertEquals("ACCEL_ONLY",f.quality);assertEquals(0.0,f.gyroRms,0.0)
    }
    @Test fun synchronizationRejectsFutureStaleAndInvalidFixes() {
        val sync=Synchronizer()
        sync.gyro(1_000_000_000,Vec3(1.0,2.0,3.0))
        assertNull(sync.sample(900_000_000,0,Vec3(0.0,0.0,9.81)).gyro)
        assertNotNull(sync.sample(1_050_000_000,0,Vec3(0.0,0.0,9.81)).gyro)
        assertNull(sync.sample(1_200_000_000,0,Vec3(0.0,0.0,9.81)).gyro)
        sync.location(Fix(1_000_000_000,0,12.0,77.0,20f,null))
        assertNull(sync.fix(999_999_999));assertNotNull(sync.fix(2_000_000_000))
        assertNull(sync.fix(7_000_000_000))
        sync.location(Fix(8_000_000_000,0,12.0,77.0,200f,1f));assertNull(sync.fix(8_000_000_000))
        sync.location(Fix(9_000_000_000,0,Double.NaN,77.0,2f,1f));assertNull(sync.fix(9_000_000_000))
    }
    @Test fun noGpsIsNull() { assertNull(Synchronizer().sample(0,0,Vec3(0.0,0.0,9.81)).fix) }
    @Test fun temporalConfirmationThenMerging() {
        val detector=EventDetector(config)
        val a=window();val b=window(50);val c=window(100)
        assertNull(detector.evaluate(a,FeatureExtractor.extract(a,config),.8))
        val second=detector.evaluate(b,FeatureExtractor.extract(b,config),.9)!!
        assertTrue(second.newEvent);assertEquals(a.first().ns,second.candidate.startNs)
        val third=detector.evaluate(c,FeatureExtractor.extract(c,config),.7)!!
        assertFalse(third.newEvent);assertEquals(.9,third.candidate.peak,0.0)
        assertEquals(.8,third.candidate.mean,1e-10);assertEquals(3,third.candidate.count)
    }
    @Test fun thresholdAndQualityResetConfirmation() {
        val detector=EventDetector(config);val w=window();val f=FeatureExtractor.extract(w,config)
        assertNull(detector.evaluate(w,f,.9));assertNull(detector.evaluate(w,f,.2))
        assertNull(detector.evaluate(w,f,.9));assertNull(detector.evaluate(w,f.copy(quality="INSUFFICIENT_SAMPLES"),.9))
        assertNull(detector.evaluate(w,f,.9))
    }
    @Test fun confirmationsDoNotSpanInterruptedWindows() {
        val detector=EventDetector(config)
        val first=window();val afterGap=window(500)
        assertNull(detector.evaluate(first,FeatureExtractor.extract(first,config),.9))
        assertNull(detector.evaluate(afterGap,FeatureExtractor.extract(afterGap,config),.9))
    }
    @Test fun cooldownPreventsSeparateNearbyEvent() {
        val detector=EventDetector(config.copy(confirmations=1))
        fun eval(offset: Int,score: Double): EventUpdate? { val w=window(offset);return detector.evaluate(w,FeatureExtractor.extract(w,config),score) }
        assertTrue(eval(0,.9)!!.newEvent);assertNull(eval(250,.1));assertNull(eval(350,.9))
        assertNull(eval(1500,.1));assertTrue(eval(1600,.9)!!.newEvent)
    }
    @Test(expected=IllegalArgumentException::class) fun modelRejectsInvalidInput() {
        StatisticalBaseline().score(FeatureExtractor.extract(window(),config).copy(rms=Double.NaN))
    }
    @Test(expected=IllegalArgumentException::class) fun modelRejectsInvalidScales() { CentroidModel("test",DoubleArray(7),DoubleArray(7),1.0) }
    @Test fun centroidNormalization() {
        val f=FeatureExtractor.extract(window(),config)
        val model=CentroidModel("test",f.vector(),DoubleArray(7){1.0},3.0)
        assertEquals(0.0,model.score(f),0.0)
    }
    @Test fun invalidSettingsAreRejected() {
        listOf(config.copy(hz=0),config.copy(overlap=1.0),config.copy(threshold=Double.NaN),config.copy(confirmations=0),config.copy(windowSeconds=20.0)).forEach {
            assertTrue(runCatching{it.validate()}.isFailure)
        }
    }
    @Test fun csvPreservesMissingValuesAndTimestampUnits() {
        val values=RecordingCsv.row(sample(1),"REAL_WORLD").split(",")
        assertEquals(18,values.size);assertEquals("20000000",values[0]);assertEquals("",values[11])
        assertEquals("REAL_WORLD",values.last());assertEquals("\"a,\"\"b\"",RecordingCsv.cell("a,\"b"))
    }
    @Test fun syntheticDemonstrationEmitsMergedCandidate() {
        val buffer=WindowBuffer(config);val detector=EventDetector(config);var count=0
        (0L..1000L).forEach { i -> buffer.add(SyntheticSource.sample(i,50,0,0))?.let { w ->
            val f=FeatureExtractor.extract(w,config)
            if(detector.evaluate(w,f,StatisticalBaseline().score(f))?.newEvent==true)count++
        } }
        assertEquals(1,count)
    }
}
