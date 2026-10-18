package org.railsense.mobile.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import org.railsense.mobile.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

fun decimal(value: Double?,digits: Int=2)=value?.takeIf{it.isFinite()}?.let{String.format(Locale.getDefault(),"%.${digits}f",it)}?:"—"
fun timeLabel(ms: Long,pattern: String="HH:mm:ss")=DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(ms))
fun durationLabel(seconds: Long): String { val s=seconds.coerceAtLeast(0);return "%02d:%02d:%02d".format(s/3600,(s%3600)/60,s%60) }
fun signalBand(score: Double,threshold: Double)=if(score>=threshold) "High signal" else "Elevated signal"

@Composable fun ScreenColumn(content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(top=8.dp,bottom=28.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
}
@Composable fun DashboardCard(modifier: Modifier=Modifier,accent: Color?=null,content: @Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=RailColors.Card,
        border=BorderStroke(1.dp,accent?.copy(alpha=.4f)?:RailColors.Border)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}
@Composable fun StatusBadge(text: String,color: Color=RailColors.Subtext,icon: ImageVector=Icons.Outlined.Circle) {
    Surface(shape=RoundedCornerShape(50),color=color.copy(alpha=.09f),border=BorderStroke(1.dp,color.copy(alpha=.25f))) {
        Row(Modifier.padding(horizontal=10.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Icon(icon,null,Modifier.size(14.dp),tint=color)
            Text(text,style=MaterialTheme.typography.labelMedium,color=color)
        }
    }
}
@Composable fun SectionHeader(title: String,action: String?=null,onAction: ()->Unit={}) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
        if(action!=null)TextButton(onClick=onAction){Text(action,color=RailColors.Cyan)}
    }
}
data class Metric(val label: String,val value: String,val unit: String="",val detail: String="",val icon: ImageVector=Icons.Outlined.Insights,val color: Color=RailColors.Cyan)
@Composable fun MetricCard(metric: Metric,modifier: Modifier=Modifier) {
    DashboardCard(modifier) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Icon(metric.icon,null,Modifier.size(18.dp),tint=metric.color)
            Text(metric.label,style=MaterialTheme.typography.bodySmall,color=RailColors.Subtext)
        }
        Text(metric.value,style=MaterialTheme.typography.headlineMedium,color=RailColors.Text)
        if(metric.unit.isNotEmpty())Text(metric.unit,style=MaterialTheme.typography.bodySmall,color=metric.color)
        if(metric.detail.isNotEmpty())Text(metric.detail,style=MaterialTheme.typography.bodySmall,color=RailColors.Subtext)
    }
}
@Composable fun MetricGrid(metrics: List<Metric>) {
    val fontScale=LocalDensity.current.fontScale
    BoxWithConstraints {
        val columns=if(maxWidth>=300.dp && fontScale<1.35f)2 else 1
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            metrics.chunked(columns).forEach { row -> Row(Modifier.height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                row.forEach{MetricCard(it,Modifier.weight(1f).fillMaxHeight())}
                if(row.size<columns)Spacer(Modifier.weight(1f))
            } }
        }
    }
}
@Composable fun PrimaryActionButton(text: String,onClick: ()->Unit,icon: ImageVector=Icons.Outlined.PlayArrow,enabled: Boolean=true,danger: Boolean=false) {
    // Darker tonal stops retain contrast for white button labels.
    val color=if(danger)Color(0xFFB92B43) else Color(0xFF1769CE)
    Button(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)
        .background(Brush.horizontalGradient(listOf(color,if(danger)Color(0xFF9E2440) else Color(0xFF1455B5))),RoundedCornerShape(14.dp)),
        shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=Color.Transparent,disabledContainerColor=RailColors.Elevated),contentPadding=PaddingValues(16.dp)) {
        Icon(icon,null,Modifier.size(22.dp));Spacer(Modifier.width(10.dp));Text(text,style=MaterialTheme.typography.labelLarge)
    }
}
@Composable fun EmptyState(title: String,body: String,icon: ImageVector=Icons.Outlined.Inbox,action: String?=null,onAction: ()->Unit={}) {
    DashboardCard { Icon(icon,null,Modifier.size(30.dp),tint=RailColors.Cyan);Text(title,style=MaterialTheme.typography.titleMedium)
        Text(body,color=RailColors.Subtext,style=MaterialTheme.typography.bodyMedium)
        if(action!=null)OutlinedButton(onClick=onAction,modifier=Modifier.heightIn(min=48.dp)){Text(action)} }
}
@Composable fun LoadingState(label: String="Loading saved data…") {
    Row(Modifier.fillMaxWidth().padding(24.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp);Text(label,color=RailColors.Subtext)
    }
}
@Composable fun DetailRow(label: String,value: String,color: Color=RailColors.Text) {
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(label,style=MaterialTheme.typography.bodySmall,color=RailColors.Subtext)
        Text(value,style=MaterialTheme.typography.bodyMedium,color=color)
    }
}
@Composable fun Notice(text: String,warning: Boolean=false) {
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Top) {
        Icon(if(warning)Icons.Outlined.Info else Icons.Outlined.Shield,null,Modifier.size(18.dp),tint=if(warning)RailColors.Amber else RailColors.Subtext)
        Text(text,color=RailColors.Subtext,style=MaterialTheme.typography.bodySmall)
    }
}
@Composable fun SensorStatusCard(name: String,available: Boolean,reading: Vec3?,unit: String,running: Boolean) {
    DashboardCard {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            StatusBadge(if(!available)"Unavailable" else if(reading!=null && running)"Live" else "Idle",
                if(reading!=null && running)RailColors.Green else RailColors.Subtext)
        }
        val axes=listOf("X" to reading?.x,"Y" to reading?.y,"Z" to reading?.z)
        val scale=LocalDensity.current.fontScale
        if(scale<1.35)Row(Modifier.fillMaxWidth()) { axes.forEach { (axis,v) -> Column(Modifier.weight(1f)) {
            Text(axis,color=RailColors.Subtext,style=MaterialTheme.typography.labelMedium)
            Text(decimal(v,2),style=MaterialTheme.typography.titleMedium,color=RailColors.Cyan)
        } } } else axes.forEach{(axis,v)->DetailRow(axis,decimal(v,2))}
        Text(unit,color=RailColors.Subtext,style=MaterialTheme.typography.bodySmall)
    }
}
@Composable fun AnomalyEventCard(event: EventEntity,highThreshold: Double,onOpen: ()->Unit) {
    val color=if(event.peak>=highThreshold)RailColors.Red else RailColors.Amber
    Surface(onClick=onOpen,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=RailColors.Card,border=BorderStroke(1.dp,RailColors.Border)) {
        Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.WarningAmber,null,tint=color,modifier=Modifier.size(24.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Text("Potential vibration anomaly",style=MaterialTheme.typography.titleMedium)
                Text(timeLabel(event.startMs,"dd MMM • HH:mm:ss"),style=MaterialTheme.typography.bodySmall,color=RailColors.Subtext)
                StatusBadge(signalBand(event.peak,highThreshold),color,Icons.Outlined.Insights)
                Text(if(event.acknowledged)"Acknowledged" else "Review event",color=RailColors.Cyan,style=MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Outlined.ChevronRight,null,tint=RailColors.Subtext)
        }
    }
}

