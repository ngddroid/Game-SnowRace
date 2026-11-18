package com.ngstudio.snowrace.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.snowrace.app.AppContainer
import com.ngstudio.snowrace.ui.theme.Palette
import com.ngstudio.snowrace.ui.theme.Type
import kotlin.math.sin

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No container") }

// ---------------------------------------------------------------------------------- text
@Composable
fun OutlinedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Type.Button,
    color: Color = Color.White,
    outline: Color = Palette.Navy,
    outlineWidth: Dp = 4.dp,
    align: TextAlign = TextAlign.Center,
    maxLines: Int = 1,
    brush: Brush? = null,
) {
    val px = with(LocalDensity.current) { outlineWidth.toPx() }
    Box(modifier, contentAlignment = Alignment.Center) {
        androidx.compose.material3.Text(
            text, style = style.copy(color = outline, drawStyle = Stroke(width = px, join = StrokeJoin.Round)),
            textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        )
        androidx.compose.material3.Text(
            text, style = if (brush != null) style.copy(brush = brush) else style.copy(color = color),
            textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, color: Color = Palette.Navy, style: TextStyle = Type.Body, align: TextAlign = TextAlign.Start, maxLines: Int = Int.MAX_VALUE) {
    androidx.compose.material3.Text(text, modifier = modifier, style = style.copy(color = color), textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
fun UiIcon(res: Int, size: Dp, modifier: Modifier = Modifier, alpha: Float = 1f) {
    Image(painterResource(res), contentDescription = null, modifier = modifier.size(size).alpha(alpha), contentScale = ContentScale.Fit)
}

// ---------------------------------------------------------------------------------- press
@Composable
fun Modifier.pressable(enabled: Boolean = true, sound: Boolean = true, onClick: () -> Unit): Modifier {
    val c = LocalContainer.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && enabled) 0.92f else 1f, spring(dampingRatio = 0.45f, stiffness = 900f), label = "press")
    return this
        .scale(s)
        .clickable(src, indication = null, enabled = enabled) { if (sound) c.tap(); onClick() }
}

// ---------------------------------------------------------------------------------- buttons
data class ButtonColors(val top: Color, val bottom: Color, val depth: Color, val text: Color = Color.White)

object Buttons {
    val Green = ButtonColors(Color(0xFF7CF06A), Color(0xFF2FC25B), Color(0xFF178A3D))
    val Mint = ButtonColors(Color(0xFF7DFBDC), Palette.Mint, Palette.MintDeep)
    val Berry = ButtonColors(Color(0xFFFF86B3), Palette.Berry, Palette.BerryDeep)
    val Sky = ButtonColors(Color(0xFF9BDDFF), Palette.Sky, Palette.SkyDeep)
    val Gold = ButtonColors(Color(0xFFFFE27A), Palette.Gold, Palette.GoldDeep)
    val Coral = ButtonColors(Color(0xFFFFA27A), Palette.Coral, Palette.CoralDeep)
    val Grey = ButtonColors(Color(0xFFD7E1EE), Color(0xFFAAB8CC), Color(0xFF7C8AA0))
    val Purple = ButtonColors(Color(0xFFD4A6FF), Palette.Purple, Color(0xFF7D3FC4))
}

@Composable
fun GameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ButtonColors = Buttons.Green,
    icon: Int? = null,
    enabled: Boolean = true,
    height: Dp = 60.dp,
    textStyle: TextStyle = Type.Button,
    shine: Boolean = false,
    sub: String? = null,
) {
    val c = LocalContainer.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed && enabled) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 1200f), label = "btn")
    val col = if (enabled) colors else Buttons.Grey
    val depth = 6.dp
    val shineX = if (shine) {
        val inf = rememberInfiniteTransition(label = "shine")
        inf.animateFloat(-0.4f, 1.6f, infiniteRepeatable(tween(2200, delayMillis = 900, easing = LinearEasing)), label = "sx").value
    } else -1f
    Box(
        modifier
            .height(height + depth)
            .graphicsLayer { scaleX = 1f - press * 0.04f; scaleY = 1f - press * 0.04f }
            .clickable(src, indication = null, enabled = enabled) { c.tap(); onClick() },
    ) {
        val d = with(LocalDensity.current) { depth.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            val r = CornerRadius(size.height * 0.3f)
            val faceH = size.height - d
            val off = press * (d - 1.5f)
            // drop shadow
            drawRoundRect(Color(0x55000000), topLeft = Offset(0f, d * 0.6f + 3f), size = Size(size.width, faceH), cornerRadius = r)
            // depth
            drawRoundRect(Palette.Navy, topLeft = Offset(0f, d), size = Size(size.width, faceH), cornerRadius = r)
            drawRoundRect(col.depth, topLeft = Offset(3f, d), size = Size(size.width - 6f, faceH - 3f), cornerRadius = r)
            // face
            drawRoundRect(Palette.Navy, topLeft = Offset(0f, off), size = Size(size.width, faceH), cornerRadius = r)
            drawRoundRect(Brush.verticalGradient(listOf(col.top, col.bottom), startY = off, endY = off + faceH), topLeft = Offset(3f, off + 3f), size = Size(size.width - 6f, faceH - 6f), cornerRadius = r)
            // gloss
            drawRoundRect(Color(0x55FFFFFF), topLeft = Offset(size.width * 0.06f, off + faceH * 0.1f), size = Size(size.width * 0.88f, faceH * 0.28f), cornerRadius = CornerRadius(faceH * 0.15f))
            if (shineX > -0.5f) {
                val x = size.width * shineX
                val p = Path().apply {
                    moveTo(x - faceH * 0.3f, off + 3f); lineTo(x + faceH * 0.1f, off + 3f)
                    lineTo(x - faceH * 0.2f, off + faceH - 3f); lineTo(x - faceH * 0.6f, off + faceH - 3f); close()
                }
                drawPath(p, Color(0x66FFFFFF))
            }
        }
        Row(
            Modifier.fillMaxWidth().height(height).offset(y = depth * press).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) { UiIcon(icon, height * 0.62f); Spacer(Modifier.width(8.dp)) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedText(text, style = textStyle, color = col.text, outlineWidth = 4.dp)
                if (sub != null) OutlinedText(sub, style = Type.Small, outlineWidth = 3.dp)
            }
        }
    }
}

