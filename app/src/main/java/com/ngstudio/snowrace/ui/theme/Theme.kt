package com.ngstudio.snowrace.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ngstudio.snowrace.R

object Palette {
    val Night = Color(0xFF1E1B4B)
    val DeepIndigo = Color(0xFF2E2A7A)
    val Indigo = Color(0xFF3B37A0)
    val Sky = Color(0xFF5EC8FF)
    val SkyDeep = Color(0xFF2E8BE6)
    val Ice = Color(0xFFDFF6FF)
    val Mint = Color(0xFF3DF5C8)
    val MintDeep = Color(0xFF14B88F)
    val Berry = Color(0xFFFF4D8D)
    val BerryDeep = Color(0xFFD6276A)
    val Gold = Color(0xFFFFC83D)
    val GoldDeep = Color(0xFFE09A12)
    val Coral = Color(0xFFFF7A45)
    val CoralDeep = Color(0xFFE0521E)
    val Navy = Color(0xFF1B2A4A)
    val Purple = Color(0xFFB46CFF)
    val White = Color.White
    val Snow = Color(0xFFF4FAFF)
    val Panel = Color(0xFFF2F8FF)
    val PanelEdge = Color(0xFFBFD9F2)
    val Muted = Color(0xFF7C8DB5)
    val Scrim = Color(0xB3141235)

    val bgGradient = Brush.verticalGradient(listOf(Color(0xFF2B2F8F), Color(0xFF1E1B4B), Color(0xFF15133A)))
    val skyGradient = Brush.verticalGradient(listOf(Color(0xFF3E8EF0), Color(0xFF7FC4FF), Color(0xFFCFEFFF)))
    val panelGradient = Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE6F3FF)))
}

@OptIn(ExperimentalTextApi::class)
object Fonts {
    val Display = FontFamily(Font(R.font.lilita_one))
    val Body = FontFamily(
        Font(R.font.nunito, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.nunito, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.nunito, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
        Font(R.font.nunito, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
        Font(R.font.nunito, FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(900))),
    )
}

object Type {
    val Huge = TextStyle(fontFamily = Fonts.Display, fontSize = 64.sp, letterSpacing = 1.sp)
    val Title = TextStyle(fontFamily = Fonts.Display, fontSize = 30.sp, letterSpacing = 0.5.sp)
    val Heading = TextStyle(fontFamily = Fonts.Display, fontSize = 22.sp, letterSpacing = 0.4.sp)
    val Button = TextStyle(fontFamily = Fonts.Display, fontSize = 22.sp, letterSpacing = 0.6.sp)
    val Label = TextStyle(fontFamily = Fonts.Display, fontSize = 15.sp, letterSpacing = 0.4.sp)
    val Small = TextStyle(fontFamily = Fonts.Display, fontSize = 12.sp, letterSpacing = 0.3.sp)
    val Body = TextStyle(fontFamily = Fonts.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 21.sp)
    val BodySmall = TextStyle(fontFamily = Fonts.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)
}
