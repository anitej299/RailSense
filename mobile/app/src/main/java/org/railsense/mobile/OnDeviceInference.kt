package org.railsense.mobile

import android.content.Context
import org.json.JSONObject

object ModelLoader {
    fun load(context: Context,config: MonitorConfig,asset: String="centroid.json"): AnomalyModel {
        if(config.model=="baseline")return StatisticalBaseline()
        val text=context.assets.open(asset).bufferedReader().use{it.readText()}
        val json=JSONObject(text)
        require(json.getString("schema")=="railsense-centroid-v1") { "Unsupported model schema" }
        require(json.getString("preprocessing")=="window-dc-magnitude-v1") { "Preprocessing mismatch" }
        require(json.getInt("hz")==config.hz && json.getDouble("windowSeconds")==config.windowSeconds) { "Model requires matching sampling rate and window" }
        val order=json.getJSONArray("features")
        require((0 until order.length()).map{order.getString(it)}==FeatureExtractor.order) { "Feature order mismatch" }
        fun array(key: String)=json.getJSONArray(key).let { a -> DoubleArray(a.length()){a.getDouble(it)} }
        return CentroidModel(json.getString("version"),array("means"),array("scales"),json.getDouble("distanceScale"))
    }
}