@Composable
fun RoundIconButton(icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 52.dp, badge: Int = 0, label: String? = null, bg: Color = Color(0x33FFFFFF)) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(bg).border(2.dp, Color(0x55FFFFFF), CircleShape))
            UiIcon(icon, size * 0.82f)
            if (badge > 0) Badge(badge, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp))
        }
        if (label != null) OutlinedText(label, style = Type.Small, outlineWidth = 3.dp, modifier = Modifier.offset(y = (-4).dp))
    }
}

@Composable
fun Badge(count: Int, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "badge")
    val s by inf.animateFloat(1f, 1.15f, infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bs")
    Box(
        modifier.scale(s).size(22.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(Color(0xFFFF3B5C)).border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) { BodyText(if (count > 9) "9+" else "$count", color = Color.White, style = Type.Small.copy(fontSize = 11.sp), align = TextAlign.Center) }
}

// ---------------------------------------------------------------------------------- panels
@Composable
fun Panel(modifier: Modifier = Modifier, radius: Dp = 26.dp, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .shadow(14.dp, shape, ambientColor = Color(0xFF0B0A30), spotColor = Color(0xFF0B0A30))
            .clip(shape)
            .background(Palette.panelGradient)
            .border(3.dp, Palette.Navy, shape)
            .drawBehind {
                drawRoundRect(Color(0x66BFD9F2), topLeft = Offset(6f, size.height - 14f), size = Size(size.width - 12f, 8f), cornerRadius = CornerRadius(4f))
            }
            .padding(padding),
        content = content,
    )
}

@Composable
fun GlassPanel(modifier: Modifier = Modifier, radius: Dp = 22.dp, padding: Dp = 12.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier.clip(shape).background(Color(0x40141235)).border(2.dp, Color(0x40FFFFFF), shape).padding(padding),
        content = content,
    )
}

