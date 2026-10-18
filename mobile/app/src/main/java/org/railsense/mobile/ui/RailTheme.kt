package org.railsense.mobile.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object RailColors {
    val Background=Color(0xFF020B16); val Secondary=Color(0xFF061526)
    val Card=Color(0xFF071B2D); val Elevated=Color(0xFF0A2238); val Border=Color(0xFF163A56)
    val Blue=Color(0xFF2588FF); val Cyan=Color(0xFF37D8FF); val Green=Color(0xFF00D5A0)
    val Amber=Color(0xFFFFB547); val Red=Color(0xFFFF4949)
    val Text=Color(0xFFF3F7FC); val Subtext=Color(0xFF91A8BE); val Muted=Color(0xFF647E98)
}
@Composable fun RailSenseTheme(content: @Composable ()->Unit) {
    val font=FontFamily.SansSerif
    MaterialTheme(colorScheme=darkColorScheme(
        primary=RailColors.Blue,onPrimary=RailColors.Text,primaryContainer=RailColors.Elevated,
        secondary=RailColors.Cyan,onSecondary=RailColors.Background,
        background=RailColors.Background,onBackground=RailColors.Text,surface=RailColors.Card,
        onSurface=RailColors.Text,surfaceVariant=RailColors.Elevated,onSurfaceVariant=RailColors.Subtext,
        outline=RailColors.Border,error=RailColors.Red),
        typography=Typography(
            headlineLarge=TextStyle(fontFamily=font,fontWeight=FontWeight.SemiBold,fontSize=28.sp,lineHeight=34.sp),
            headlineMedium=TextStyle(fontFamily=font,fontWeight=FontWeight.SemiBold,fontSize=25.sp,lineHeight=32.sp),
            titleLarge=TextStyle(fontFamily=font,fontWeight=FontWeight.SemiBold,fontSize=20.sp,lineHeight=27.sp),
            titleMedium=TextStyle(fontFamily=font,fontWeight=FontWeight.SemiBold,fontSize=17.sp,lineHeight=24.sp),
            bodyLarge=TextStyle(fontFamily=font,fontSize=16.sp,lineHeight=24.sp),
            bodyMedium=TextStyle(fontFamily=font,fontSize=14.sp,lineHeight=21.sp),
            bodySmall=TextStyle(fontFamily=font,fontSize=13.sp,lineHeight=19.sp),
            labelLarge=TextStyle(fontFamily=font,fontWeight=FontWeight.SemiBold,fontSize=14.sp,lineHeight=20.sp),
            labelMedium=TextStyle(fontFamily=font,fontWeight=FontWeight.Medium,fontSize=12.sp,lineHeight=18.sp)
        ),content=content)
}