/** Bounded Compose Canvas chart; no fabricated interpolation or per-sensor recomposition. */
@Composable fun LiveWaveformChart(values: List<Double>,unit: String="m/s²",times: List<Long>?=null,
    eventRange: LongRange?=null,highlightAll: Boolean=false,description: String="Acceleration waveform") {
    val valid=values.filter{it.isFinite()}
    val min=valid.minOrNull()?:0.0; val max=valid.maxOrNull()?:1.0
    val span=max(max-min,.2); val low=min-span*.1; val high=max+span*.1
    val density=LocalDensity.current
    val labelPaint=remember(density) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color=RailColors.Subtext.toArgb();textSize=12*density.density*density.fontScale
    } }
    Canvas(Modifier.fillMaxWidth().height(210.dp).semantics {
        contentDescription=if(values.isEmpty())"$description. No samples available." else "$description. ${values.size} samples. Minimum ${decimal(min)}, maximum ${decimal(max)} $unit. ${if(highlightAll)"Stored candidate interval." else ""}"
    }) {
        val left=52.dp.toPx();val right=size.width-12.dp.toPx();val top=16.dp.toPx();val bottom=size.height-36.dp.toPx()
        val width=(right-left).coerceAtLeast(1f);val height=bottom-top
        for(i in 0..4) {
            val y=top+height*i/4
            drawLine(RailColors.Border.copy(alpha=.65f),Offset(left,y),Offset(right,y),1f)
            drawContext.canvas.nativeCanvas.drawText(decimal(high-(high-low)*i/4,1),0f,y+4.dp.toPx(),labelPaint)
        }
        for(i in 0..4)drawLine(RailColors.Border.copy(alpha=.4f),Offset(left+width*i/4,top),Offset(left+width*i/4,bottom),1f)
        if(values.size>1) {
            val t=times?.takeIf{it.size==values.size && it.last()>it.first()}
            fun x(i: Int)=left+width*(if(t!=null)(t[i]-t.first()).toFloat()/(t.last()-t.first()) else i.toFloat()/(values.size-1))
            fun y(i: Int)=bottom-((values[i]-low)/(high-low)).toFloat()*height
            if(highlightAll)drawRect(RailColors.Red.copy(alpha=.08f),Offset(left,top),androidx.compose.ui.geometry.Size(width,height))
            val path=Path().apply{moveTo(x(0),y(0));for(i in 1 until values.size)lineTo(x(i),y(i))}
            val area=Path().apply { addPath(path);lineTo(right,bottom);lineTo(left,bottom);close() }
            drawPath(area,Brush.verticalGradient(listOf(RailColors.Cyan.copy(alpha=.14f),Color.Transparent),top,bottom))
            for(i in 1 until values.size) {
                val candidate=highlightAll || (t!=null && eventRange!=null && t[i] in eventRange)
                drawLine(if(candidate)RailColors.Red else RailColors.Cyan,Offset(x(i-1),y(i-1)),Offset(x(i),y(i)),2.dp.toPx(),StrokeCap.Round)
            }
            val start=if(t!=null)timeLabel(t.first()) else "Sample 1"
            val end=if(t!=null)timeLabel(t.last()) else "${values.size}"
            drawContext.canvas.nativeCanvas.drawText(start,left,size.height-9.dp.toPx(),labelPaint)
            drawContext.canvas.nativeCanvas.drawText(end,right-labelPaint.measureText(end),size.height-9.dp.toPx(),labelPaint)
        } else drawContext.canvas.nativeCanvas.drawText("Waiting for samples",left+8.dp.toPx(),top+height/2,labelPaint)
    }
    Text(unit,style=MaterialTheme.typography.bodySmall,color=RailColors.Subtext)
}