@Composable
fun Ribbon(text: String, modifier: Modifier = Modifier, color: Color = Palette.Berry, dark: Color = Palette.BerryDeep) {
    Box(modifier.height(52.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            val tail = h * 0.55f
            // tails
            val lt = Path().apply { moveTo(0f, h * 0.28f); lineTo(tail, h * 0.28f); lineTo(tail, h); lineTo(0f, h); lineTo(tail * 0.45f, h * 0.64f); close() }
            val rt = Path().apply { moveTo(w, h * 0.28f); lineTo(w - tail, h * 0.28f); lineTo(w - tail, h); lineTo(w, h); lineTo(w - tail * 0.45f, h * 0.64f); close() }
            drawPath(lt, dark); drawPath(rt, dark)
            drawPath(lt, Palette.Navy, style = Stroke(5f)); drawPath(rt, Palette.Navy, style = Stroke(5f))
            val body = Path().apply {
                moveTo(tail * 0.55f, 0f); lineTo(w - tail * 0.55f, 0f); lineTo(w - tail * 0.55f, h * 0.8f); lineTo(tail * 0.55f, h * 0.8f); close()
            }
            drawPath(body, Brush.verticalGradient(listOf(color.copy(alpha = 1f), dark)))
            drawPath(body, Palette.Navy, style = Stroke(5f))
            drawRect(Color(0x44FFFFFF), topLeft = Offset(tail * 0.55f + 6f, 6f), size = Size(w - tail * 1.1f - 12f, h * 0.16f))
        }
        OutlinedText(text, style = Type.Heading, modifier = Modifier.offset(y = (-5).dp).padding(horizontal = 40.dp))
    }
}

@Composable
fun CurrencyPill(icon: Int, value: Int, modifier: Modifier = Modifier, onPlus: (() -> Unit)? = null) {
    val shown by animateIntAsState(value, tween(700, easing = FastOutSlowInEasing), label = "cur")
    Box(modifier.height(40.dp), contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier.padding(start = 16.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x99141235))
                .border(2.dp, Color(0x55FFFFFF), RoundedCornerShape(16.dp)).padding(start = 26.dp, end = if (onPlus != null) 4.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedText(formatNum(shown), style = Type.Label.copy(fontSize = 16.sp), outlineWidth = 3.dp, modifier = Modifier.widthIn(min = 30.dp))
            if (onPlus != null) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(24.dp).clip(CircleShape).background(Palette.Mint).border(2.dp, Palette.Navy, CircleShape).pressable(onClick = onPlus), contentAlignment = Alignment.Center) {
                    OutlinedText("+", style = Type.Label, outlineWidth = 2.dp)
                }
            }
        }
        UiIcon(icon, 40.dp)
    }
}

fun formatNum(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
    n >= 100_000 -> "${n / 1000}K"
    else -> "%,d".format(n)
}

@Composable
fun GameProgressBar(progress: Float, modifier: Modifier = Modifier, fill: Brush = Brush.horizontalGradient(listOf(Palette.Mint, Palette.Sky)), height: Dp = 16.dp, track: Color = Color(0x55141235)) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(450), label = "pb")
    Box(modifier.height(height).clip(RoundedCornerShape(height / 2)).background(track).border(2.dp, Palette.Navy, RoundedCornerShape(height / 2))) {
        Box(Modifier.fillMaxWidth(p).height(height).clip(RoundedCornerShape(height / 2)).background(fill)) {
            Box(Modifier.fillMaxWidth().height(height * 0.35f).padding(horizontal = 4.dp).offset(y = 2.dp).clip(RoundedCornerShape(height)).background(Color(0x55FFFFFF)))
        }
    }
}

@Composable
fun GameToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val x by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "tg")
    val track = if (checked) Palette.Mint else Color(0xFFB9C6DA)
    Box(
        modifier.size(62.dp, 34.dp).clip(RoundedCornerShape(17.dp)).background(track).border(3.dp, Palette.Navy, RoundedCornerShape(17.dp)).pressable { onChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = 4.dp + 28.dp * x).size(26.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(Color.White).border(2.dp, Palette.Navy, CircleShape))
    }
}

// ---------------------------------------------------------------------------------- dialogs
@Composable
fun GameDialog(title: String, onDismiss: (() -> Unit)?, modifier: Modifier = Modifier, ribbon: Color = Palette.Berry, ribbonDark: Color = Palette.BerryDeep, content: @Composable ColumnScope.() -> Unit) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f)) }
    val c = LocalContainer.current
    LaunchedEffect(Unit) { c.audio.play(com.ngstudio.snowrace.audio.Sfx.UI_OPEN) }
    Box(
        Modifier.fillMaxSize().background(Palette.Scrim.copy(alpha = 0.7f * anim.value.coerceIn(0f, 1f)))
            .clickable(remember { MutableInteractionSource() }, indication = null) { onDismiss?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier.padding(horizontal = 22.dp).widthIn(max = 420.dp)
                .graphicsLayer { scaleX = 0.6f + 0.4f * anim.value; scaleY = 0.6f + 0.4f * anim.value; alpha = anim.value.coerceIn(0f, 1f) }
                .clickable(remember { MutableInteractionSource() }, indication = null) { },
        ) {
            Panel(Modifier.padding(top = 26.dp).fillMaxWidth(), padding = 18.dp) {
                Spacer(Modifier.height(24.dp))
                content()
            }
            Ribbon(title, Modifier.align(Alignment.TopCenter).fillMaxWidth(0.86f), color = ribbon, dark = ribbonDark)
            if (onDismiss != null) {
                Box(Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = 14.dp).size(44.dp).pressable(onClick = onDismiss)) {
                    UiIcon(com.ngstudio.snowrace.R.drawable.ui_close, 44.dp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------- backgrounds
@Composable
fun SnowfallLayer(modifier: Modifier = Modifier, count: Int = 60, color: Color = Color.White) {
    val seeds = remember { List(count) { floatArrayOf(Math.random().toFloat(), Math.random().toFloat(), 0.4f + Math.random().toFloat() * 0.8f, Math.random().toFloat() * 6f) } }
    val frame = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { frame.longValue = it } }
    Canvas(modifier.fillMaxSize()) {
        val t = frame.longValue / 1e9f
        for (s in seeds) {
            val y = ((s[1] + t * 0.05f * s[2]) % 1f) * size.height
            val x = (s[0] * size.width + sin(t * 0.8f + s[3]) * 18f * s[2]) % size.width
            drawCircle(color.copy(alpha = 0.35f + 0.4f * (s[2] - 0.4f)), radius = 1.6f + s[2] * 2.6f, center = Offset(x, y))
        }
    }
}

@Composable
fun GameBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier.fillMaxSize().background(Palette.bgGradient)) {
        Canvas(Modifier.fillMaxSize()) {
            // soft aurora glow
            drawCircle(Brush.radialGradient(listOf(Color(0x403DF5C8), Color.Transparent), center = Offset(size.width * 0.15f, size.height * 0.12f), radius = size.width * 0.8f), radius = size.width * 0.8f, center = Offset(size.width * 0.15f, size.height * 0.12f))
            drawCircle(Brush.radialGradient(listOf(Color(0x33FF4D8D), Color.Transparent), center = Offset(size.width * 0.95f, size.height * 0.35f), radius = size.width * 0.7f), radius = size.width * 0.7f, center = Offset(size.width * 0.95f, size.height * 0.35f))
            drawCircle(Brush.radialGradient(listOf(Color(0x305EC8FF), Color.Transparent), center = Offset(size.width * 0.3f, size.height * 0.9f), radius = size.width * 0.9f), radius = size.width * 0.9f, center = Offset(size.width * 0.3f, size.height * 0.9f))
        }
        SnowfallLayer(count = 45)
        content()
    }
}

// ---------------------------------------------------------------------------------- studio credit
/** "PRESENTED BY" caption over the NG Studio logo (splash and settings), same as the other NG Studio games. */
@Composable
fun StudioCredit(modifier: Modifier = Modifier, logoWidth: Dp = 108.dp, inline: Boolean = false) {
    val logo = @Composable {
        Image(
            painterResource(com.ngstudio.snowrace.R.drawable.ng_studio_logo),
            contentDescription = "NG Studio",
            modifier = Modifier.size(logoWidth, logoWidth * 2 / 3),
            contentScale = ContentScale.Fit,
        )
    }
    if (inline) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            OutlinedText("PRESENTED BY", style = Type.Small.copy(fontSize = 11.sp), outlineWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
            logo()
        }
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedText("PRESENTED BY", style = Type.Small.copy(fontSize = 11.sp), outlineWidth = 2.dp)
            logo()
        }
    }
}
