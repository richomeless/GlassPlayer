package com.glass.player

import android.Manifest
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import android.os.Bundle
import android.util.Rational
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class Channel(val name: String, val url: String, val logo: String = "", val ref: String = "", val ua: String = "")

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("glass", Context.MODE_PRIVATE)
    // موضع المتابعة لكل فيديو (بالميلي ثانية)، آخر 300 فيديو
    private fun posMap(): LinkedHashMap<String, Long> {
        val m = LinkedHashMap<String, Long>()
        (p.getString("pos", "") ?: "").split("\n").forEach { l ->
            val i = l.lastIndexOf('\t')
            if (i > 0) l.substring(i + 1).toLongOrNull()?.let { m[l.substring(0, i)] = it }
        }
        return m
    }
    fun pos(url: String): Long = posMap()[url] ?: 0L
    fun savePos(url: String, ms: Long) {
        val m = posMap(); m.remove(url); if (ms > 0L) m[url] = ms
        while (m.size > 300) m.remove(m.keys.first())
        p.edit().putString("pos", m.entries.joinToString("\n") { "${it.key}\t${it.value}" }).apply()
    }
    // ترتيب خاص لكل قائمة: المفتاح "fav" للمفضلة أو رابط القائمة. 0 أبجدي، 1 الأكثر تشغيلاً
    fun sorts(): Map<String, Int> = (p.getString("sorts", "") ?: "").split("\n").filter { it.contains("\t") }
        .mapNotNull { l -> val i = l.lastIndexOf('\t'); l.substring(i + 1).toIntOrNull()?.let { m -> l.substring(0, i) to m } }.toMap()
    fun saveSorts(m: Map<String, Int>) = p.edit().putString("sorts", m.entries.joinToString("\n") { "${it.key}\t${it.value}" }).apply()
    fun counts(): Map<String, Int> = (p.getString("cnt", "") ?: "").split("\n").filter { it.contains("\t") }
        .mapNotNull { l -> val i = l.lastIndexOf('\t'); l.substring(i + 1).toIntOrNull()?.let { n -> l.substring(0, i) to n } }.toMap()
    fun bumpCount(url: String) {
        val m = counts().toMutableMap(); m[url] = (m[url] ?: 0) + 1
        p.edit().putString("cnt", m.entries.joinToString("\n") { "${it.key}\t${it.value}" }).apply()
    }
    // وضع الألوان: 0 حسب النظام، 1 فاتح، 2 داكن
    fun themeMode(): Int = p.getInt("tmode", 0)
    fun setThemeMode(m: Int) = p.edit().putInt("tmode", m).apply()
    // الثيم: 0 هوية التطبيق، 1 Material You، 2 أحادي اللون
    fun themeStyle(): Int = p.getInt("tstyle", 0)
    fun setThemeStyle(m: Int) = p.edit().putInt("tstyle", m).apply()
    // القنوات اللي المستخدم بدّل نسبة شاشتها (16:9 <-> 4:3): كل قناة تتذكر اختيارها لحالها
    fun arFlips(): Set<String> = (p.getString("arf", "") ?: "").split("\n").filter { it.isNotBlank() }.toSet()
    fun arFlip(url: String): Boolean = url in arFlips()
    fun setArFlip(url: String, b: Boolean) {
        val s = arFlips().toMutableSet()
        if (b) s.add(url) else s.remove(url)
        p.edit().putString("arf", s.joinToString("\n")).apply()
    }
    // الاهتزازات: الكل / الواجهة الرئيسية (البلاي ليست) / مشغل الفيديو
    fun hapAll(): Boolean = p.getBoolean("hap_all", true)
    fun hapList(): Boolean = p.getBoolean("hap_list", true)
    fun hapVideo(): Boolean = p.getBoolean("hap_video", true)
    fun hapScroll(): Boolean = p.getBoolean("hap_scroll", true)
    fun setHap(key: String, b: Boolean) = p.edit().putBoolean(key, b).apply()
    fun pipMode(): Boolean = p.getBoolean("pip", true)
    fun setPipMode(b: Boolean) = p.edit().putBoolean("pip", b).apply()
    // إيماءات المشغل الجانبية (تقديم/تأخير بالسحب + صوت + سطوع): خيار وحد يشغلها ويطفيها كلها
    fun swipeGestures(): Boolean = p.getBoolean("swipe", true)
    fun setSwipeGestures(b: Boolean) = p.edit().putBoolean("swipe", b).apply()
    fun favs(): List<Channel> = (p.getString("fav", "") ?: "").split("\n").filter { it.isNotBlank() }.map {
        val f = it.split("\t")
        Channel(f[0], f.getOrElse(1) { "" }, f.getOrElse(2) { "" }, f.getOrElse(3) { "" }, f.getOrElse(4) { "" })
    }
    fun saveFavs(l: List<Channel>) = p.edit().putString("fav",
        l.joinToString("\n") { "${it.name}\t${it.url}\t${it.logo}\t${it.ref}\t${it.ua}" }).apply()
}

class MainActivity : ComponentActivity() {
    private var future: ListenableFuture<MediaController>? = null
    private val controller = mutableStateOf<MediaController?>(null)
    private val inPip = mutableStateOf(false)
    private val openReq = mutableStateOf(0)
    private val themeMode = mutableIntStateOf(0)
    private val themeStyle = mutableIntStateOf(0)
    private val extUri = mutableStateOf<android.net.Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // فتح جديد للتطبيق (مو من إشعار التشغيل ولا من فيديو خارجي): ما نكمل أي فيديو كان شغال قبل الإغلاق
        val coldStart = savedInstanceState == null &&
            intent?.getBooleanExtra("open_player", false) != true && intent?.action != android.content.Intent.ACTION_VIEW
        Logs.install(applicationContext)
        initFonts(applicationContext)
        handleOpenIntent(intent)
        enableEdgeToEdge()
        // نطلب أعلى معدل تحديث للشاشة (120Hz) لتبقى الإيماءات سلسة
        if (Build.VERSION.SDK_INT >= 35) window.decorView.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val f = MediaController.Builder(this, token).buildAsync()
        f.addListener({
            val mc = f.get()
            if (coldStart && mc.mediaItemCount > 0) { mc.stop(); mc.clearMediaItems() }
            controller.value = mc
            // لو الشاشة انفتحت من جديد قبل ما يتصل المتحكم، onStart ما قدر يرجّع الفيديو: نرجّعه هنا
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) setVideo(true)
        }, MoreExecutors.directExecutor())
        future = f
        val themeStore = Store(applicationContext)
        themeMode.intValue = themeStore.themeMode()
        themeStyle.intValue = themeStore.themeStyle()
        setContent {
            // وضع الألوان (نظام/فاتح/داكن) والثيم (هوية التطبيق/Material You) من الإعدادات
            val darkTheme = when (themeMode.intValue) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
            val appCtx = LocalContext.current
            val dyn = if (themeStyle.intValue == 1) dynamicScheme(appCtx, darkTheme) else null
            val mono = themeStyle.intValue == 2
            val scheme = dyn ?: if (mono) monoScheme(darkTheme) else if (darkTheme) darkColorScheme(
                surface = Color(0xFF14161D), onSurface = Color(0xFFE6E8F0),
                surfaceContainer = Color(0xFF191B23), surfaceContainerHigh = Color(0xFF1D2029),
                background = Color(0xFF0A0B10), onBackground = Color(0xFFE6E8F0)
            ) else lightColorScheme(
                surface = Color(0xFFF7F8FE), onSurface = Color(0xFF12141C),
                surfaceContainer = Color(0xFFEDEFF9), surfaceContainerHigh = Color(0xFFE8EBF7),
                background = Color(0xFFF2F4FB), onBackground = Color(0xFF12141C)
            )
            val base = if (darkTheme) DarkPal else LightPal
            val pal = if (dyn != null) materialYouPal(dyn, base) else if (mono) monoPal(darkTheme) else base
            MaterialTheme(typography = appTypography(), colorScheme = scheme) {
                CompositionLocalProvider(LocalPal provides pal) {
                    App(controller.value, inPip.value, openReq.value, themeMode.intValue, themeStyle.intValue,
                        { m, st ->
                            themeMode.intValue = m; themeStyle.intValue = st
                            themeStore.setThemeMode(m); themeStore.setThemeStyle(st)
                        }, extUri.value, { extUri.value = null }) { enterPip() }
                }
            }
        }
    }

    // الضغط على إشعار التشغيل: نفتح الفيديو الشغال
    private fun handleOpenIntent(i: android.content.Intent?) {
        if (i?.getBooleanExtra("open_player", false) == true) {
            i.removeExtra("open_player")
            openReq.value = openReq.value + 1
        }
        // فتح فيديو من تطبيق ثاني (مدير الملفات، المعرض، متصفح...)
        if (i?.action == android.content.Intent.ACTION_VIEW && i.data != null) {
            extUri.value = i.data
            i.data = null
            i.action = null
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenIntent(intent)
    }

    private fun enterPip() {
        val p = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
        enterPictureInPictureMode(p)
    }

    private fun pipOn() = getSharedPreferences("glass", Context.MODE_PRIVATE).getBoolean("pip", true)

    private fun setVideo(on: Boolean) {
        controller.value?.let { c ->
            // تبديل مسار الفيديو يقطع الصوت لحظة ويظهر إطار مضغوط عند الرجوع، فما نلمسه إلا لو الفيديو رابط ويب (نوفّر النت)
            // الفيديوهات المحلية تكمل بدون عرض بالخلفية: السطح ينهدم لحاله والصوت ما ينقطع
            val web = c.currentMediaItem?.localConfiguration?.uri?.scheme?.startsWith("http") == true
            if (!on && !web) return
            if (c.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO) == !on) return
            c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !on).build()
        }
    }

    override fun onUserLeaveHint() {
        trace("onUserLeaveHint playing=${controller.value?.isPlaying} pip=${pipOn()}")
        if (controller.value?.isPlaying == true && pipOn()) enterPip()
    }

    override fun onStart() { super.onStart(); trace("activity onStart"); setVideo(true) }
    override fun onResume() { super.onResume(); trace("activity onResume") }
    override fun onPause() { super.onPause(); trace("activity onPause") }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); trace("window focus=$hasFocus") }

    // وضع الصوت فقط: نوقف الفيديو لما التطبيق يروح للخلفية عشان نوفر النت والبطارية
    override fun onStop() { super.onStop(); trace("activity onStop"); if (!pipOn()) setVideo(false) }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPip, newConfig)
        trace("pipChanged=$isInPip")
        inPip.value = isInPip
    }

    override fun onDestroy() {
        future?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}

// فرش الزجاج مشتركة بين كل العناصر عشان ما تنعاد صياغتها مع كل تمرير
private val GlassFill = Brush.linearGradient(listOf(Color.White.copy(0.20f), Color.White.copy(0.06f)))
private val GlassStroke = Brush.linearGradient(listOf(Color.White.copy(0.75f), Color.White.copy(0.08f), Color.White.copy(0.45f)))

// زجاج مضبب حقيقي: تمويه قوي مع رفع التشبع زي زجاج iOS (يشتغل من أندرويد 12)
@androidx.annotation.RequiresApi(31)
fun frostEffect(radius: Float): androidx.compose.ui.graphics.RenderEffect {
    val cm = android.graphics.ColorMatrix().apply { setSaturation(1.7f) }
    val sat = android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(cm))
    val blur = android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP)
    return android.graphics.RenderEffect.createChainEffect(sat, blur).asComposeRenderEffect()
}

// تمويه ناعم للإضاءة المحيطية: تمويه قوي، تشبع خفيف، وأطرافها تتلاشى
@androidx.annotation.RequiresApi(31)
fun ambientEffect(radius: Float): androidx.compose.ui.graphics.RenderEffect {
    val cm = android.graphics.ColorMatrix().apply { setSaturation(1.1f) }
    val sat = android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(cm))
    val blur = android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.DECAL)
    return android.graphics.RenderEffect.createChainEffect(sat, blur).asComposeRenderEffect()
}

// ألوان الواجهة: داكن وفاتح (يتبدلون تلقائياً حسب ثيم الجوال). المشغّل دايماً داكن.
class Pal(
    val dark: Boolean,
    val bg1: Color, val bg2: Color, val blob1: Color, val blob2: Color,
    val text: Color, val textDim: Color,
    val glassFill: Brush, val glassStroke: Brush,
    val chipHi: Color, val headerTint: Brush, val rim: Brush,
    val shim: Color, val dot: Color
)
val DarkPal = Pal(
    true, Color(0xFF040508), Color(0xFF0C0E18), Color(0x664B3AA8), Color(0x550A6C94),
    Color.White, Color.White.copy(0.65f), GlassFill, GlassStroke,
    Color.White.copy(0.22f),
    Brush.verticalGradient(listOf(Color.White.copy(0.20f), Color.White.copy(0.07f))),
    Brush.horizontalGradient(listOf(Color.White.copy(0.55f), Color.White.copy(0.12f), Color.White.copy(0.45f))),
    Color.White, Color.White
)
val LightPal = Pal(
    false, Color(0xFFF2F4FB), Color(0xFFE3E7F6), Color(0x558C7BFF), Color(0x4458B6E8),
    Color(0xFF12141C), Color(0xFF12141C).copy(0.6f),
    Brush.linearGradient(listOf(Color.White.copy(0.80f), Color.White.copy(0.42f))),
    Brush.linearGradient(listOf(Color.White.copy(0.95f), Color(0xFF8E98BD).copy(0.30f), Color.White.copy(0.75f))),
    Color(0xFF5B6CFF).copy(0.20f),
    Brush.verticalGradient(listOf(Color.White.copy(0.62f), Color.White.copy(0.36f))),
    Brush.horizontalGradient(listOf(Color(0xFF8E98BD).copy(0.55f), Color(0xFF8E98BD).copy(0.15f), Color(0xFF8E98BD).copy(0.45f))),
    Color(0xFF3F4BD6), Color(0xFF3F4BD6)
)
val LocalPal = staticCompositionLocalOf { DarkPal }

// ألوان Material You الديناميكية (أندرويد 12+) من خلفية الجوال، وتنرجع null على الأجهزة الأقدم
fun dynamicScheme(ctx: Context, dark: Boolean): ColorScheme? =
    if (Build.VERSION.SDK_INT >= 31) (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)) else null

// نبني ألوان الواجهة (الخلفية، البقع، النص، النقطة) من مخطط Material You، ونبقي زجاج التطبيق كما هو
fun materialYouPal(cs: ColorScheme, base: Pal) = Pal(
    base.dark, cs.surface, cs.surfaceContainer,
    cs.primary.copy(alpha = if (base.dark) 0.40f else 0.30f), cs.tertiary.copy(alpha = if (base.dark) 0.34f else 0.26f),
    cs.onSurface, cs.onSurface.copy(alpha = 0.65f), base.glassFill, base.glassStroke,
    cs.primary.copy(alpha = 0.22f), base.headerTint, base.rim,
    if (base.dark) base.shim else cs.primary, cs.primary
)

// ثيم أحادي اللون: أبيض وأسود ورمادي فقط، بدون أي لون. زجاج التطبيق نفسه بس بدرجات رمادية
fun monoPal(dark: Boolean): Pal = if (dark) Pal(
    true, Color(0xFF000000), Color(0xFF0E0E0E), Color(0x30FFFFFF), Color(0x1EFFFFFF),
    Color(0xFFF2F2F2), Color(0xFFF2F2F2).copy(0.62f), GlassFill, GlassStroke,
    Color.White.copy(0.20f), DarkPal.headerTint, DarkPal.rim,
    Color.White, Color.White
) else Pal(
    false, Color(0xFFF5F5F5), Color(0xFFE6E6E6), Color(0x26000000), Color(0x18000000),
    Color(0xFF111111), Color(0xFF111111).copy(0.6f),
    Brush.linearGradient(listOf(Color.White.copy(0.80f), Color.White.copy(0.42f))),
    Brush.linearGradient(listOf(Color.White.copy(0.95f), Color(0xFF8C8C8C).copy(0.30f), Color.White.copy(0.75f))),
    Color(0xFF000000).copy(0.12f),
    LightPal.headerTint,
    Brush.horizontalGradient(listOf(Color(0xFF8C8C8C).copy(0.55f), Color(0xFF8C8C8C).copy(0.15f), Color(0xFF8C8C8C).copy(0.45f))),
    Color(0xFF222222), Color(0xFF222222)
)

// مخطط Material للثيم الأحادي: كل الألوان رمادية (حتى الأزرار والمفاتيح والقوائم ما يطلع فيها لون)
fun monoScheme(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = Color(0xFFEDEDED), onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFF2A2A2A), onPrimaryContainer = Color(0xFFEDEDED),
    secondary = Color(0xFFBDBDBD), onSecondary = Color(0xFF111111),
    secondaryContainer = Color(0xFF2E2E2E), onSecondaryContainer = Color(0xFFEDEDED),
    tertiary = Color(0xFF9E9E9E), onTertiary = Color(0xFF111111),
    tertiaryContainer = Color(0xFF333333), onTertiaryContainer = Color(0xFFEDEDED),
    surface = Color(0xFF111111), onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF2A2A2A), onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceTint = Color(0xFFEDEDED),
    surfaceContainer = Color(0xFF181818), surfaceContainerHigh = Color(0xFF1F1F1F),
    background = Color(0xFF000000), onBackground = Color(0xFFEDEDED),
    outline = Color(0xFF8A8A8A), outlineVariant = Color(0xFF444444)
) else lightColorScheme(
    primary = Color(0xFF1A1A1A), onPrimary = Color.White,
    primaryContainer = Color(0xFFDEDEDE), onPrimaryContainer = Color(0xFF111111),
    secondary = Color(0xFF555555), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E0E0), onSecondaryContainer = Color(0xFF111111),
    tertiary = Color(0xFF777777), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6E6E6), onTertiaryContainer = Color(0xFF111111),
    surface = Color(0xFFF5F5F5), onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFE0E0E0), onSurfaceVariant = Color(0xFF555555),
    surfaceTint = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFFECECEC), surfaceContainerHigh = Color(0xFFE6E6E6),
    background = Color(0xFFF2F2F2), onBackground = Color(0xFF111111),
    outline = Color(0xFF777777), outlineVariant = Color(0xFFCCCCCC)
)

// صف خيار في الإعدادات
@Composable
fun SettingChoice(t: String, sel: Boolean, enabled: Boolean, modifier: Modifier, fontSize: androidx.compose.ui.unit.TextUnit = 14.sp, onClick: () -> Unit) {
    val pal = LocalPal.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.height(50.dp).glass(16.dp)
            .then(if (sel) Modifier.background(pal.chipHi).border(1.5.dp, pal.dot.copy(0.9f), shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(t, color = pal.text.copy(alpha = if (enabled) 1f else 0.4f), fontSize = fontSize,
            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.Center)
    }
}

// صف مفتاح فرعي داخل إعدادات الاهتزازات
@Composable
fun HapSubRow(t: String, on: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    val dim by animateFloatAsState(if (enabled) 1f else 0.4f, tween(220), label = "hapDim")
    Row(modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t, color = pal.text.copy(alpha = dim), fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = onChange, enabled = enabled)
    }
}

// الاهتزازات: مفتاح عام للكل، ولما تفتح السهم يظهر مفتاحين: الواجهة الرئيسية (البلاي ليست) ومشغل الفيديو
@Composable
fun HapticsSetting(all: Boolean, list: Boolean, video: Boolean, scroll: Boolean,
                   onAll: (Boolean) -> Unit, onList: (Boolean) -> Unit, onVideo: (Boolean) -> Unit, onScroll: (Boolean) -> Unit,
                   open: Boolean, onOpen: (Boolean) -> Unit) {
    val pal = LocalPal.current
    val rot by animateFloatAsState(if (open) 90f else 0f, spring(dampingRatio = 0.55f, stiffness = 320f), label = "hapRot")
    Column(Modifier.fillMaxWidth().glass(20.dp)) {
        Row(Modifier.fillMaxWidth().height(68.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).fillMaxHeight().clickable { onOpen(!open) }.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("الاهتزازات", color = pal.text, fontSize = 16.sp)
                    Text(if (all) "مفعّلة" else "متوقفة", color = pal.textDim, fontSize = 12.sp)
                }
                Icon(IChev, contentDescription = null, tint = pal.textDim,
                    modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = 180f - rot })   // RTL: مسكّر = يشاور لليسار، مفتوح = لتحت
            }
            Box(Modifier.width(1.dp).height(34.dp).background(pal.glassStroke))
            Box(Modifier.padding(horizontal = 16.dp)) { Switch(checked = all, onCheckedChange = onAll) }
        }
        // الفتح: البطاقة تنمو بنعومة وتدفع اللي تحتها، والمفاتيح تنزل واحد ورا الثاني (تأخير 70ms بينها) مع تلاشي وانزلاق خفيف.
        // الإغلاق: تتلاشى وتنكمش بسرعة أكبر.
        androidx.compose.animation.AnimatedVisibility(
            visible = open,
            enter = androidx.compose.animation.expandVertically(tween(420, easing = FastOutSlowInEasing)) +
                androidx.compose.animation.fadeIn(tween(260)),
            exit = androidx.compose.animation.shrinkVertically(tween(300, easing = FastOutSlowInEasing)) +
                androidx.compose.animation.fadeOut(tween(160))
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 16.dp, bottom = 6.dp)) {
                listOf(
                    Triple("اهتزازات مشغل الفيديو", video, onVideo),
                    Triple("اهتزازات تصفح الفيديوهات", scroll, onScroll),
                    Triple("اهتزازات التنقل بين المجلدات", list, onList)
                ).forEachIndexed { i, (t, v, cb) ->
                    HapSubRow(t, v, all, cb, Modifier.animateEnterExit(
                        enter = androidx.compose.animation.fadeIn(tween(300, delayMillis = 70 * i)) +
                            androidx.compose.animation.slideInVertically(tween(380, delayMillis = 70 * i, easing = FastOutSlowInEasing)) { -it / 2 },
                        exit = androidx.compose.animation.fadeOut(tween(120))
                    ))
                }
            }
        }
    }
}

// صف إجراء في الإعدادات (إضافة رابط، سحب السجل)
@Composable
fun SettingAction(t: String, onClick: () -> Unit) {
    val pal = LocalPal.current
    Box(Modifier.fillMaxWidth().height(56.dp).glass(20.dp).clickable(onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart) {
        Text(t, color = pal.text, fontSize = 16.sp)
    }
}

// لقطة حالة الوضع القديم لتأثير الدائرة الكاشفة
class RevealSnap(val pal: Pal, val scheme: ColorScheme, val mode: Int, val style: Int, val kind: Int)

// جسم صفحة الإعدادات (الخلفية + المحتوى). يُرسم مرتين أثناء تبديل الوضع بنفس الكود تماماً:
// الجديد تحت، والقديم فوقه يُمسح بدائرة حوافها مموهة. لأنهم نفس التصميم بالضبط ما في أي إزاحة بينهم.
@Composable
fun SettingsBody(mode: Int, style: Int, onMode: (Int) -> Unit, onStyle: (Int) -> Unit,
                 hapAll: Boolean, hapList: Boolean, hapVideo: Boolean, hapScroll: Boolean,
                 onHapAll: (Boolean) -> Unit, onHapList: (Boolean) -> Unit, onHapVideo: (Boolean) -> Unit, onHapScroll: (Boolean) -> Unit,
                 hapOpen: Boolean, onHapOpen: (Boolean) -> Unit,
                 onAddLink: () -> Unit, onOpenFile: () -> Unit, onLogs: () -> Unit, onClose: () -> Unit,
                 scroll: ScrollState, view: View) {
    val pal = LocalPal.current
    val myOk = Build.VERSION.SDK_INT >= 31
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(pal.bg1, pal.bg2)))
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(pal.blob1, Color.Transparent), Offset(size.width * 0.1f, size.height * 0.05f), size.width * 0.9f))
                drawRect(Brush.radialGradient(listOf(pal.blob2, Color.Transparent), Offset(size.width * 0.95f, size.height * 0.95f), size.width * 0.9f))
            }
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp).verticalScroll(scroll)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).glass(14.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(IBack, contentDescription = null, tint = pal.text, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("الإعدادات", color = pal.text, fontSize = 24.sp, fontFamily = TitleFont,
                    fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp)
            }
            // الصفحة عربية: كل شي تحت الهيدر يترتب من اليمين لليسار (الكتابة على اليمين، والمفاتيح والأسهم على اليسار)
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(28.dp))
                    Text("وضع الألوان", color = pal.textDim, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("حسب النظام", "فاتح", "داكن").forEachIndexed { i, t ->
                            SettingChoice(t, mode == i, true, Modifier.weight(1f)) { if (mode != i) { buzz(view, false); onMode(i) } }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Text("الثيم", color = pal.textDim, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingChoice("هوية التطبيق", style == 0, true, Modifier.weight(1f), 13.sp) { if (style != 0) { buzz(view, false); onStyle(0) } }
                        SettingChoice("Material You ✨", style == 1, myOk, Modifier.weight(1f), 13.sp) { if (style != 1) { buzz(view, false); onStyle(1) } }
                        SettingChoice("أحادي اللون", style == 2, true, Modifier.weight(1f), 13.sp) { if (style != 2) { buzz(view, false); onStyle(2) } }
                    }
                    if (!myOk) Text("Material You يحتاج أندرويد 12 أو أحدث", color = pal.textDim, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 10.dp))
                    else if (style == 2) Text("درجات الأبيض والأسود والرمادي فقط، بدون أي لون", color = pal.textDim, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 10.dp))
                    else Text("ألوان الواجهة تتبع خلفية الجوال", color = pal.textDim, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 10.dp))
                    Spacer(Modifier.height(28.dp))
                    HapticsSetting(hapAll, hapList, hapVideo, hapScroll, onHapAll, onHapList, onHapVideo, onHapScroll, hapOpen, onHapOpen)
                    Spacer(Modifier.height(28.dp))
                    SettingAction("فتح ملف فيديو", onOpenFile)
                    Spacer(Modifier.height(10.dp))
                    SettingAction("فتح رابط فيديو", onAddLink)
                    Spacer(Modifier.height(10.dp))
                    SettingAction("سحب السجل (Logs)", onLogs)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// صفحة الإعدادات: طبقة فوق التطبيق داخل نفس النافذة (مو نافذة منفصلة)، فشريط الإشعارات وأيقونات الواي فاي
// تتبع نفس إعدادات التطبيق الرئيسي، والخلفية تمتد تحت الشريط بلون واحد.
@Composable
fun SettingsDialog(mode: Int, style: Int, onMode: (Int) -> Unit, onStyle: (Int) -> Unit,
                   hapAll: Boolean, hapList: Boolean, hapVideo: Boolean, hapScroll: Boolean,
                   onHapAll: (Boolean) -> Unit, onHapList: (Boolean) -> Unit, onHapVideo: (Boolean) -> Unit, onHapScroll: (Boolean) -> Unit,
                   onAddLink: () -> Unit, onOpenFile: () -> Unit, onLogs: () -> Unit, onClose: () -> Unit) {
    val pal = LocalPal.current
    val scheme = MaterialTheme.colorScheme
    val view = androidx.compose.ui.platform.LocalView.current
    val scroll = rememberScrollState()
    var hapOpen by remember { mutableStateOf(false) }
    var snap by remember { mutableStateOf<RevealSnap?>(null) }
    var revealId by remember { mutableIntStateOf(0) }
    var prog by remember { mutableFloatStateOf(0f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var lastDown by remember { mutableStateOf(Offset.Zero) }
    val fade = remember { Animatable(0f) }
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(160)) }
    LaunchedEffect(revealId) {
        if (revealId > 0) {
            // ننتظر فريمين عشان إعادة رسم الثيم الجديد (التقيلة) تخلص قبل ما تبدأ الحركة
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.animation.core.animate(0f, 1f,
                animationSpec = tween(if (snap?.kind == 1) 1000 else 850,
                    easing = androidx.compose.animation.core.CubicBezierEasing(if (snap?.kind == 1) 0.4f else 0.22f, 0f, if (snap?.kind == 1) 0.2f else 0.1f, 1f))) { v, _ -> prog = v }
            snap = null
        }
    }
    // نحفظ حالة الوضع القديم، نبدّل الوضع، ونكشف الجديد بدائرة تنتشر من مكان الإصبع
    fun revealThen(kind: Int, apply: () -> Unit) {
        prog = 0f; origin = lastDown
        snap = RevealSnap(pal, scheme, mode, style, kind)
        revealId++
        apply()
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val d = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    lastDown = d.position
                }
            }
    ) {
        SettingsBody(mode, style, { m -> revealThen(0) { onMode(m) } }, { st -> revealThen(1) { onStyle(st) } },
            hapAll, hapList, hapVideo, hapScroll, onHapAll, onHapList, onHapVideo, onHapScroll,
                    hapOpen, { hapOpen = it }, onAddLink, onOpenFile, onLogs, onClose, scroll, view)
        val sn = snap
        if (sn != null) Box(
            Modifier.fillMaxSize()
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val w = size.width; val h = size.height
                    if (sn.kind == 0) {
                        // تبديل فاتح/داكن: دائرة حوافها مموهة تنتشر من مكان الإصبع
                        val feather = 130.dp.toPx()
                        val dx = maxOf(origin.x, w - origin.x); val dy = maxOf(origin.y, h - origin.y)
                        val maxR = kotlin.math.sqrt(dx * dx + dy * dy)
                        val rad = (maxR + feather) * prog
                        if (rad > 1f) {
                            val inner = ((rad - feather) / rad).coerceIn(0f, 1f)
                            drawRect(
                                Brush.radialGradient(
                                    0f to Color.Black, inner to Color.Black, 1f to Color.Transparent,
                                    center = origin, radius = rad
                                ),
                                blendMode = androidx.compose.ui.graphics.BlendMode.DstOut
                            )
                        }
                    } else {
                        // تبديل الثيم: موجة تنسدل من أعلى الشاشة لأسفلها، وخلفها يظهر الثيم الجديد.
                        // حافتها متموجة شوي (أعمدة رفيعة كل واحد له إزاحة)، وعليها وهج بلون الثيم الجديد
                        val feather = 240.dp.toPx()
                        val pr = prog.coerceIn(0f, 1f)
                        val t = (h + feather) * pr
                        val amp = 26.dp.toPx() * kotlin.math.sin(Math.PI.toFloat() * pr)   // التموج يختفي بالبداية والنهاية
                        val glowA = kotlin.math.sin(Math.PI.toFloat() * pr)
                        val hw = feather * 0.38f
                        val n = 40
                        for (i in 0 until n) {
                            val x0 = kotlin.math.floor(w * i / n)
                            val x1 = if (i == n - 1) w else kotlin.math.floor(w * (i + 1) / n)
                            val off = amp * kotlin.math.sin(i * 0.55f + pr * 7f)
                            drawRect(
                                Brush.verticalGradient(
                                    0f to Color.Black, 1f to Color.Transparent,
                                    startY = t - feather + off, endY = t + off
                                ),
                                topLeft = Offset(x0, 0f), size = Size(x1 - x0, h),
                                blendMode = androidx.compose.ui.graphics.BlendMode.DstOut
                            )
                            if (glowA > 0.01f) {
                                val mid = t - feather * 0.5f + off
                                drawRect(
                                    Brush.verticalGradient(
                                        0f to Color.Transparent, 0.5f to pal.dot.copy(alpha = 0.38f * glowA), 1f to Color.Transparent,
                                        startY = mid - hw, endY = mid + hw
                                    ),
                                    topLeft = Offset(x0, 0f), size = Size(x1 - x0, h)
                                )
                            }
                        }
                    }
                }
                // أثناء الحركة (أقل من ثانية) نتجاهل اللمس عشان ما يتداخل مع النسخة القديمة
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
        ) {
            MaterialTheme(typography = appTypography(), colorScheme = sn.scheme) {
                CompositionLocalProvider(LocalPal provides sn.pal) {
                    SettingsBody(sn.mode, sn.style, {}, {},
                        hapAll, hapList, hapVideo, hapScroll, {}, {}, {}, {},
                        hapOpen, {}, {}, {}, {}, {}, scroll, view)
                }
            }
        }
    }
}

fun Modifier.glass(r: Dp = 24.dp, clipContent: Boolean = true): Modifier = composed {
    val pal = LocalPal.current
    val shape = RoundedCornerShape(r)
    (if (clipContent) Modifier.clip(shape) else Modifier).background(pal.glassFill, shape).border(1.dp, pal.glassStroke, shape)
}

// هزة خفيفة: نجرب هزة النظام أول، وإذا ما اشتغلت نجرب الاهتزاز المباشر
// إعدادات الاهتزاز العامة: inPlayer = مشغل الفيديو مفتوح، وإلا نحن في الواجهة الرئيسية
object Hap {
    @Volatile var all = true
    @Volatile var list = true
    @Volatile var video = true
    @Volatile var scroll = true
    @Volatile var inPlayer = false
    fun allowed(): Boolean = all && (if (inPlayer) video else list)
    fun scrollAllowed(): Boolean = all && scroll
}

// نقرة ناعمة لتأثير برايل: PRIMITIVE_LOW_TICK بقوة متوسطة-منخفضة، أو نبضة قصيرة بسعة ضعيفة، مع تصنيفها كلمس عشان النظام ما يكتمها
private var softVib: android.os.Vibrator? = null
private var softPrim = false
fun scrollTick(v: View) {
    if (!Hap.scrollAllowed()) return
    var done = false
    try {
        var vib = softVib
        if (vib == null) {
            val ctx = v.context
            vib = if (Build.VERSION.SDK_INT >= 31) {
                ctx.getSystemService(android.os.VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }
            softVib = vib
            softPrim = Build.VERSION.SDK_INT >= 30 &&
                vib.areAllPrimitivesSupported(android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
        }
        if (vib != null && vib.hasVibrator()) {
            val effect = if (softPrim) {
                android.os.VibrationEffect.startComposition()
                    .addPrimitive(android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.6f)
                    .compose()
            } else {
                android.os.VibrationEffect.createOneShot(5L, 60)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                vib.vibrate(effect, android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_TOUCH))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(effect, android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            }
            done = true
        }
    } catch (_: Throwable) {}
    // لو الاهتزاز المباشر فشل لأي سبب: هزة النظام الجاهزة كخطة بديلة
    if (!done) try {
        val c = when {
            Build.VERSION.SDK_INT >= 34 -> android.view.HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            Build.VERSION.SDK_INT >= 27 -> android.view.HapticFeedbackConstants.TEXT_HANDLE_MOVE
            else -> android.view.HapticFeedbackConstants.CLOCK_TICK
        }
        v.performHapticFeedback(c)
    } catch (_: Throwable) {}
}

// تأثير برايل: هزة صغيرة كل مسافة تمرير قصيرة في قائمة القنوات، نزولاً وصعوداً
@Composable
fun ScrollTicks(state: LazyGridState, view: View) {
    val d = androidx.compose.ui.platform.LocalDensity.current
    val gapPx = with(d) { 14.dp.toPx() }
    // عند الوصول لأعلى القائمة أو أسفلها: نفس هزة تغيير البلاي ليست
    LaunchedEffect(state) {
        var prevBack = state.canScrollBackward
        var prevFwd = state.canScrollForward
        snapshotFlow { Pair(state.canScrollBackward, state.canScrollForward) }.collect { (back, fwd) ->
            val hitTop = prevBack && !back
            val hitBottom = prevFwd && !fwd
            prevBack = back; prevFwd = fwd
            if ((hitTop || hitBottom) && state.isScrollInProgress) buzz(view, false, scroll = true)
        }
    }
    // الهزات حسب سرعة التمرير: بطيء = هزات بطيئة، سريع = هزات سريعة (والفلينج يبطّأ تدريجياً مع نزول السرعة)
    LaunchedEffect(state) {
        val density = d.density
        val minPx = with(d) { 6.dp.toPx() }
        fun posNow(): Float {
            val items = state.layoutInfo.visibleItemsInfo
            val f = items.firstOrNull() ?: return Float.NaN
            val cols = items.count { it.offset.y == f.offset.y }.coerceAtLeast(1)
            return (f.index / cols) * (f.size.height + gapPx) - f.offset.y
        }
        snapshotFlow { state.isScrollInProgress }.collectLatest { scrolling ->
            if (!scrolling) return@collectLatest
            var lastPos = posNow()
            var lastMs = -1L
            var speed = 0f          // px/s بعد التنعيم
            var accum = 0f          // المسافة المقطوعة منذ آخر هزة
            var lastTickMs = 0L
            while (state.isScrollInProgress) {
                androidx.compose.runtime.withFrameNanos { ns ->
                    val t = ns / 1_000_000L
                    val pos = posNow()
                    if (pos.isNaN() || lastPos.isNaN()) { lastPos = pos; lastMs = t; return@withFrameNanos }
                    if (lastMs < 0L) { lastMs = t; lastPos = pos; return@withFrameNanos }
                    val dt = (t - lastMs).coerceAtLeast(1L)
                    val delta = kotlin.math.abs(pos - lastPos)
                    lastPos = pos; lastMs = t
                    speed = speed * 0.75f + (delta / dt * 1000f) * 0.25f
                    accum += delta
                    val sDp = speed / density
                    // الفاصل بين الهزات: 260ms عند التمرير البطيء جداً ... 30ms عند التمرير السريع
                    val slow = 60f; val fast = 2400f
                    val frac = if (sDp <= slow) 0f else if (sDp >= fast) 1f
                        else (kotlin.math.ln(sDp / slow) / kotlin.math.ln(fast / slow))
                    val interval = (260.0 * Math.pow(30.0 / 260.0, frac.toDouble())).toLong()
                    if (accum >= minPx && t - lastTickMs >= interval) {
                        lastTickMs = t; accum = 0f
                        scrollTick(view)
                    }
                }
            }
        }
    }
}

fun buzz(v: View, release: Boolean, scroll: Boolean = false) {
    if (!(if (scroll) Hap.scrollAllowed() else Hap.allowed())) return
    val ok = try {
        val c = if (release && Build.VERSION.SDK_INT >= 27) android.view.HapticFeedbackConstants.VIRTUAL_KEY_RELEASE
                else android.view.HapticFeedbackConstants.VIRTUAL_KEY
        v.performHapticFeedback(c)
    } catch (_: Throwable) { false }
    if (!ok) try {
        val ctx = v.context
        val vib: android.os.Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(android.os.VibratorManager::class.java).defaultVibrator
        } else {
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
        vib.vibrate(android.os.VibrationEffect.createOneShot(if (release) 10L else 16L, 150))
    } catch (_: Throwable) {}
}

// قوة الصدمة (0..1) حسب سرعة سحب الإصبعين: بطيء = صدمة خفيفة جداً، سريع جداً = صدمة قوية
// السرعة بوحدة dp/ث: أقل من ~150 تعتبر بطيئة، وأكثر من ~1400 تعتبر أقصى قوة
fun impactStrength(dpPerSec: Float): Float {
    val x = ((dpPerSec - 150f) / 1250f).coerceIn(0f, 1f)
    return 0.12f + 0.88f * x
}

// هزة الصدمة عند حد التقريب: s من 0 إلى 1.
// ضعيفة = نقرة خفيفة، قوية = ضربة THUD كاملة + ارتدادات متتالية تتلاشى (تتزامن مع ارتداد الفيديو)
fun impactBuzz(v: View, s: Float) {
    if (!Hap.allowed()) return
    val st = s.coerceIn(0f, 1f)
    try {
        val ctx = v.context
        val vib: android.os.Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(android.os.VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
        if (!vib.hasVibrator()) { buzz(v, false); return }
        val after = when { st < 0.3f -> 0; st < 0.6f -> 1; st < 0.85f -> 2; else -> 3 }
        val effect: android.os.VibrationEffect =
            if (Build.VERSION.SDK_INT >= 30 && vib.areAllPrimitivesSupported(
                    android.os.VibrationEffect.Composition.PRIMITIVE_THUD,
                    android.os.VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                val c = android.os.VibrationEffect.startComposition()
                if (st < 0.3f) c.addPrimitive(android.os.VibrationEffect.Composition.PRIMITIVE_CLICK, 0.35f + st)
                else c.addPrimitive(android.os.VibrationEffect.Composition.PRIMITIVE_THUD, 0.5f + 0.5f * st)
                for (i in 1..after) {
                    c.addPrimitive(android.os.VibrationEffect.Composition.PRIMITIVE_CLICK,
                        (0.85f * st / (i + 0.6f)).coerceIn(0.2f, 1f), 50)
                }
                c.compose()
            } else {
                val n = after + 1
                val t = LongArray(n * 2); val a = IntArray(n * 2)
                for (i in 0 until n) {
                    t[i * 2] = if (i == 0) 0L else 40L; a[i * 2] = 0
                    t[i * 2 + 1] = (14f + 30f * st / (i + 1)).toLong()
                    a[i * 2 + 1] = (255f * (0.25f + 0.75f * st) / (i + 1)).toInt().coerceIn(1, 255)
                }
                android.os.VibrationEffect.createWaveform(t, a, -1)
            }
        if (Build.VERSION.SDK_INT >= 33) {
            vib.vibrate(effect, android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_TOUCH))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(effect, android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        }
    } catch (_: Throwable) { buzz(v, false) }
}

// زر حي: يصغر وهو مضغوط ويرتد، يتوهج عند اللمس، وإطاره يلمع بدوران بطيء جداً وسلس، مع هزة عند الضغط وعند الرفع
@Composable
fun Modifier.liveGlass(r: Dp, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val view = androidx.compose.ui.platform.LocalView.current
    val armed = remember { booleanArrayOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) { armed[0] = true; buzz(view, false) }
        else if (armed[0]) { armed[0] = false; buzz(view, true) }
    }
    val sc by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.4f, stiffness = 520f), label = "sc")
    val gl by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 90 else 420), label = "gl")
    val ang by rememberInfiniteTransition(label = "rot").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(36000, easing = LinearEasing)), label = "ang"
    )
    return this
        .graphicsLayer { scaleX = sc; scaleY = sc }
        .glass(r)
        .drawWithContent {
            drawContent()
            val cr = CornerRadius(r.toPx())
            // توهج عند اللمس
            if (gl > 0.01f) drawRoundRect(
                Brush.radialGradient(listOf(Color.White.copy(0.55f * gl), Color.White.copy(0.12f * gl)), center = center, radius = size.maxDimension * 0.7f),
                cornerRadius = cr
            )
            // إطار لامع يدور ببطء: رأس لامع وذيل طويل يتلاشى بنعومة
            val t = ang / 360f
            val n = 48
            val stops = Array(n + 1) { i ->
                val pos = i / n.toFloat()
                var d = pos - t
                if (d > 0.5f) d -= 1f
                if (d < -0.5f) d += 1f
                val u = if (d <= 0f) (1f + d / 0.30f).coerceIn(0f, 1f) else (1f - d / 0.08f).coerceIn(0f, 1f)
                val k = u * u * (3f - 2f * u)
                pos to Color(0.74f + 0.26f * k, 0.87f + 0.13f * k, 1f, k * k)
            }
            val w = 2.dp.toPx()
            drawRoundRect(Brush.sweepGradient(*stops, center = center), topLeft = Offset(w / 2f, w / 2f),
                size = Size(size.width - w, size.height - w), cornerRadius = cr,
                style = androidx.compose.ui.graphics.drawscope.Stroke(w), alpha = 0.8f + 0.2f * gl)
        }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

// خط التطبيق: Instrument Sans (رزين وهادئ). خط الشعار: Tektur (فضائي). الحروف العربية تنزل على خط النظام تلقائياً
// الخطوط: لو ملفات الخطوط موجودة في res/font نستخدمها (Instrument Sans للنص، Tektur للشعار)، وإلا خط النظام
var AppFont: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default
var TitleFont: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default
fun initFonts(ctx: Context) {
    try {
        fun id(n: String) = ctx.resources.getIdentifier(n, "font", ctx.packageName)
        val r = id("instrumentsans_regular"); val b = id("instrumentsans_bold"); val t = id("tektur_medium")
        if (r != 0 && b != 0) AppFont = androidx.compose.ui.text.font.FontFamily(
            androidx.compose.ui.text.font.Font(r, FontWeight.Normal),
            androidx.compose.ui.text.font.Font(r, FontWeight.Medium),
            androidx.compose.ui.text.font.Font(b, FontWeight.SemiBold),
            androidx.compose.ui.text.font.Font(b, FontWeight.Bold)
        )
        if (t != 0) TitleFont = androidx.compose.ui.text.font.FontFamily(
            androidx.compose.ui.text.font.Font(t, FontWeight.Medium)
        )
    } catch (_: Throwable) {}
}

fun appTypography(): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = AppFont), displayMedium = d.displayMedium.copy(fontFamily = AppFont),
        displaySmall = d.displaySmall.copy(fontFamily = AppFont), headlineLarge = d.headlineLarge.copy(fontFamily = AppFont),
        headlineMedium = d.headlineMedium.copy(fontFamily = AppFont), headlineSmall = d.headlineSmall.copy(fontFamily = AppFont),
        titleLarge = d.titleLarge.copy(fontFamily = AppFont), titleMedium = d.titleMedium.copy(fontFamily = AppFont),
        titleSmall = d.titleSmall.copy(fontFamily = AppFont), bodyLarge = d.bodyLarge.copy(fontFamily = AppFont),
        bodyMedium = d.bodyMedium.copy(fontFamily = AppFont), bodySmall = d.bodySmall.copy(fontFamily = AppFont),
        labelLarge = d.labelLarge.copy(fontFamily = AppFont), labelMedium = d.labelMedium.copy(fontFamily = AppFont),
        labelSmall = d.labelSmall.copy(fontFamily = AppFont)
    )
}

// لمعة الشعار: وميض يمر على الحروف، وبعده طرف النص يتوهج ثم ينطفئ، وبعدها مهلة قبل المرور التالي
@Composable
fun Modifier.logoShimmer(): Modifier {
    val sweep = remember { Animatable(0f) }
    val tip = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(4500)
            sweep.snapTo(0f); tip.snapTo(0f)
            sweep.animateTo(1f, tween(1600, easing = FastOutSlowInEasing))
            tip.animateTo(1f, tween(1700, easing = LinearEasing))
            sweep.snapTo(0f); tip.snapTo(0f)
        }
    }
    return this
        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val tw = size.width - 20.dp.toPx()   // عرض النص الفعلي (20dp مساحة للتوهج عند الطرف)
            val cy = size.height / 2f
            val lw = 1.6.dp.toPx()
            val v = sweep.value
            if (v > 0f && v < 1f) {
                val band = tw * 0.28f
                val cx = -band + (tw + 2f * band) * v
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White, Color.Transparent),
                        start = Offset(cx - band / 2f, 0f), end = Offset(cx + band / 2f, size.height * 0.35f)
                    ),
                    blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop
                )
                val a = kotlin.math.sin(v * Math.PI.toFloat()).coerceIn(0f, 1f)
                val c = Offset(cx.coerceIn(0f, tw), cy)
                val gr = 18.dp.toPx()
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(0.5f * a), Color.Transparent), center = c, radius = gr), radius = gr, center = c)
                drawLine(Color.White.copy(0.9f * a), Offset(c.x - 12.dp.toPx(), c.y), Offset(c.x + 12.dp.toPx(), c.y), lw, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(Color.White.copy(0.9f * a), Offset(c.x, c.y - 9.dp.toPx()), Offset(c.x, c.y + 9.dp.toPx()), lw, androidx.compose.ui.graphics.StrokeCap.Round)
            }
            val g = tip.value
            if (g > 0f && g < 1f) {
                // يتوهج بسرعة ثم ينطفئ ببطء
                val e = if (g < 0.18f) g / 0.18f else { val u = 1f - (g - 0.18f) / 0.82f; u * u }
                // توهج على الحروف الأخيرة نفسها
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(0.6f * e)), startX = tw * 0.55f, endX = tw),
                    blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop
                )
                // لمعة عند طرف آخر حرف
                val c = Offset(tw - 2.dp.toPx(), cy)
                val gr = 22.dp.toPx() * (0.55f + 0.45f * e)
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(0.65f * e), Color.Transparent), center = c, radius = gr), radius = gr, center = c)
                drawLine(Color.White.copy(0.95f * e), Offset(c.x - 16.dp.toPx() * e, c.y), Offset(c.x + 16.dp.toPx() * e, c.y), lw, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(Color.White.copy(0.95f * e), Offset(c.x, c.y - 12.dp.toPx() * e), Offset(c.x, c.y + 12.dp.toPx() * e), lw, androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
        .padding(end = 20.dp)
}

val IVolOff = mkIcon("M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z")
val IVolLow = mkIcon("M7 9v6h4l5 5V4l-5 5H7z")
val IVolMid = mkIcon("M18.5 12c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM5 9v6h4l5 5V4L9 9H5z")
val IVolHigh = mkIcon("M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z")

// إطار لامع يدور ببطء (نفس لمعة الأزرار الحية) بدون ضغط ولا تصغير، لعناصر مثل السلايدر
@Composable
fun Modifier.shimmerBorder(r: Dp): Modifier {
    val ang by rememberInfiniteTransition(label = "rotB").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(36000, easing = LinearEasing)), label = "angB"
    )
    return this.drawWithContent {
        drawContent()
        val t = ang / 360f
        val n = 48
        val stops = Array(n + 1) { i ->
            val pos = i / n.toFloat()
            var d = pos - t
            if (d > 0.5f) d -= 1f
            if (d < -0.5f) d += 1f
            val u = if (d <= 0f) (1f + d / 0.30f).coerceIn(0f, 1f) else (1f - d / 0.08f).coerceIn(0f, 1f)
            val k = u * u * (3f - 2f * u)
            pos to Color(0.74f + 0.26f * k, 0.87f + 0.13f * k, 1f, k * k)
        }
        val w = 2.dp.toPx()
        drawRoundRect(Brush.sweepGradient(*stops, center = center), topLeft = Offset(w / 2f, w / 2f),
            size = Size(size.width - w, size.height - w), cornerRadius = CornerRadius(r.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(w), alpha = 0.8f)
    }
}

// سلايدر صوت زجاجي: يتحكم بصوت مشغّل التطبيق، ويتذكر آخر مستوى
@Composable
fun GlassVolume(player: Player, onTouch: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("glass_volume", Context.MODE_PRIVATE) }
    var vol by remember(player) { mutableFloatStateOf(player.volume.coerceIn(0f, 1f)) }
    DisposableEffect(player) {
        val l = object : Player.Listener { override fun onVolumeChanged(volume: Float) { vol = volume } }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    fun set(v: Float) { val x = v.coerceIn(0f, 1f); vol = x; player.volume = x }
    fun save() { prefs.edit().putFloat("v", vol).apply() }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier.fillMaxWidth().height(52.dp).glass(18.dp).shimmerBorder(18.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (vol <= 0.001f) IVolOff else if (vol < 0.34f) IVolLow else if (vol < 0.67f) IVolMid else IVolHigh,
                contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp).clickable { onTouch(); set(if (vol > 0.001f) 0f else 1f); save() }
            )
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .pointerInput(Unit) {
                        val r = 10.dp.toPx()
                        fun frac(x: Float) = ((x - r) / (size.width - 2f * r).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        detectTapGestures { o -> onTouch(); set(frac(o.x)); save() }
                    }
                    .pointerInput(Unit) {
                        val r = 10.dp.toPx()
                        fun frac(x: Float) = ((x - r) / (size.width - 2f * r).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        detectHorizontalDragGestures(
                            onDragStart = { o -> onTouch(); set(frac(o.x)) },
                            onDragEnd = { save() }, onDragCancel = { save() }
                        ) { ch, _ -> ch.consume(); onTouch(); set(frac(ch.position.x)) }
                    }
            ) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val r = 10.dp.toPx()
                    val th = 6.dp.toPx()
                    val cy = size.height / 2f
                    val x = r + vol * (size.width - 2f * r)
                    drawRoundRect(Color.White.copy(0.20f), Offset(r, cy - th / 2f), Size(size.width - 2f * r, th), CornerRadius(th / 2f))
                    if (x > r) drawRoundRect(
                        Brush.horizontalGradient(listOf(Color(0xFFA9D2FF), Color.White), startX = r, endX = x.coerceAtLeast(r + 1f)),
                        Offset(r, cy - th / 2f), Size(x - r, th), CornerRadius(th / 2f)
                    )
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(0.55f), Color.Transparent), center = Offset(x, cy), radius = r * 2f), radius = r * 2f, center = Offset(x, cy))
                    drawCircle(Color.White, radius = r * 0.75f, center = Offset(x, cy))
                }
            }
            Spacer(Modifier.width(12.dp))
            Text("${(vol * 100).toInt()}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
        }
    }
}

@Composable
fun GlassButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(52.dp).liveGlass(18.dp, onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1) }
}

fun mkIcon(p: String): ImageVector = ImageVector.Builder(
    defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
).addPath(addPathNodes(p), fill = SolidColor(Color.White)).build()

val IPlay = mkIcon("M8 5v14l11-7z")
val IPause = mkIcon("M6 19h4V5H6v14zm8-14v14h4V5h-4z")
val IBack = mkIcon("M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z")
val IStarOn = mkIcon("M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z")
val IStarOff = mkIcon("M22 9.24l-7.19-.62L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21 12 17.27 18.18 21l-1.63-7.03L22 9.24zM12 15.4l-3.76 2.27 1-4.28-3.32-2.88 4.38-.38L12 6.1l1.71 4.04 4.38.38-3.32 2.88 1 4.28L12 15.4z")
val IFull = mkIcon("M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z")
val IFullExit = mkIcon("M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z")
val IPip = mkIcon("M19 11h-8v6h8v-6zm4 8V4.98C23 3.88 22.1 3 21 3H3c-1.1 0-2 .88-2 1.98V19c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2zm-2 .02H3V4.97h18v14.05z")
val ISort = mkIcon("M3 18h6v-2H3v2zM3 6v2h18V6H3zm0 7h12v-2H3v2z")
val IGear = mkIcon("M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z")
val IChev = mkIcon("M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z")
val IMore = mkIcon("M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z")
val IHead = mkIcon("M12 1c-4.97 0-9 4.03-9 9v7c0 1.66 1.34 3 3 3h3v-8H5v-2c0-3.87 3.13-7 7-7s7 3.13 7 7v2h-4v8h3c1.66 0 3-1.34 3-3v-7c0-4.97-4.03-9-9-9z")
val IRew = mkIcon("M11 18V6l-8.5 6 8.5 6zm.5-6l8.5 6V6l-8.5 6z")
val IFwd = mkIcon("M4 18l8.5-6L4 6v12zm9-12v12l8.5-6L13 6z")
val ISpeed = mkIcon("M20.38 8.57l-1.23 1.85a8 8 0 0 1-.22 7.58H5.07A8 8 0 0 1 15.58 6.85l1.85-1.23A10 10 0 0 0 3.35 19a2 2 0 0 0 1.72 1h13.85a2 2 0 0 0 1.74-1 10 10 0 0 0-.27-10.44zm-9.79 6.84a2 2 0 0 0 2.83 0l5.66-8.49-8.49 5.66a2 2 0 0 0 0 2.83z")
val ITouch = mkIcon("M9 11.24V7.5a2.5 2.5 0 0 1 5 0v3.74c1.21-.81 2-2.18 2-3.74C16 5.01 13.99 3 11.5 3S7 5.01 7 7.5c0 1.56.79 2.93 2 3.74zm9.84 4.63l-4.54-2.26c-.17-.07-.35-.11-.54-.11H13v-6c0-.83-.67-1.5-1.5-1.5S10 6.67 10 7.5v10.74l-3.43-.72c-.08-.01-.15-.03-.24-.03-.31 0-.59.13-.79.33l-.79.8 4.94 4.94c.27.27.65.44 1.06.44h6.79c.75 0 1.33-.55 1.44-1.28l.75-5.27c.01-.07.02-.14.02-.2 0-.62-.38-1.16-.91-1.38z")

fun fmtDur(ms: Long): String {
    val s = (ms.coerceAtLeast(0L) / 1000L)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun activityOf(c: Context): android.app.Activity? {
    var x: Context = c
    while (x is android.content.ContextWrapper) {
        if (x is android.app.Activity) return x
        x = x.baseContext
    }
    return null
}

fun fmtSpeed(s: Float): String = (if (s % 1f == 0f) s.toInt().toString() else s.toString()) + "x"

// سلايدر تقديم الفيديو: نفس زجاج وإطار سلايدر الصوت، يتحرك مع التشغيل، والسحب عليه يقدّم أو يرجّع الفيديو
@Composable
fun GlassSeek(player: Player, onTouch: () -> Unit, modifier: Modifier = Modifier) {
    var pos by remember(player) { mutableLongStateOf(player.currentPosition) }
    var dur by remember(player) { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(player) {
        while (true) {
            if (!dragging) {
                pos = player.currentPosition
                val d = player.duration
                dur = if (d == C.TIME_UNSET || d < 0L) 0L else d
            }
            delay(250)
        }
    }
    val frac = if (dragging) dragFrac else if (dur > 0L) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier.fillMaxWidth().height(52.dp).glass(18.dp).shimmerBorder(18.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(fmtDur(if (dragging) (dragFrac * dur).toLong() else pos), color = Color.White, fontSize = 12.sp,
                fontWeight = FontWeight.Medium, modifier = Modifier.width(46.dp))
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .pointerInput(dur) {
                        val r = 10.dp.toPx()
                        fun fr(x: Float) = ((x - r) / (size.width - 2f * r).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        detectTapGestures { o -> onTouch(); if (dur > 0L) { pos = (fr(o.x) * dur).toLong(); player.seekTo(pos) } }
                    }
                    .pointerInput(dur) {
                        val r = 10.dp.toPx()
                        fun fr(x: Float) = ((x - r) / (size.width - 2f * r).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        detectHorizontalDragGestures(
                            onDragStart = { o -> onTouch(); dragFrac = fr(o.x); dragging = true },
                            onDragEnd = { if (dur > 0L) { pos = (dragFrac * dur).toLong(); player.seekTo(pos) }; dragging = false },
                            onDragCancel = { dragging = false }
                        ) { ch, _ -> ch.consume(); onTouch(); dragFrac = fr(ch.position.x) }
                    }
            ) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val r = 10.dp.toPx()
                    val th = 6.dp.toPx()
                    val cy = size.height / 2f
                    val x = r + frac * (size.width - 2f * r)
                    drawRoundRect(Color.White.copy(0.20f), Offset(r, cy - th / 2f), Size(size.width - 2f * r, th), CornerRadius(th / 2f))
                    if (x > r) drawRoundRect(
                        Brush.horizontalGradient(listOf(Color(0xFFA9D2FF), Color.White), startX = r, endX = x.coerceAtLeast(r + 1f)),
                        Offset(r, cy - th / 2f), Size(x - r, th), CornerRadius(th / 2f)
                    )
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(0.55f), Color.Transparent), center = Offset(x, cy), radius = r * 2f), radius = r * 2f, center = Offset(x, cy))
                    drawCircle(Color.White, radius = r * 0.75f, center = Offset(x, cy))
                }
            }
            Text(fmtDur(dur), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.width(46.dp), textAlign = TextAlign.End)
        }
    }
}

// صورة مصغّرة للفيديو من مكتبة الجوال.
// الكاش محدود بالحجم الفعلي (24MB) مو بالعدد، والتحميل على خيطين بس عشان التمرير السريع ما يخنق المعالج،
// وفي تأخير بسيط قبل التحميل: لو البطاقة طلعت من الشاشة قبله ينلغي التحميل بالكامل
private val thumbCache = object : android.util.LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = (value.width * value.height * 4).coerceAtLeast(1)
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private val thumbIO = Dispatchers.IO.limitedParallelism(3)

@Composable
fun rememberThumb(uri: String): ImageBitmap? {
    val ctx = LocalContext.current
    var bmp by remember(uri) { mutableStateOf<ImageBitmap?>(thumbCache.get(uri)) }
    LaunchedEffect(uri) {
        if (bmp == null) {
            delay(90)
            val signal = android.os.CancellationSignal()
            val job = async(thumbIO) {
                try {
                    val raw = ctx.contentResolver.loadThumbnail(android.net.Uri.parse(uri), android.util.Size(720, 405), signal)
                    // نحوّلها لصورة على كرت الشاشة مرة وحدة: أخف على الذاكرة والرسم أسرع
                    val hw = try { raw.copy(android.graphics.Bitmap.Config.HARDWARE, false) } catch (_: Throwable) { null }
                    val img = if (hw != null) { raw.recycle(); hw } else raw
                    img.asImageBitmap().also { thumbCache.put(uri, it) }
                } catch (_: Throwable) { null }
            }
            try {
                bmp = job.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                signal.cancel(); job.cancel(); throw e
            }
        }
    }
    return bmp
}

// لقطة الفيديو كبيانات JPEG للإشعار (فيديوهات الجوال فقط؛ روابط الويب ما لها صورة)
fun loadArtwork(ctx: android.content.Context, uri: String): ByteArray? {
    if (!uri.startsWith("content://") && !uri.startsWith("file://")) return null
    val u = android.net.Uri.parse(uri)
    var bmp: android.graphics.Bitmap? = try {
        ctx.contentResolver.loadThumbnail(u, android.util.Size(720, 720), null)
    } catch (_: Throwable) { null }
    if (bmp == null) {
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, u)
            bmp = r.getFrameAtTime(1_000_000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Throwable) {} finally { try { r.release() } catch (_: Throwable) {} }
    }
    if (bmp == null) return null
    return try {
        // غلاف مربع: الصورة كاملة بالوسط وشريط بلون حافة الصورة (متدرّج للأغمق) من الجهتين، عشان لا ينقص منها شي ولا تنضغط
        if (bmp.config == android.graphics.Bitmap.Config.HARDWARE) bmp = bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
        val src = bmp!!
        val side = 720
        val sc = side.toFloat() / maxOf(src.width, src.height)
        val w = (src.width * sc).toInt().coerceAtLeast(1); val h = (src.height * sc).toInt().coerceAtLeast(1)
        val scaled = android.graphics.Bitmap.createScaledBitmap(src, w, h, true)
        val sq = android.graphics.Bitmap.createBitmap(side, side, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(sq)
        fun avg(x: Int, y: Int, cw: Int, ch: Int): Int =
            android.graphics.Bitmap.createScaledBitmap(android.graphics.Bitmap.createBitmap(src, x, y, cw.coerceAtLeast(1), ch.coerceAtLeast(1)), 1, 1, true).getPixel(0, 0)
        fun dark(c: Int, f: Float) = android.graphics.Color.rgb(
            (android.graphics.Color.red(c) * f).toInt(), (android.graphics.Color.green(c) * f).toInt(), (android.graphics.Color.blue(c) * f).toInt())
        val pt = android.graphics.Paint()
        if (h < side) {          // صورة عريضة: شريط فوق وتحت
            val y0 = (side - h) / 2
            val strip = (src.height / 8).coerceAtLeast(1)
            val top = avg(0, 0, src.width, strip); val bot = avg(0, src.height - strip, src.width, strip)
            pt.shader = android.graphics.LinearGradient(0f, 0f, 0f, y0.toFloat(), dark(top, 0.45f), top, android.graphics.Shader.TileMode.CLAMP)
            cv.drawRect(0f, 0f, side.toFloat(), y0.toFloat(), pt)
            pt.shader = android.graphics.LinearGradient(0f, (y0 + h).toFloat(), 0f, side.toFloat(), bot, dark(bot, 0.45f), android.graphics.Shader.TileMode.CLAMP)
            cv.drawRect(0f, (y0 + h).toFloat(), side.toFloat(), side.toFloat(), pt)
            cv.drawBitmap(scaled, 0f, y0.toFloat(), null)
        } else if (w < side) {   // صورة طولية: شريط يمين ويسار
            val x0 = (side - w) / 2
            val strip = (src.width / 8).coerceAtLeast(1)
            val l = avg(0, 0, strip, src.height); val r = avg(src.width - strip, 0, strip, src.height)
            pt.shader = android.graphics.LinearGradient(0f, 0f, x0.toFloat(), 0f, dark(l, 0.45f), l, android.graphics.Shader.TileMode.CLAMP)
            cv.drawRect(0f, 0f, x0.toFloat(), side.toFloat(), pt)
            pt.shader = android.graphics.LinearGradient((x0 + w).toFloat(), 0f, side.toFloat(), 0f, r, dark(r, 0.45f), android.graphics.Shader.TileMode.CLAMP)
            cv.drawRect((x0 + w).toFloat(), 0f, side.toFloat(), side.toFloat(), pt)
            cv.drawBitmap(scaled, x0.toFloat(), 0f, null)
        } else cv.drawBitmap(scaled, 0f, 0f, null)
        val out = java.io.ByteArrayOutputStream()
        sq.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
        out.toByteArray()
    } catch (_: Throwable) { null }
}

@Composable
fun GlassIcon(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = Color.White, onClick: () -> Unit) {
    Box(
        modifier.height(52.dp).liveGlass(18.dp, onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp)) }
}

// زر التشغيل/الإيقاف: زجاجي عادي بنفس أسلوب باقي الأزرار (بدون عدسة ولا شيدر)
@Composable
fun PlayButton(playing: Boolean, modifier: Modifier = Modifier, diameter: Dp = 88.dp, onClick: () -> Unit) {
    Box(
        modifier.size(diameter).liveGlass(diameter / 2, onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(if (playing) IPause else IPlay, contentDescription = null, tint = Color.White,
            modifier = Modifier.size(diameter * 0.46f))
    }
}

// أيقونة داخل قائمة الثلاث نقاط: مضيئة لو الخيار شغّال، وخافتة لو مطفي. ضغطة طويلة تظهر اسم الزر
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MenuIcon(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    var showLabel by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(showLabel) { if (showLabel) { delay(1400); showLabel = false } }
    Box(
        Modifier.size(46.dp)
            .background(if (on) Color.White.copy(0.16f) else Color.Transparent, CircleShape)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); showLabel = true }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = Color.White.copy(if (on) 1f else 0.45f), modifier = Modifier.size(24.dp))
        if (showLabel) androidx.compose.ui.window.Popup(
            alignment = Alignment.BottomCenter,
            offset = androidx.compose.ui.unit.IntOffset(0, with(androidx.compose.ui.platform.LocalDensity.current) { 40.dp.roundToPx() })
        ) {
            Text(label, color = Color.White, fontSize = 12.sp, maxLines = 1,
                modifier = Modifier.background(Color.Black.copy(0.8f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 5.dp))
        }
    }
}

// يوسّع العنصر لحافتي الشاشة (يتجاوز هامش الأب الأفقي) عشان ما ينقص طرفه عند الحواف
fun Modifier.bleed(extra: androidx.compose.ui.unit.Dp): Modifier = this.layout { m, c ->
    val e = extra.roundToPx()
    val w = c.maxWidth + 2 * e
    val p = m.measure(c.copy(minWidth = w, maxWidth = w))
    layout(c.maxWidth, p.height) { p.place(-e, 0) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Chip(text: String, sel: () -> Float, modifier: Modifier = Modifier, onLong: () -> Unit = {}, onClick: () -> Unit) {
    // نفس الارتفاع ونفس الشكل لكل الأزرار، والتحديد تظليل حي يتبع السحب (يُقرأ وقت الرسم بس)
    val pal = LocalPal.current
    Box(
        modifier.height(40.dp).glass(14.dp)
            .drawBehind {
                drawRoundRect(pal.chipHi.copy(alpha = pal.chipHi.alpha * sel().coerceIn(0f, 1f)), cornerRadius = CornerRadius(14.dp.toPx()))
            }
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = pal.text, maxLines = 1, fontSize = 14.sp) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tile(c: Channel, fav: Boolean, onLong: () -> Unit, onClick: (Rect) -> Unit) {
    // نحفظ مكان البطاقة بدون ما نسبب إعادة رسم، ونستخدمه وقت الضغط لانيميشن الفتح
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val thumb = rememberThumb(c.url)
    Column(
        Modifier.combinedClickable(
            onClick = { onClick(holder[0]?.let { if (it.isAttached) it.boundsInRoot() else Rect.Zero } ?: Rect.Zero) },
            onLongClick = onLong
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).onGloballyPositioned { holder[0] = it }.glass(22.dp, clipContent = false),
            contentAlignment = Alignment.Center) {
            if (thumb == null) Text(cleanName(c.name).take(1), color = LocalPal.current.text, fontSize = 28.sp)
            else Image(thumb, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().padding(1.dp).clip(RoundedCornerShape(21.dp)),
                filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium)
            // مدة الفيديو
            c.ref.toLongOrNull()?.takeIf { it > 0L }?.let { d ->
                Text(fmtDur(d), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(7.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp))
            }
            if (fav) Text("★", color = Color(0xFFFFD60A), fontSize = 16.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        // سطرين دايماً عشان كل الصفوف بنفس الارتفاع
        Text(cleanName(c.name), color = LocalPal.current.text.copy(0.9f), fontSize = 12.sp, maxLines = 2, minLines = 2,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

// نجمة رباعية الرؤوس بجوانب مقعّرة
private fun starPath(c: Offset, R: Float, r: Float): Path = Path().apply {
    val k = 0.12f
    moveTo(c.x, c.y - r)
    quadraticBezierTo(c.x + k * R, c.y - k * r, c.x + R, c.y)
    quadraticBezierTo(c.x + k * R, c.y + k * r, c.x, c.y + r)
    quadraticBezierTo(c.x - k * R, c.y + k * r, c.x - R, c.y)
    quadraticBezierTo(c.x - k * R, c.y - k * r, c.x, c.y - r)
    close()
}

// سحب السجل: يحفظ آخر انهيار ويجمع سجل التطبيق (logcat) عشان تنسخه أو تشاركه بدون adb
const val TG = "GPLAYER"
fun trace(m: String, e: Throwable? = null) { if (e != null) android.util.Log.i(TG, m, e) else android.util.Log.i(TG, m) }

object Logs {
    private const val CRASH = "last_crash.txt"

    fun install(ctx: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                java.io.File(ctx.filesDir, CRASH).writeText(
                    "${java.util.Date()}\nthread=${t.name}\n" + android.util.Log.getStackTraceString(e)
                )
            } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }

    fun collect(ctx: Context): String {
        val sb = StringBuilder()
        val ver = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Throwable) { "?" }
        sb.append("Glass Player $ver | ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        sb.append("Time: ${java.util.Date()}\n\n")
        try {
            val f = java.io.File(ctx.filesDir, CRASH)
            if (f.exists()) sb.append("=== آخر انهيار ===\n").append(f.readText()).append("\n\n")
        } catch (_: Throwable) {}
        try {
            val pr = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-s", "$TG:V").redirectErrorStream(true).start()
            sb.append("=== تتبع التطبيق (GPLAYER) ===\n").append(pr.inputStream.bufferedReader().readText()).append("\n")
            pr.destroy()
        } catch (_: Throwable) {}
        sb.append("=== logcat ===\n")
        try {
            val pr = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", "1500").redirectErrorStream(true).start()
            sb.append(pr.inputStream.bufferedReader().readText())
            pr.destroy()
        } catch (e: Throwable) { sb.append("تعذّر قراءة logcat: ${e.message}") }
        val t = sb.toString()
        return if (t.length > 90_000) "...(مقصوص)\n" + t.takeLast(90_000) else t
    }
}

// شكل القص: مستطيل يكبر من مكان بطاقة القناة لين يملأ الشاشة
class RevealShape(private val p: Float, private val o: Rect, private val corner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val l = mix(o.left, 0f, p); val t = mix(o.top, 0f, p)
        val r = mix(o.right, size.width, p); val b = mix(o.bottom, size.height, p)
        val rad = mix(corner, 0f, p)
        return Outline.Rounded(RoundRect(l, t, r, b, CornerRadius(rad, rad)))
    }
}

@Composable
fun Video(p: Player, modifier: Modifier = Modifier, onView: ((PlayerView) -> Unit)? = null) {
    DisposableEffect(Unit) {
        trace("Video composed")
        onDispose { trace("Video DISPOSED") }
    }
    VideoView(p, modifier, onView)
}

@Composable
fun VideoView(p: Player, modifier: Modifier = Modifier, onView: ((PlayerView) -> Unit)? = null) = AndroidView(
    // TextureView عشان الزوم والتدوير يشتغلون صح
    factory = { c -> (LayoutInflater.from(c).inflate(R.layout.player_view, null, false) as PlayerView).apply { player = p; post { onView?.invoke(this) } } },
    update = { it.player = p },
    modifier = modifier.fillMaxSize()
)

// إضاءة محيطية حول الفيديو: نأخذ إطار صغير جداً من الفيديو كل ~120ms، ننعّم ألوانه مع الإطار السابق،
// ونرسمه انعكاس مرآة مضغوط عند أطراف الفيديو (مقعر، ملتصق بالحافة ويتلاشى للخارج) زي iOS
@Composable
fun AmbientGlow(pv: PlayerView?, ar: Float, modifier: Modifier = Modifier) {
    val gw = 16; val gh = 9      // حجم الشبكة اللي نرسمها
    val sw = 80; val sh = 45     // حجم السحب من الفيديو (5x5 بكسل لكل خانة) لتجنب الوميض من أخذ عينات قليلة
    val bmp = remember { android.graphics.Bitmap.createBitmap(gw, gh, android.graphics.Bitmap.Config.ARGB_8888) }
    val tmp = remember { android.graphics.Bitmap.createBitmap(sw, sh, android.graphics.Bitmap.Config.ARGB_8888) }
    val img = remember { bmp.asImageBitmap() }
    var tick by remember { mutableStateOf(0) }
    val fx = remember { if (Build.VERSION.SDK_INT >= 31) ambientEffect(70f) else null }
    LaunchedEffect(pv) {
        if (pv == null) return@LaunchedEffect
        val target = FloatArray(gw * gh * 3)
        val cur = FloatArray(gw * gh * 3)
        val raw = IntArray(sw * sh)
        val px = IntArray(gw * gh)
        var have = false
        // 1) سحب الألوان من الفيديو ~7 مرات بالثانية (متوسط كل 5x5 بكسل)
        launch {
            val bw = sw / gw; val bh = sh / gh
            while (true) {
                val tv = pv.videoSurfaceView as? android.view.TextureView
                if (tv != null && tv.isAvailable && tv.width > 0 && tv.height > 0) {
                    val ok = try { tv.getBitmap(tmp); true } catch (_: Throwable) { false }
                    if (ok) {
                        tmp.getPixels(raw, 0, sw, 0, 0, sw, sh)
                        for (gy in 0 until gh) for (gx in 0 until gw) {
                            var r = 0f; var g = 0f; var b = 0f
                            for (yy in 0 until bh) for (xx in 0 until bw) {
                                val c = raw[(gy * bh + yy) * sw + gx * bw + xx]
                                r += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255
                            }
                            val n = (bw * bh).toFloat(); val o = (gy * gw + gx) * 3
                            target[o] = r / n; target[o + 1] = g / n; target[o + 2] = b / n
                        }
                        if (!have) { System.arraycopy(target, 0, cur, 0, cur.size); have = true }
                    }
                }
                delay(140)
            }
        }
        // 2) كل فريم (30fps) نقرّب الألوان الحالية من الهدف بنعومة، حتى تكون الحركة مايعة بدون قفزات
        var last = 0L
        while (true) {
            val t = androidx.compose.runtime.withFrameNanos { it }
            if (last == 0L) { last = t; continue }
            val dt = (t - last) / 1e9f
            if (dt < 0.033f || !have) continue
            last = t
            val k = 1f - kotlin.math.exp(-dt / 0.5f)   // ثابت زمني 0.5 ثانية
            for (i in 0 until gw * gh) {
                val o = i * 3
                cur[o] += (target[o] - cur[o]) * k
                cur[o + 1] += (target[o + 1] - cur[o + 1]) * k
                cur[o + 2] += (target[o + 2] - cur[o + 2]) * k
                // الخانات الداكنة تتلاشى (شفافية) بدل ما نرسمها: كذا ما تبين مربعات/تدرجات متقطعة في المشاهد الداكنة
                val l = 0.2126f * cur[o] + 0.7152f * cur[o + 1] + 0.0722f * cur[o + 2]
                val tt = ((l - 14f) / 34f).coerceIn(0f, 1f)
                val al = (tt * tt * (3f - 2f * tt) * 255f).toInt()
                px[i] = (al shl 24) or (cur[o].toInt() shl 16) or (cur[o + 1].toInt() shl 8) or cur[o + 2].toInt()
            }
            bmp.setPixels(px, 0, gw, 0, 0, gw, gh)
            tick++
        }
    }
    Box(modifier.fillMaxSize()
        .graphicsLayer { renderEffect = fx; compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawBehind {
            tick   // قراءة الحالة عشان يعاد الرسم مع كل إطار جديد
            val a = if (ar > 0f) ar else 16f / 9f
            val rw: Float; val rh: Float
            if (a > size.width / size.height) { rw = size.width; rh = size.width / a } else { rh = size.height; rw = size.height * a }
            val cx = size.width / 2f; val cy = size.height / 2f
            val x0 = cx - rw / 2f; val y0 = cy - rh / 2f
            val e = minOf(rw, rh) * 0.24f   // عمق الانعكاس خارج حافة الفيديو
            val xs = floatArrayOf(x0 - e, x0, x0 + rw, x0 + rw + e)
            val ys = floatArrayOf(y0 - e, y0, y0 + rh, y0 + rh + e)
            // 1) انعكاس مرآة لكل حافة مضغوط داخل شريط ضيق: الألوان تتجمّع عند الأطراف (مقعر) وتكمل من لون الحافة نفسها
            for (iy in 0..2) for (ix in 0..2) {
                val dx = xs[ix]; val dy = ys[iy]
                val dw = xs[ix + 1] - xs[ix]; val dh = ys[iy + 1] - ys[iy]
                val sx = if (ix == 1) 1f else -1f
                val sy = if (iy == 1) 1f else -1f
                scale(sx, sy, pivot = Offset(dx + dw / 2f, dy + dh / 2f)) {
                    drawImage(img,
                        dstOffset = androidx.compose.ui.unit.IntOffset(dx.toInt(), dy.toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(dw.toInt().coerceAtLeast(1), dh.toInt().coerceAtLeast(1)),
                        alpha = 0.95f, filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium)
                }
            }
            // 2) تلاشي مقعّر: قوي وملتصق بحافة الفيديو، ثم ينحني ويخف بسرعة للخارج (t=0 عند الحافة، 1 عند آخر الانعكاس)
            val st = listOf(0f to 0.95f, 0.12f to 0.74f, 0.35f to 0.32f, 0.65f to 0.08f, 1f to 0f)
            val outward = st.map { (t, al) -> t to Color.Black.copy(alpha = al) }.toTypedArray()
            val inward = st.reversed().map { (t, al) -> (1f - t) to Color.Black.copy(alpha = al) }.toTypedArray()
            val dm = androidx.compose.ui.graphics.BlendMode.DstIn
            val r = x0 + rw; val bt = y0 + rh
            drawRect(Brush.horizontalGradient(colorStops = inward, startX = x0 - e, endX = x0), Offset(x0 - e, y0), Size(e, rh), blendMode = dm)
            drawRect(Brush.horizontalGradient(colorStops = outward, startX = r, endX = r + e), Offset(r, y0), Size(e, rh), blendMode = dm)
            drawRect(Brush.verticalGradient(colorStops = inward, startY = y0 - e, endY = y0), Offset(x0, y0 - e), Size(rw, e), blendMode = dm)
            drawRect(Brush.verticalGradient(colorStops = outward, startY = bt, endY = bt + e), Offset(x0, bt), Size(rw, e), blendMode = dm)
            // الزوايا: تلاشي دائري من زاوية الفيديو، فتطلع الزاوية مقوّسة مثل الزجاج
            drawRect(Brush.radialGradient(colorStops = outward, center = Offset(x0, y0), radius = e), Offset(x0 - e, y0 - e), Size(e, e), blendMode = dm)
            drawRect(Brush.radialGradient(colorStops = outward, center = Offset(r, y0), radius = e), Offset(r, y0 - e), Size(e, e), blendMode = dm)
            drawRect(Brush.radialGradient(colorStops = outward, center = Offset(x0, bt), radius = e), Offset(x0 - e, bt), Size(e, e), blendMode = dm)
            drawRect(Brush.radialGradient(colorStops = outward, center = Offset(r, bt), radius = e), Offset(r, bt), Size(e, e), blendMode = dm)
        })
}

// ترتيب الفيديوهات: 0 الأحدث (ترتيب المكتبة)، 1 أبجدي، 2 الأكثر تشغيلاً
fun sortChannels(list: List<Channel>, mode: Int, counts: Map<String, Int>): List<Channel> = when (mode) {
    1 -> list.sortedBy { cleanName(it.name).lowercase() }
    2 -> list.sortedWith(compareByDescending<Channel> { counts[it.url] ?: 0 }.thenBy { cleanName(it.name).lowercase() })
    else -> list
}

fun cleanName(n: String): String = n.trim().ifEmpty { n }

fun videoPerm(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

fun hasVideoPerm(c: Context): Boolean =
    c.checkSelfPermission(videoPerm()) == android.content.pm.PackageManager.PERMISSION_GRANTED

// كل فيديوهات الجوال من المكتبة (MediaStore)، الأحدث أول. folder فاضي = كل المجلدات.
// ref = مدة الفيديو بالميلي ثانية (نخزنها بنفس الحقل عشان تنحفظ مع المفضلة)
fun loadVideos(ctx: Context, folder: String): List<Channel> {
    val out = ArrayList<Channel>()
    try {
        val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION)
        val sel = if (folder.isEmpty()) null else "${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} = ?"
        val args = if (folder.isEmpty()) null else arrayOf(folder)
        ctx.contentResolver.query(base, proj, sel, args, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val name = (c.getString(iName) ?: "فيديو").substringBeforeLast('.').replace("\t", " ")
                out += Channel(name, android.content.ContentUris.withAppendedId(base, id).toString(), "", c.getLong(iDur).toString())
            }
        }
    } catch (_: Throwable) {}
    return out
}

// أسماء المجلدات اللي فيها فيديوهات (للتبويبات)
fun loadFolders(ctx: Context): List<String> {
    val set = java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER)
    try {
        ctx.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Media.BUCKET_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) c.getString(0)?.takeIf { it.isNotBlank() }?.let { set.add(it) }
        }
    } catch (_: Throwable) {}
    return set.toList()
}

// اسم الفيديو لما يجي من منتقي الملفات أو من تطبيق ثاني
fun displayName(ctx: Context, u: android.net.Uri): String {
    try {
        ctx.contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.let { n -> return n.substringBeforeLast('.').replace("\t", " ") }
        }
    } catch (_: Throwable) {}
    return (u.lastPathSegment ?: "فيديو").substringAfterLast('/').substringBeforeLast('.').replace("\t", " ")
}

@Composable
fun App(player: Player?, inPip: Boolean, openReq: Int, themeMode: Int, themeStyle: Int, onTheme: (Int, Int) -> Unit,
        extUri: android.net.Uri?, onExtUri: () -> Unit, onPip: () -> Unit) {
    val ctx = LocalContext.current
    val act = ctx as Activity
    val store = remember { Store(ctx) }
    var folders by remember { mutableStateOf(listOf<String>()) }
    // التبويبات: "" = كل الفيديوهات، وبعدها مجلد مجلد
    val lists = remember(folders) { listOf("") + folders }
    var favs by remember { mutableStateOf(store.favs()) }
    var tab by remember { mutableStateOf(0) }
    var channels by remember { mutableStateOf(listOf<Channel>()) }
    var loading by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var cur by remember { mutableStateOf<Channel?>(null) }
    var hasPerm by remember { mutableStateOf(hasVideoPerm(ctx)) }
    var pendingOpen by remember { mutableStateOf<android.net.Uri?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPerm = it }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            try { ctx.contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
            pendingOpen = u
        }
    }
    LaunchedEffect(Unit) { if (!hasPerm) permLauncher.launch(videoPerm()) }
    var hapAll by remember { mutableStateOf(store.hapAll()) }
    var hapList by remember { mutableStateOf(store.hapList()) }
    var hapVideo by remember { mutableStateOf(store.hapVideo()) }
    var hapScroll by remember { mutableStateOf(store.hapScroll()) }
    SideEffect { Hap.all = hapAll; Hap.list = hapList; Hap.video = hapVideo; Hap.scroll = hapScroll; Hap.inPlayer = cur != null }
    var ui by remember { mutableStateOf(true) }
    var zoom by remember { mutableStateOf(1f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }
    var arForced by remember { mutableStateOf(false) }   // نسبة الشاشة: false = الأصلية، true = البديلة (16:9 <-> 4:3)
    val reveal = remember { Animatable(0f) }
    var origin by remember { mutableStateOf(Rect.Zero) }
    var closing by remember { mutableStateOf(false) }
    // fullP: 0 = بورتريه، 1 = شاشة كاملة (الفيديو يلف 90 درجة عكس عقارب الساعة)
    val fullP = remember { Animatable(0f) }
    var fpDrag by remember { mutableStateOf(-1f) }   // أثناء السحب بالإصبع: تقدم الشاشة الكاملة الحي
    fun fpNow() = if (fpDrag >= 0f) fpDrag else fullP.value
    // سحب للأسفل وهو بشاشة كاملة: الفيديو ينزل مع الإصبع، وعند الإفلات يلف ويصغر
    // الفيديو يلحق الإصبع (لأعلى أو لأسفل أو لليسار) وعند الإفلات يكمل التكبير أو التصغير
    var fsDrag by remember { mutableStateOf(-1f) }      // >=0 أثناء السحب
    var fsDx by remember { mutableStateOf(0f) }
    var fsDy by remember { mutableStateOf(0f) }
    val fsAx = remember { Animatable(0f) }
    val fsA = remember { Animatable(0f) }
    fun fsNowX() = if (fsDrag >= 0f) fsDx else fsAx.value
    fun fsNowY() = if (fsDrag >= 0f) fsDy else fsA.value
    // رمي الفيديو للأسفل (سحب للأسفل في البورتريه)
    val dismissA = remember { Animatable(0f) }
    var dismissDrag by remember { mutableStateOf(-1f) }
    var flying by remember { mutableStateOf(false) }   // الفيديو راجع لبطاقته بأنيميشن
    fun dyNow() = if (dismissDrag >= 0f) dismissDrag else dismissA.value
    val cfg = LocalConfiguration.current
    val winPortraitNow by rememberUpdatedState(cfg.orientation == Configuration.ORIENTATION_PORTRAIT)
    var vAr by remember { mutableStateOf(0f) }
    var pvRef by remember { mutableStateOf<PlayerView?>(null) }
    val ctlRot by remember { derivedStateOf { fpNow() > 0.5f } }
    // موضع كل شريحة داخل صف الشرائح (x = حافتها اليسرى، y = عرضها). مفتاحها رقم الصفحة: 0 المفضلة، i+1 القائمة i
    val chipPos = remember { mutableStateMapOf<Int, Offset>() }
    val chipScroll = rememberScrollState()
    var chipRowW by remember { mutableStateOf(0) }
    val listChannels = remember { mutableStateMapOf<String, List<Channel>>() }
    val listGrids = remember { mutableMapOf<String, LazyGridState>() }
    var dotLeft by remember { mutableStateOf(0f) }
    val uiAlpha by animateFloatAsState(
        targetValue = if (ui) 1f else 0f,
        animationSpec = tween(durationMillis = if (ui) 280 else 650, easing = FastOutSlowInEasing),
        label = "ui"
    )
    var quals by remember { mutableStateOf(listOf<Pair<Int, TrackSelectionOverride>>()) }
    var qSel by remember { mutableStateOf<Int?>(null) }
    var vh by remember { mutableStateOf(0) }
    var speed by remember { mutableStateOf(1f) }
    var pipMode by remember { mutableStateOf(store.pipMode()) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(uiAlpha < 0.5f) { if (uiAlpha < 0.5f) menu = false }   // القائمة نافذة مستقلة: تنقفل مع اختفاء الأزرار
    var menuPage by remember { mutableIntStateOf(0) }       // 0 القائمة الرئيسية، 1 اختيار السرعة
    var swipeOn by remember { mutableStateOf(store.swipeGestures()) }
    var hud by remember { mutableIntStateOf(0) }            // 0 لا شي، 1 صوت، 2 سطوع، 3 تقديم/تأخير
    var hudLevel by remember { mutableFloatStateOf(0f) }
    var hudText by remember { mutableStateOf("") }
    var hudHold by remember { mutableStateOf(false) }
    var hudTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(hudTick) { if (hudTick > 0) { delay(700); if (!hudHold) hud = 0 } }
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
    val hostAct = remember { activityOf(ctx) }
    fun curBright(): Float {
        val b = hostAct?.window?.attributes?.screenBrightness ?: -1f
        return if (b >= 0f) b else try {
            android.provider.Settings.System.getInt(ctx.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (_: Throwable) { 0.5f }
    }
    fun setBright(v: Float) {
        hostAct?.window?.let { w -> val lp = w.attributes; lp.screenBrightness = v; w.attributes = lp }
    }
    val scope = rememberCoroutineScope()
    val hapticView = androidx.compose.ui.platform.LocalView.current
    fun pullLogs() {
        scope.launch {
            val t = withContext(Dispatchers.IO) { Logs.collect(ctx) }
            try {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Glass Player log", t))
            } catch (_: Throwable) {}
            Toast.makeText(ctx, "تم نسخ السجل", Toast.LENGTH_SHORT).show()
            try {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Glass Player log")
                    putExtra(android.content.Intent.EXTRA_TEXT, t)
                }
                ctx.startActivity(android.content.Intent.createChooser(send, "مشاركة السجل").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Throwable) {}
        }
    }
    var showSettings by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(player?.isPlaying == true) }
    var error by remember { mutableStateOf<String?>(null) }
    var full by remember { mutableStateOf(false) }
    // الأزرار تختفي تلقائياً بعد 5 ثواني من ظهورها (يتجدد العد عند أي لمسة على الأزرار)
    var uiTick by remember { mutableStateOf(0) }
    var resumeAsk by remember { mutableStateOf(0L) }   // موضع المتابعة (ms) لو الفيديو مشغّل قبل أكثر من دقيقة، وإلا 0
    var skipDir by remember { mutableIntStateOf(0) }    // أثر التقديم/الإرجاع: 1 يمين، -1 يسار
    var skipTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(skipTick) { if (skipTick > 0) { delay(650); skipDir = 0 } }
    LaunchedEffect(resumeAsk) { if (resumeAsk > 0L) { delay(9000); resumeAsk = 0L } }
    LaunchedEffect(cur == null) { if (cur == null) hostAct?.window?.let { w -> val lp = w.attributes; lp.screenBrightness = -1f; w.attributes = lp } }
    LaunchedEffect(cur, ui, menu, uiTick) {
        if (cur != null && ui && !menu) {
            snapshotFlow { reveal.value }.first { it >= 0.99f }
            delay(5000)
            ui = false
        }
    }
    // حالة الشبكة هنا فوق عشان ترجع لنفس المكان بعد الخروج من القناة
    fun gridFor(name: String) = listGrids.getOrPut(name) { LazyGridState() }
    // الصفحة i = القائمة i (كل قائمة صفحة حقيقية، والسحب بينها حي)
    val pager = rememberPagerState(initialPage = 0, pageCount = { lists.size })
    var mainTab by remember { mutableStateOf(0) }
    LaunchedEffect(tab) {
        if (tab >= 0) mainTab = tab
        val target = tab
        if (pager.currentPage != target) pager.animateScrollToPage(target)
    }
    fun pagePos() = pager.currentPage + pager.currentPageOffsetFraction
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { pg ->
            val t = pg
            if (tab != t) tab = t
            if (t >= 0) mainTab = t
        }
    }
    // الشريط العلوي ما يتحرك مع إصبعك: بعد ما تستقر الصفحة (تنتهي عملية الانتقال) يتحرك بنعومة والشريحة المحددة بنص الشاشة
    LaunchedEffect(Unit) {
        var first = true
        snapshotFlow {
            val c = chipPos[pager.settledPage]
            if (c == null || chipRowW == 0) null else c.x + c.y / 2f - chipRowW / 2f
        }.collectLatest { target ->
            if (target != null) {
                val t = target.toInt().coerceAtLeast(0)
                if (first) { first = false; chipScroll.scrollTo(t) }
                else chipScroll.animateScrollTo(t, tween(750, easing = FastOutSlowInEasing))
            }
        }
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                if (isPlaying) error = null
            }
            override fun onPlaybackStateChanged(state: Int) {
                // انتهى الفيديو: نظهر الأزرار عشان تعيده أو ترجع
                if (state == Player.STATE_ENDED) ui = true
            }
            override fun onPlayerError(e: PlaybackException) {
                trace("onPlayerError ${e.errorCodeName}: ${e.message}")
                error = "تعذّر تشغيل الفيديو (${e.errorCodeName})"
            }
            override fun onVideoSizeChanged(size: VideoSize) {
                vh = size.height
                vAr = if (size.height > 0) size.width * size.pixelWidthHeightRatio / size.height else 0f
            }
            override fun onTracksChanged(tracks: Tracks) {
                quals = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { g ->
                    (0 until g.length).filter { g.isTrackSupported(it) && g.getTrackFormat(it).height > 0 }
                        .map { i -> g.getTrackFormat(i).height to TrackSelectionOverride(g.mediaTrackGroup, i) }
                }.sortedByDescending { it.first }.distinctBy { it.first }
            }
        }
        player?.addListener(l)
        onDispose { player?.removeListener(l) }
    }

    LaunchedEffect(hasPerm) { if (hasPerm) folders = withContext(Dispatchers.IO) { loadFolders(ctx) } }
    // كل قائمة تتحمّل لحالها: الحالية تتحدّث كل مرة، والجيران نحمّلهم لو ما انحمّلوا عشان السحب ما يشوف صفحة فاضية
    LaunchedEffect(pager.settledPage, lists, hasPerm) {
        if (hasPerm) {
            val cp = pager.settledPage
            for (pg in listOf(cp, cp - 1, cp + 1)) {
                val name = lists.getOrNull(pg) ?: continue
                if (pg == cp || listChannels[name] == null) {
                    listChannels[name] = withContext(Dispatchers.IO) { loadVideos(ctx, name) }
                }
            }
        }
    }

    // ارتداد خفيف مع هزات بسيطة عند الوصول لحد التقريب (الأدنى أو الأقصى)
    val limScale = remember { Animatable(1f) }
    val limShake = remember { Animatable(0f) }
    // st = قوة الصدمة 0..1 (من سرعة السحب): تتحكم بعمق الانكماش وقوة الاهتزاز البصري وقوة هزة الجوال
    fun limitBounce(st: Float = 0.5f) {
        val k = st.coerceIn(0f, 1f)
        impactBuzz(hapticView, k)
        scope.launch {
            launch { limScale.snapTo(1f - (0.02f + 0.14f * k)); limScale.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 520f)) }
            launch { limShake.snapTo(2f + 26f * k); limShake.animateTo(0f, spring(dampingRatio = 0.14f, stiffness = 800f)) }
        }
    }

    // معاملات تحجيم الفيديو (بمحوره المحلي) ليملأ الشاشة بالنسبة المطلوبة؛ rot = الشاشة الكاملة المدوّرة
    fun baseScale(rot: Boolean, w: Float, h: Float): Offset {
        val ar = if (vAr > 0f) vAr else 16f / 9f
        val ow: Float; val oh: Float
        if (w / h < ar) { ow = w; oh = w / ar } else { oh = h; ow = h * ar }
        val t = if (arForced) (if (ar >= 1.55f) 4f / 3f else 16f / 9f) else ar
        val fw = if (rot) h else w; val fh = if (rot) w else h
        val dw: Float; val dh: Float
        if (fw / fh < t) { dw = fw; dh = fw / t } else { dh = fh; dw = fh * t }
        return Offset(dw / ow, dh / oh)
    }
    // حدود تحريك الفيديو المقرّب (بإحداثيات الشاشة). لو الفيديو أصغر من الشاشة على محور يبقى في المنتصف
    fun panLimit(z: Float, w: Float, h: Float): Offset {
        val ar = if (vAr > 0f) vAr else 16f / 9f
        val ow: Float; val oh: Float
        if (w / h < ar) { ow = w; oh = w / ar } else { oh = h; ow = h * ar }
        val rot = full && h > w
        val b = baseScale(rot, w, h)
        val lx = ow * b.x * z; val ly = oh * b.y * z
        val ex = if (rot) ly else lx
        val ey = if (rot) lx else ly
        return Offset(((ex - w) / 2f).coerceAtLeast(0f), ((ey - h) / 2f).coerceAtLeast(0f))
    }
    // الزوم حول نقطة (f نسبة لمنتصف الشاشة): النقطة اللي تحت الإصبع تبقى مكانها
    fun zoomAt(z0: Float, pan0: Offset, z1: Float, f: Offset, w: Float, h: Float): Offset {
        val r = z1 / z0
        val lim = panLimit(z1, w, h)
        return Offset(
            (f.x - (f.x - pan0.x) * r).coerceIn(-lim.x, lim.x),
            (f.y - (f.y - pan0.y) * r).coerceIn(-lim.y, lim.y)
        )
    }
    val flingJob = remember { arrayOfNulls<Job>(1) }

    fun start(c: Channel, from: Rect? = null) {
        trace("start(${c.name}) closing=$closing")
        if (closing) return
        error = null; qSel = null; vh = 0; ui = true; zoom = 1f; panX = 0f; panY = 0f; speed = 1f
        arForced = false
        store.bumpCount(c.url)
        val savedPos = store.pos(c.url)
        resumeAsk = if (savedPos > 60_000L) savedPos else 0L
        val dm = ctx.resources.displayMetrics
        val cx = dm.widthPixels / 2f; val cy = dm.heightPixels / 2f
        origin = if (from != null && from.width > 0f) from else Rect(cx - 80f, cy - 80f, cx + 80f, cy + 80f)
        cur = c
        scope.launch {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
        }
        player?.let { p ->
            val md = MediaMetadata.Builder().setTitle(cleanName(c.name)).build()
            val item = MediaItem.Builder().setUri(c.url).setMediaMetadata(md).build()
            // نكمل من آخر موضع وقفت عنده
            // احتياط: لو مسار الفيديو كان مطفي (وضع الصوت فقط بالخلفية) نشغّله قبل التشغيل، وإلا يطلع صوت بدون صورة
            if (p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO)) {
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
            }
            // المقاطع القصيرة (أو لو الموضع المحفوظ أقل من دقيقة) تبدأ من البداية
            p.setMediaItem(item, if (savedPos > 60_000L) savedPos else 0L)
            p.setPlaybackSpeed(1f)
            p.prepare(); p.play()
            // صورة الفيديو المصغّرة تظهر بإشعار التشغيل: نجهزها بالخلفية ثم نحدّث بيانات العنصر بدون ما يتأثر التشغيل
            val uriStr = c.url
            scope.launch {
                val art = withContext(Dispatchers.IO) { loadArtwork(ctx, uriStr) } ?: return@launch
                val cur0 = p.currentMediaItem ?: return@launch
                if (cur0.localConfiguration?.uri?.toString() != uriStr) return@launch
                val md2 = cur0.mediaMetadata.buildUpon()
                    .setArtworkData(art, MediaMetadata.PICTURE_TYPE_FRONT_COVER).build()
                try { p.replaceMediaItem(p.currentMediaItemIndex, cur0.buildUpon().setMediaMetadata(md2).build()) } catch (_: Throwable) {}
            }
        }
    }

    fun setQuality(o: TrackSelectionOverride?, h: Int?) {
        player?.let { p ->
            val b = p.trackSelectionParameters.buildUpon()
            if (o == null) b.clearOverridesOfType(C.TRACK_TYPE_VIDEO) else b.setOverrideForType(o)
            p.trackSelectionParameters = b.build()
        }
        qSel = h
    }

    fun bars(show: Boolean) {
        val c = WindowCompat.getInsetsController(act.window, act.window.decorView)
        if (show) c.show(WindowInsetsCompat.Type.systemBars())
        else {
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // يكمّل أنيميشن الشاشة الكاملة (تدوير الفيديو فقط، بدون تدوير النظام)
    fun settleFull(p0: Float, target: Float) {
        scope.launch {
            fullP.snapTo(p0); fpDrag = -1f
            fullP.animateTo(target, tween(if (target >= 1f) 320 else 380, easing = FastOutSlowInEasing))
            buzz(hapticView, false)   // هزة عند اكتمال التكبير أو التصغير
        }
    }

    fun setFull(on: Boolean) {
        if (on == full) return
        full = on
        if (on) ui = false   // عند التكبير الأزرار وسلايدر الصوت تنخفي حتى لو كانت ظاهرة
        bars(!on)
        settleFull(fpNow(), if (on) 1f else 0f)
    }

    // نحفظ موضع المتابعة (ما نحفظه لو الفيديو على وشك الانتهاء)
    fun saveResume() {
        val p = player ?: return
        val u = cur?.url ?: return
        val d = p.duration; val t = p.currentPosition
        store.savePos(u, if (d > 0L && t > 3000L && t < d - 5000L) t else 0L)
    }
    LaunchedEffect(cur, playing) { while (cur != null && playing) { delay(5000); saveResume() } }

    // الخروج: الفيديو ينكمش راجع لمكان البطاقة، وبعدها نوقف التشغيل
    fun close() {
        trace("close() closing=$closing cur=${cur?.name} reveal=${reveal.value}", Throwable("close caller"))
        if (closing || cur == null) return
        closing = true
        if (full) { full = false; bars(true) }
        scope.launch {
            if (fullP.value != 0f) fullP.snapTo(0f)
            reveal.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
            saveResume(); player?.stop(); cur = null; error = null
            zoom = 1f; panX = 0f; panY = 0f
            closing = false
        }
    }

    fun toggleFav(c: Channel) {
        favs = if (favs.any { it.url == c.url }) favs.filter { it.url != c.url } else favs + c
        store.saveFavs(favs)
    }

    fun addUrl() {
        val u = url.trim()
        if (u.isEmpty()) return
        showAdd = false
        start(Channel(u.substringAfterLast('/').substringBefore('?').ifEmpty { "فيديو" }, u))
        url = ""
    }

    BackHandler(cur != null) { trace("BackHandler fired full=$full"); if (full) setFull(false) else close() }
    // فتح الفيديو الشغال من إشعار التشغيل (التطبيق كان بالخلفية)
    LaunchedEffect(openReq, player) {
        if (openReq > 0 && player != null && cur == null && !closing &&
            player.currentMediaItem != null && player.playbackState != Player.STATE_IDLE) {
            val mi = player.currentMediaItem!!
            val md = mi.mediaMetadata
            val ch = Channel(md.title?.toString() ?: "فيديو", mi.localConfiguration?.uri?.toString() ?: "")
            trace("resume video from notification: ${ch.name}")
            error = null; qSel = null; vh = 0; ui = true; zoom = 1f; panX = 0f; panY = 0f
            arForced = false
            val dm = ctx.resources.displayMetrics
            val cx = dm.widthPixels / 2f; val cy = dm.heightPixels / 2f
            origin = Rect(cx - 80f, cy - 80f, cx + 80f, cy + 80f)
            cur = ch
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(player) {
        // ما في سلايدر صوت بالمشغل: الصوت من أزرار الجوال، فنخلي مستوى المشغل كامل
        player?.let { it.volume = 1f }
    }
    // فيديو جاي من تطبيق ثاني أو من منتقي الملفات: ننتظر لين المشغّل يتصل ثم نشغّله
    LaunchedEffect(extUri, player) {
        if (extUri != null && player != null) { start(Channel(displayName(ctx, extUri), extUri.toString())); onExtUri() }
    }
    LaunchedEffect(pendingOpen, player) {
        val u = pendingOpen
        if (u != null && player != null) { start(Channel(displayName(ctx, u), u.toString())); pendingOpen = null }
    }
    LaunchedEffect(cur) { trace("cur changed -> ${cur?.name} reveal=${reveal.value}") }
    LaunchedEffect(inPip) { trace("inPip=$inPip") }

    if (inPip) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { player?.let { Video(it) } }
        return
    }

    val pal = LocalPal.current
    val favUrls = remember(favs) { favs.map { it.url }.toHashSet() }
    val hh = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 112.dp
    val layer = rememberGraphicsLayer()
    val frost = remember { if (Build.VERSION.SDK_INT >= 31) frostEffect(110f) else null }
    var menuMore by remember { mutableStateOf(false) }
    var menuSort by remember { mutableStateOf(false) }
    var sorts by remember { mutableStateOf(store.sorts()) }
    var counts by remember { mutableStateOf(store.counts()) }
    // عدد مرات التشغيل يتحدّث لما ترجع للقائمة (عشان ما تتحرك البطاقات وأنت داخل قناة)
    LaunchedEffect(cur == null) { if (cur == null) counts = store.counts() }
    // كل قائمة لها ترتيبها الخاص (المفضلة = "fav")
    fun sortKey(): String = "f:" + (lists.getOrNull(mainTab) ?: "")
    // هزة عند التنقل بين القوائم
    // الهزة فورية: عند الضغط على القائمة مباشرة، أو لما الصفحة تعدّي نصف السحب (مو بعد ما تستقر)
    var lastTick by remember { mutableStateOf(0L) }
    fun tick() {
        val n = android.os.SystemClock.uptimeMillis()
        if (n - lastTick > 350L) { lastTick = n; buzz(hapticView, false) }
    }
    var prevTab by remember { mutableStateOf(tab) }
    LaunchedEffect(tab) { if (tab != prevTab) { prevTab = tab; tick() } }
    LaunchedEffect(pager) {
        var first = true
        snapshotFlow { pager.currentPage }.collect { if (first) first = false else tick() }
    }
    // لمعة الهيدر: حلقة إطارات تشتغل فقط وأنت في الواجهة الرئيسية
    var shT by remember { mutableStateOf(0f) }
    // تذبذب نور النجمة: يقوى ويضعف بشكل غير منتظم مثل نجم يتلألأ في الفضاء (0.18 ضعيف .. 1 قوي)
    var twk by remember { mutableStateOf(1f) }
    LaunchedEffect(cur == null) {
        if (cur == null) {
            val t0 = withFrameNanos { it }
            while (true) {
                val t = withFrameNanos { it }
                val ms = (t - t0) / 1_000_000L
                val ph = ms % 16000L
                shT = if (ph < 12000L) ph / 12000f else 1f
                val sec = ms / 1000.0
                val tau = 2.0 * Math.PI
                // ثلاث موجات بترددات غير متناسبة فلا يتكرر النمط بشكل واضح
                val v = 0.60 + 0.24 * Math.sin(tau * sec / 2.3) +
                    0.13 * Math.sin(tau * sec / 0.83 + 1.3) +
                    0.07 * Math.sin(tau * sec / 0.31 + 2.1)
                twk = v.toFloat().coerceIn(0.18f, 1f)
            }
        }
    }
    val c = cur
    SideEffect {
        val ic = WindowCompat.getInsetsController(act.window, act.window.decorView)
        ic.isAppearanceLightStatusBars = !pal.dark && cur == null
        ic.isAppearanceLightNavigationBars = !pal.dark && cur == null
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            // الواجهة الرئيسية تصغر وتخفت شوي وقت ما الفيديو يفتح فوقها
            val dp = (dyNow() / (size.height * 0.6f)).coerceIn(0f, 1f)
            val p = (reveal.value * (1f - dp)).coerceIn(0f, 1f)
            val k = 1f - 0.07f * p
            scaleX = k; scaleY = k; alpha = 1f - 0.6f * p
        }) {
            Box(Modifier.fillMaxSize().background(pal.bg1)) {
                // الخلفية والقنوات كلها تتسجل في طبقة وحدة، وتنرسم تحت الهيدر فقط
                Box(Modifier.fillMaxSize().drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    clipRect(top = hh.toPx()) { drawLayer(layer) }
                }) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(pal.bg1, pal.bg2))))
                    Box(Modifier.fillMaxSize().drawBehind {
                        drawRect(Brush.radialGradient(listOf(pal.blob1, Color.Transparent), Offset(size.width * 0.1f, size.height * 0.05f), size.width * 0.9f))
                        drawRect(Brush.radialGradient(listOf(pal.blob2, Color.Transparent), Offset(size.width * 0.95f, size.height * 0.95f), size.width * 0.9f))
                    })
                    val pagerJob = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            // التنقل بين المفضلة والقوائم: نتحكم فيه بأنفسنا. ما يبدأ إلا لو السحب أفقي بوضوح،
                            // وأي سحب مائل (حتى لو مال الإصبع) يعتبر تمرير عمودي للقائمة
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                // لو الصفحة تكمل انزلاقها من سحبة سابقة نمسكها بإصبعنا، وغير كذا (ضغط شريحة) نتجاهل
                                var caught = false
                                if (pager.isScrollInProgress) {
                                    if (pagerJob[0]?.isActive == true) { pagerJob[0]?.cancel(); caught = true }
                                    else return@awaitEachGesture
                                }
                                val vt = VelocityTracker()
                                vt.addPointerInputChange(down)
                                var decided = false
                                var horiz = false
                                var last = down.position
                                while (true) {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    vt.addPointerInputChange(ch)
                                    val tot = ch.position - down.position
                                    if (!decided) {
                                        if (tot.getDistance() <= viewConfiguration.touchSlop) continue
                                        decided = true
                                        val cp = pager.currentPage
                                        val dirOk = (tot.x > 0f && cp > 0) || (tot.x < 0f && cp < pager.pageCount - 1)
                                        horiz = dirOk && kotlin.math.abs(tot.x) > kotlin.math.abs(tot.y) * 2.2f
                                        if (!horiz) break   // عمودي أو مائل: القائمة تتمرر عادي
                                    }
                                    pager.dispatchRawDelta(-(ch.position.x - last.x))
                                    last = ch.position
                                    ch.consume()
                                }
                                if (horiz) {
                                    val vx = vt.calculateVelocity().x
                                    val pos = pagePos()
                                    val target = (if (vx > 700f) kotlin.math.floor(pos).toInt() else if (vx < -700f) kotlin.math.ceil(pos).toInt() else pager.currentPage)
                                        .coerceIn(0, pager.pageCount - 1)
                                    pagerJob[0] = scope.launch { pager.animateScrollToPage(target) }
                                } else if (caught) {
                                    pagerJob[0] = scope.launch { pager.animateScrollToPage(pager.currentPage) }
                                }
                            }
                        }, userScrollEnabled = false, beyondViewportPageCount = 1) { page ->
                        val isFavPage = false
                        val listName = lists.getOrNull(page) ?: ""
                        val pageSrc = if (isFavPage) favs else listChannels[listName]
                        val smode = sorts[if (isFavPage) "fav" else "f:$listName"] ?: 0
                        val shownList = remember(isFavPage, pageSrc, smode, counts) {
                            sortChannels(pageSrc ?: emptyList(), smode, counts)
                        }
                        val gs = gridFor(listName)
                        if (!isFavPage) ScrollTicks(gs, hapticView)
                        Box(Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                state = gs,
                                columns = GridCells.Adaptive(150.dp),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = hh + 8.dp, bottom = 48.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(shownList, contentType = { "tile" }) { ch ->
                                    Tile(ch, false, onLong = {}) { r -> start(ch, r) }
                                }
                            }
                            if (isFavPage && favs.isEmpty()) Text("اضغط مطولاً على أي فيديو لإضافته للمفضلة", color = pal.textDim,
                                textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(32.dp))
                            else if (!isFavPage && !hasPerm) Column(Modifier.align(Alignment.Center).padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("التطبيق يحتاج إذن الوصول للفيديوهات", color = pal.textDim, textAlign = TextAlign.Center)
                                Box(Modifier.width(150.dp).height(48.dp).glass(16.dp).clickable { permLauncher.launch(videoPerm()) },
                                    contentAlignment = Alignment.Center) { Text("السماح", color = pal.text, fontWeight = FontWeight.SemiBold) }
                            }
                            else if (!isFavPage && pageSrc == null) Text("جاري التحميل...", color = pal.textDim,
                                modifier = Modifier.align(Alignment.Center))
                            else if (!isFavPage && pageSrc!!.isEmpty()) Text("ما في فيديوهات هنا. تقدر تفتح ملف من الإعدادات", color = pal.textDim,
                                textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(32.dp))
                        }
                    }
                }
                // الهيدر: زجاج مضبب حقيقي، نفس الطبقة لكن مموهة بقوة
                Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(hh)) {
                    Box(Modifier.fillMaxSize()
                        .graphicsLayer { renderEffect = frost; clip = true }
                        .drawBehind { drawLayer(layer) })
                    Box(Modifier.fillMaxSize().background(pal.headerTint))
                    // حافة الهيدر السفلية: مذنّب متوهج متغير الألوان وذيل يتلاشى
                    Box(Modifier.align(Alignment.BottomCenter).offset(y = 30.dp).fillMaxWidth().height(60.dp).drawBehind {
                        val yb = size.height / 2f
                        val tail = size.width * 0.38f
                        val x0 = size.width * 0.25f
                        val x1 = size.width * 0.82f
                        val hx = x0 + (x1 - x0) * shT
                        fun sm(a: Float, b: Float, x: Float): Float { val u = ((x - a) / (b - a)).coerceIn(0f, 1f); return u * u * (3f - 2f * u) }
                        val env = sm(0f, 0.3f, shT) * (1f - sm(0.6f, 1f, shT))
                        val hue = 190f + 110f * shT
                        val hc = Color.hsv(hue, 0.55f, 1f)
                        val hc2 = Color.hsv((hue + 70f) % 360f, 0.6f, 1f)
                        // الخط الأساسي
                        drawRect(pal.rim,
                            topLeft = Offset(0f, yb - 1.dp.toPx()), size = Size(size.width, 2.dp.toPx()))
                        // مذنّب صغير وناعم: يظهر تدريجياً من الربع ويتلاشى قبل نهاية الخط، وألوانه تتغير ببطء
                        fun Color.a(x: Float) = copy(alpha = alpha * x * env)
                        val tl = size.width * 0.26f
                        fun taper(tw: Float) = Path().apply {
                            moveTo(hx, yb - tw / 2f)
                            quadraticBezierTo(hx - tl * 0.5f, yb - tw * 0.25f, hx - tl, yb)
                            quadraticBezierTo(hx - tl * 0.5f, yb + tw * 0.25f, hx, yb + tw / 2f)
                            this.close()   // this عشان ما تنادي دالة close() حقت القناة
                        }
                        if (env > 0.002f) {
                            // أثر متوهج يبقى على الخط خلف النجمة ويتلاشى تدريجياً
                            val gx0 = (hx - tl * 1.5f).coerceAtLeast(0f)
                            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, hc.a(0.10f), hc2.a(0.30f)), startX = gx0, endX = hx),
                                topLeft = Offset(gx0, yb - 3.dp.toPx()), size = Size(hx - gx0, 6.dp.toPx()))
                            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, hc2.a(0.35f), pal.shim.a(0.75f)), startX = gx0, endX = hx),
                                topLeft = Offset(gx0, yb - 1.dp.toPx()), size = Size(hx - gx0, 2.dp.toPx()))
                            // ذيل نحيف بثلاث طبقات: هالة خفيفة، ثم لون متوهج، ثم قلب أبيض ساطع
                            drawPath(taper(7.dp.toPx()), Brush.horizontalGradient(listOf(Color.Transparent, hc.a(0.12f), hc2.a(0.38f)), startX = hx - tl, endX = hx))
                            drawPath(taper(3.2f.dp.toPx()), Brush.horizontalGradient(listOf(Color.Transparent, hc2.a(0.5f), hc.a(0.9f)), startX = hx - tl, endX = hx))
                            drawPath(taper(1.4f.dp.toPx()), Brush.horizontalGradient(listOf(Color.Transparent, pal.shim.a(0.7f), Color.White.copy(alpha = env)), startX = hx - tl * 0.85f, endX = hx))
                            for (i in 1..4) {
                                val f = i / 5f
                                val sp = Offset(hx - tl * f * 0.8f, yb + kotlin.math.sin(shT * 14f + i * 2.1f) * 4.dp.toPx() * f)
                                drawCircle(hc2.a((1f - f) * 0.4f), radius = (1.6f - f * 0.9f).dp.toPx(), center = sp)
                            }
                            val c0 = Offset(hx, yb)
                            // تلألؤ النجمة: الشدة والحجم يتغيران معاً مع twk
                            val tw = twk
                            val pulse = (0.8f + 0.35f * tw)
                            val halo = 16.dp.toPx() * (0.75f + 0.5f * tw)
                            drawCircle(Brush.radialGradient(listOf(hc.a(0.28f * (0.35f + 0.65f * tw)), Color.Transparent), center = c0, radius = halo),
                                radius = halo, center = c0)
                            val core = 6.dp.toPx() * pulse
                            drawCircle(Brush.radialGradient(listOf(pal.shim.a(0.85f * (0.25f + 0.75f * tw)), hc.a(0.5f * (0.3f + 0.7f * tw)), Color.Transparent), center = c0, radius = core),
                                radius = core, center = c0)
                        }
                    })
                    Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Glass Player", color = pal.text.copy(alpha = 0.88f), fontSize = 25.sp, fontFamily = TitleFont,
                                fontWeight = FontWeight.Medium, letterSpacing = 2.sp,
                                modifier = Modifier.padding(vertical = 10.dp).logoShimmer())
                            Spacer(Modifier.weight(1f))
                            Box {
                                Box(Modifier.size(40.dp).glass(14.dp).clickable { menuSort = true }, contentAlignment = Alignment.Center) {
                                    Icon(ISort, contentDescription = null, tint = pal.text, modifier = Modifier.size(22.dp))
                                }
                                DropdownMenu(expanded = menuSort, onDismissRequest = { menuSort = false }) {
                                    listOf("الأحدث", "أبجدي (A-Z)", "الأكثر تشغيلاً").forEachIndexed { i, t ->
                                        DropdownMenuItem(text = { Text(if ((sorts[sortKey()] ?: 0) == i) "✓  $t" else t) }, onClick = {
                                            val k = sortKey()
                                            sorts = sorts + (k to i); store.saveSorts(sorts); menuSort = false
                                            scope.launch { gridFor(lists.getOrNull(mainTab) ?: "").scrollToItem(0) }
                                        })
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.size(40.dp).glass(14.dp).clickable { showSettings = true }, contentAlignment = Alignment.Center) {
                                Icon(IGear, contentDescription = null, tint = pal.text, modifier = Modifier.size(22.dp))
                            }
                        }
                        // صف الشرائح: تقدر تسحبه عرضياً بإصبعك، وبعد كل انتقال يرجّع الشريحة المحددة لنص الشاشة
                        Row(
                            Modifier.fillMaxWidth().onSizeChanged { chipRowW = it.width }.horizontalScroll(chipScroll),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val sideW = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp / 2).dp
                            Spacer(Modifier.width(sideW))
                            lists.forEachIndexed { i, u ->
                                val label = if (u.isEmpty()) "كل الفيديوهات" else u
                                Chip(label.take(24), { (1f - kotlin.math.abs(pagePos() - i)).coerceIn(0f, 1f) },
                                    Modifier.onGloballyPositioned { val o = Offset(it.positionInParent().x, it.size.width.toFloat()); if (chipPos[i] != o) chipPos[i] = o }) { tab = i }
                            }
                            Spacer(Modifier.width(sideW))
                        }
                        // النقطة الديناميكية: تتبع إصبعك بالضبط بين الشرائح وتتمدد وهي تتحرك (نفس سحب المفضلة)
                        Box(Modifier.fillMaxWidth().height(12.dp)
                            .drawBehind {
                                val n = pager.pageCount
                                val pos = pagePos().coerceIn(0f, (n - 1).toFloat())
                                val k0 = kotlin.math.floor(pos).toInt().coerceIn(0, n - 1)
                                val k1 = (k0 + 1).coerceAtMost(n - 1)
                                val ca = chipPos[k0]; val cb = chipPos[k1]
                                if (ca != null && cb != null) {
                                    val f = pos - k0
                                    val x = (ca.x + ca.y / 2f) * (1f - f) + (cb.x + cb.y / 2f) * f - chipScroll.value
                                    val moving = kotlin.math.sin(Math.PI.toFloat() * f)
                                    val w = 16.dp.toPx() + moving * 36.dp.toPx()
                                    val h = 5.dp.toPx()
                                    drawRoundRect(pal.dot.copy(0.28f), Offset(x - w / 2f - 3.dp.toPx(), 1.dp.toPx()),
                                        Size(w + 6.dp.toPx(), h + 4.dp.toPx()), CornerRadius(h))
                                    drawRoundRect(pal.dot.copy(0.95f), Offset(x - w / 2f, 3.dp.toPx()), Size(w, h), CornerRadius(h / 2f))
                                }
                            })
                    }
                }
            }
        }

        CompositionLocalProvider(LocalPal provides DarkPal) {
        if (c != null) {
            val isFav = favs.any { it.url == c.url }
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        // الإطار يتوسع/ينكمش بين البطاقة والشاشة الكاملة
                        val p = reveal.value.coerceIn(0f, 1f)
                        shape = RevealShape(p, origin, 22.dp.toPx())
                        clip = true
                        alpha = (p * 4f).coerceIn(0f, 1f)
                    }
                    .drawBehind {
                        // الخلفية السوداء تختفي أثناء السحب للأسفل عشان تشوف الواجهة وراء الفيديو
                        val bgA = if (flying) 0f else (1f - dyNow() / (size.height * 0.12f)).coerceIn(0f, 1f)
                        drawRect(Color.Black, alpha = bgA)
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            flingJob[0]?.cancel()
                            val vt = VelocityTracker()
                            vt.addPointerInputChange(down)
                            var moved = false
                            var panning = false
                            var released = false
                            var mode = 0            // 0 لا شي، 1 دخول شاشة كاملة، 2 خروج منها
                            var sideways = false    // دخول الشاشة الكاملة بسحب لليمين أو لليسار (بدل لأعلى)
                            var lastPos = down.position
                            val dist = 280.dp.toPx()
                            // الربع السفلي من الشاشة (منطقة إيماءة الخروج من التطبيق) ما يفعّل الشاشة الكاملة
                            // الأطراف اليمين واليسار (هامش 48dp) مثلها: ما تفعّل الشاشة الكاملة، عشان إيماءة الرجوع من حافة الشاشة تشتغل بدون ما الفيديو يكبر
                            val edgeW = 48.dp.toPx()
                            val onEdge = down.position.x < edgeW || down.position.x > size.width - edgeW
                            val blockUp = !full && (down.position.y > size.height * 0.86f || onEdge)
                            var prog = if (full) 1f else 0f
                            // هزات برايل: نقطة بنفس الشدة كل مسافة ثابتة من السحب (بدون تصاعد)، نزولاً وصعوداً
                            val brStep = 26.dp.toPx()
                            var brIdx = 0
                            var vBase = 0f; var bBase = 0f
                            var seekBase = 0L; var seekSpan = 0L; var seekTarget = -1L
                            var hIdx = Int.MIN_VALUE
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                vt.addPointerInputChange(ch)
                                if (!ch.pressed) { released = true; break }
                                val tot = ch.position - down.position
                                // اتجاهات الصورة: بعد التدوير مع عقارب الساعة، فوق الصورة = يمين الجهاز
                                val rot = full && size.height > size.width
                                val ay = if (rot) -tot.x else tot.y
                                val ax = if (rot) tot.y else tot.x
                                if (!moved && tot.getDistance() > viewConfiguration.touchSlop) {
                                    moved = true
                                    val vert = kotlin.math.abs(ay) > kotlin.math.abs(ax)
                                    val limM = panLimit(zoom, size.width.toFloat(), size.height.toFloat())
                                    val canPanDown = zoom > 1.02f && panY < limM.y - 1f
                                    // الإيماءات الجانبية (لو مفعّلة): أقصى اليمين = صوت النظام، أقصى اليسار = السطوع (سحب عمودي)، وأي سحب أفقي = تقديم/تأخير
                                    val zfx = if (rot) down.position.y / size.height else down.position.x / size.width
                                    val edgeZ = if (zfx > 0.8f) 1 else if (zfx < 0.2f) -1 else 0
                                    if (swipeOn && zoom <= 1.02f && !closing && (!vert || edgeZ != 0)) {
                                        if (!vert) {
                                            val pl = player
                                            if (pl != null && pl.duration > 0L) {
                                                mode = 6; seekBase = pl.currentPosition; seekSpan = minOf(180_000L, pl.duration); seekTarget = seekBase
                                            }
                                        } else if (edgeZ > 0) {
                                            mode = 4; vBase = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() /
                                                audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                        } else {
                                            mode = 5; bBase = curBright()
                                        }
                                        if (mode >= 4) hudHold = true
                                    }
                                    if (mode >= 4) {
                                        // قرار الإيماءة الجانبية اتخذ: نتخطى إيماءات الشاشة الكاملة والإغلاق
                                    } else if (zoom <= 1.02f && full) {
                                        // شاشة كاملة: السحب بأي اتجاه يلغيها ويرجع للبورتريه
                                        mode = 2
                                    } else if (zoom <= 1.02f && vert) {
                                        if (!full && ay < 0f && !blockUp) mode = 1
                                        else if (!full && ay > 0f) mode = 3
                                    } else if (zoom <= 1.02f && !full && !blockUp) {
                                        // سحب لليمين أو لليسار: تكبير الشاشة (الشاشة الكاملة)
                                        mode = 1; sideways = true
                                    } else if (!full && ay > 0f && !canPanDown && kotlin.math.abs(ay) > kotlin.math.abs(ax) * 0.7f) {
                                        // سحب للأسفل شبه عمودي، أو الفيديو مقرّب وواصل لحافته العليا: نقبل الرمي
                                        mode = 3
                                    }
                                }
                                if (mode >= 4) {
                                    val picH = (if (rot) size.width else size.height).toFloat()
                                    val picWd = (if (rot) size.height else size.width).toFloat()
                                    when (mode) {
                                        4 -> {
                                            val lv = (vBase - ay / (picH * 0.7f)).coerceIn(0f, 1f)
                                            val mx = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                            val iv = Math.round(lv * mx)
                                            if (audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) != iv) {
                                                audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, iv, 0)
                                                buzz(hapticView, false)
                                            }
                                            hud = 1; hudLevel = lv
                                        }
                                        5 -> {
                                            val lv = (bBase - ay / (picH * 0.7f)).coerceIn(0.02f, 1f)
                                            setBright(lv)
                                            val bi = (lv * 20f).toInt()
                                            if (bi != hIdx) { if (hIdx != Int.MIN_VALUE) buzz(hapticView, false); hIdx = bi }
                                            hud = 2; hudLevel = lv
                                        }
                                        else -> {
                                            val dur = player?.duration ?: 0L
                                            val t = (seekBase + (ax / picWd * seekSpan).toLong()).coerceIn(0L, if (dur > 0L) dur else seekBase)
                                            seekTarget = t
                                            val si = ((t - seekBase) / 10_000L).toInt()
                                            if (si != hIdx) { if (hIdx != Int.MIN_VALUE) buzz(hapticView, false); hIdx = si }
                                            hud = 3; hudText = (if (t >= seekBase) "+" else "-") + fmtDur(kotlin.math.abs(t - seekBase)) + "   ·   " + fmtDur(t)
                                        }
                                    }
                                    ch.consume()
                                } else if (mode == 3) {
                                    // سحب للأسفل في البورتريه: الفيديو ينزل مع الإصبع (حتى لو مقرّب)
                                    dismissDrag = ay.coerceAtLeast(0f)
                                    ch.consume()
                                } else if (mode == 1 || mode == 2) {
                                    // الفيديو يلحق إصبعك بنفس الاتجاه (في الخروج من الشاشة الكاملة: أي اتجاه)
                                    // دخول الشاشة الكاملة: الفيديو يلحق الإصبع بأي زاوية (قطري 45° أو أفقي أو عمودي) ولا يرتفع عمودياً فقط
                                    if (mode == 1) { fsDx = tot.x; fsDy = tot.y.coerceAtMost(0f) }
                                    else { fsDx = tot.x; fsDy = tot.y }
                                    fsDrag = 1f
                                    ch.consume()
                                } else if (moved && zoom > 1.02f) {
                                    // تحريك الفيديو بعد الزوم
                                    val d = ch.position - lastPos
                                    val lim = panLimit(zoom, size.width.toFloat(), size.height.toFloat())
                                    panX = (panX + d.x).coerceIn(-lim.x, lim.x)
                                    panY = (panY + d.y).coerceIn(-lim.y, lim.y)
                                    panning = true
                                    ch.consume()
                                } else if (mode != 0) {
                                    // الفيديو يتحرك مع الإصبع حي
                                    prog = if (mode == 1) (-ay / dist).coerceIn(0f, 1f) else 1f - ((if (rot) maxOf(ay, tot.y) else ay) / dist).coerceIn(0f, 1f)
                                    fpDrag = prog
                                    ch.consume()
                                }
                                if (mode in 1..3) {
                                    // المسافة المقطوعة من نقطة البداية حسب نوع السحب؛ كل تغيّر في رقم النقطة = هزة وحدة
                                    val travel = when (mode) {
                                        1 -> kotlin.math.sqrt(tot.x * tot.x + tot.y.coerceAtMost(0f) * tot.y.coerceAtMost(0f))
                                        3 -> ay.coerceAtLeast(0f)
                                        else -> maxOf(kotlin.math.abs(tot.x), kotlin.math.abs(tot.y))
                                    }
                                    val idx = (travel / brStep).toInt()
                                    if (idx != brIdx) { brIdx = idx; buzz(hapticView, false) }
                                }
                                lastPos = ch.position
                            }
                            if (mode >= 4) {
                                // الإفلات: التقديم/التأخير يتنفذ هنا، والمؤشر يختفي بعد لحظة
                                if (mode == 6 && seekTarget >= 0L) player?.seekTo(seekTarget)
                                hudHold = false; hudTick++
                                return@awaitEachGesture
                            }
                            if (mode == 1 || mode == 2) {
                                val v = vt.calculateVelocity()
                                val dispX = kotlin.math.abs(fsDx); val dispY = kotlin.math.abs(fsDy)
                                val dist1 = kotlin.math.sqrt(dispX * dispX + dispY * dispY)
                                val disp = if (mode == 1) dist1 else maxOf(dispX, dispY)
                                // الحد: بين 12% من العرض (أفقي) و12% من الارتفاع (عمودي) حسب زاوية السحب
                                val fracV = if (dist1 > 0f) dispY / dist1 else 1f
                                val thr = if (mode == 1) size.width * 0.12f * (1f - fracV) + size.height * 0.12f * fracV
                                          else if (mode == 2 && dispX > dispY) size.width * 0.12f else size.height * 0.12f
                                val vel = if (mode == 1) kotlin.math.sqrt(v.x * v.x + v.y.coerceAtMost(0f) * v.y.coerceAtMost(0f))
                                          else kotlin.math.sqrt(v.x * v.x + v.y * v.y)
                                val go = disp > thr || vel > 900f
                                val enter = mode == 1
                                scope.launch {
                                    fsAx.snapTo(fsDx); fsA.snapTo(fsDy)
                                    fsDrag = -1f; fsDx = 0f; fsDy = 0f
                                    if (go) {
                                        full = enter; bars(!enter)
                                        if (enter) ui = false   // التكبير بالسحب: نخفي الأزرار والصوت
                                        launch { fsAx.animateTo(0f, tween(360, easing = FastOutSlowInEasing)) }
                                        launch { fsA.animateTo(0f, tween(360, easing = FastOutSlowInEasing)) }
                                        // يلف ويكبر أو يصغر، وهزة عند الاكتمال
                                        settleFull(if (enter) 0f else 1f, if (enter) 1f else 0f)
                                    } else {
                                        launch { fsAx.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 300f)) }
                                        fsA.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 300f))
                                    }
                                }
                                return@awaitEachGesture
                            }
                            if (mode == 3) {
                                val v = vt.calculateVelocity()
                                val hPx = size.height.toFloat()
                                val dyEnd = dismissDrag.coerceAtLeast(0f)
                                val goBack = dyEnd > hPx * 0.10f || v.y > 900f
                                if (goBack) { closing = true; flying = true }
                                scope.launch {
                                    dismissA.snapTo(dyEnd); dismissDrag = -1f
                                    if (goBack) {
                                        // الفيديو يرجع لمكان القناة اللي فتحته منها
                                        launch { dismissA.animateTo(0f, tween(480, easing = FastOutSlowInEasing)) }
                                        reveal.animateTo(0f, tween(480, easing = FastOutSlowInEasing))
                                        trace("dismiss fly finished")
                                        saveResume(); player?.stop(); cur = null; error = null
                                        zoom = 1f; panX = 0f; panY = 0f
                                        flying = false; closing = false
                                    } else {
                                        dismissA.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 300f))
                                    }
                                }
                                return@awaitEachGesture
                            }
                            if (mode != 0) {
                                // عند الإفلات: نكمل للنهاية أو نرجع حسب المسافة والسرعة
                                val v = vt.calculateVelocity()
                                val rot = full && size.height > size.width
                                val vy = if (rot) (if (mode == 2) maxOf(-v.x, v.y) else -v.x) else v.y
                                val target = when {
                                    vy < -1200f -> 1f
                                    vy > 1200f -> 0f
                                    else -> if (prog > 0.5f) 1f else 0f
                                }
                                full = target == 1f
                                if (full) ui = false
                                bars(target == 0f)
                                settleFull(prog, target)
                                return@awaitEachGesture
                            }
                            if (panning && released && zoom > 1.02f) {
                                // انزلاق بعد الإفلات: الفيديو يكمل بسرعة الإصبع ويهدأ تدريجياً
                                val v = vt.calculateVelocity()
                                val lim = panLimit(zoom, size.width.toFloat(), size.height.toFloat())
                                val decay = exponentialDecay<Float>()
                                flingJob[0]?.cancel()
                                flingJob[0] = scope.launch {
                                    if (lim.x > 0f && kotlin.math.abs(v.x) > 60f) launch {
                                        Animatable(panX).apply { updateBounds(-lim.x, lim.x) }
                                            .animateDecay(v.x, decay) { if (zoom > 1.02f) panX = value }
                                    }
                                    if (lim.y > 0f && kotlin.math.abs(v.y) > 60f) launch {
                                        Animatable(panY).apply { updateBounds(-lim.y, lim.y) }
                                            .animateDecay(v.y, decay) { if (zoom > 1.02f) panY = value }
                                    }
                                }
                                return@awaitEachGesture
                            }
                            if (!released || moved) return@awaitEachGesture
                            // ضغطة وحدة: إظهار/إخفاء الأزرار. ضغطتين مع سحب: زوم
                            val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { awaitFirstDown() }
                            if (second == null) { ui = !ui; return@awaitEachGesture }
                            var lastP = second.position
                            val sw = size.width.toFloat(); val sh = size.height.toFloat()
                            // نقطة التقريب = مكان الإصبع (نسبة لمنتصف الشاشة)
                            val anchor = Offset(second.position.x - sw / 2f, second.position.y - sh / 2f)
                            var dragged = false
                            val vtZ = VelocityTracker()
                            vtZ.addPointerInputChange(second)
                            var over = 0f
                            var armed = true
                            var impactV = 0f   // أعلى سرعة سحب (px/ث) لحظة الاصطدام بحد التقريب
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == second.id } ?: break
                                vtZ.addPointerInputChange(ch)
                                if (!ch.pressed) break
                                val d = ch.position - lastP
                                lastP = ch.position
                                if (!dragged && (ch.position - second.position).getDistance() > viewConfiguration.touchSlop) dragged = true
                                val dyPic = if (full && size.height > size.width) -d.x else d.y
                                if (dyPic != 0f) {
                                    val zRaw = zoom * kotlin.math.exp(dyPic / 450f)
                                    val zNew = zRaw.coerceIn(1f, 4f)
                                    val np = zoomAt(zoom, Offset(panX, panY), zNew, anchor, sw, sh)
                                    zoom = zNew; panX = np.x; panY = np.y
                                    if (zRaw < 1f || zRaw > 4f) {
                                        // وصلنا لحد التقريب وما زال المستخدم يسحب: ارتداد وهزات حسب سرعة السحب
                                        val vNow = vtZ.calculateVelocity()
                                        val spd = kotlin.math.abs(if (full && size.height > size.width) vNow.x else vNow.y)
                                        impactV = kotlin.math.max(impactV, spd)
                                        over += kotlin.math.abs(dyPic)
                                        if (armed && over > 10.dp.toPx()) { armed = false; limitBounce(impactStrength(impactV / density)) }
                                    } else { over = 0f; armed = true; impactV = 0f }
                                    ch.consume()
                                }
                            }
                            // انزلاق الزوم بعد الإفلات: لو سحبت بسرعة وفلتّ، الزوم يكمل ويهدأ تدريجياً (مع بقاء نقطة الإصبع ثابتة)
                            if (dragged) {
                                val bouncedInDrag = !armed   // الصدمة صارت أثناء السحب، لا نكررها عند الإفلات
                                val vz = vtZ.calculateVelocity()
                                val rotNow = full && size.height > size.width
                                val uVel = (if (rotNow) -vz.x else vz.y) / 450f * 0.45f   // سرعة تغيّر ln(zoom) بالثانية (مخفّفة)
                                if (kotlin.math.abs(uVel) > 0.1f) {
                                    val u0 = kotlin.math.ln(zoom)
                                    flingJob[0]?.cancel()
                                    flingJob[0] = scope.launch {
                                        var zPrev = zoom
                                        val a = Animatable(u0)
                                        a.updateBounds(0f, kotlin.math.ln(4f))
                                        val r = a.animateDecay(uVel, exponentialDecay<Float>(frictionMultiplier = 1.8f)) {
                                            val zNew = kotlin.math.exp(value).coerceIn(1f, 4f)
                                            val np = zoomAt(zPrev, Offset(panX, panY), zNew, anchor, sw, sh)
                                            zoom = zNew; panX = np.x; panY = np.y; zPrev = zNew
                                        }
                                        if (!bouncedInDrag && r.endReason == androidx.compose.animation.core.AnimationEndReason.BoundReached &&
                                            kotlin.math.abs(r.endState.velocity) > 0.8f)
                                            limitBounce(impactStrength(kotlin.math.abs(r.endState.velocity) * 1000f / density))
                                        if (zoom < 1.05f) { zoom = 1f; panX = 0f; panY = 0f }
                                    }
                                    return@awaitEachGesture
                                }
                            }
                            // رجعنا تقريباً للحجم الطبيعي: نثبّته على 1 بالضبط عشان إيماءة الشاشة الكاملة ما تتعطل
                            if (dragged && zoom < 1.05f) { zoom = 1f; panX = 0f; panY = 0f }
                            if (!dragged) {
                                val z0 = zoom; val x0 = panX; val y0 = panY
                                // ضغطتين بأقصى اليمين: +10 ثواني، بأقصى اليسار: -10 ثواني (الأطراف بحسب اتجاه الصورة)
                                val rotNow = full && size.height > size.width
                                val fx = if (rotNow) second.position.y / sh else second.position.x / sw
                                val skip = if (fx > 0.75f) 1 else if (fx < 0.25f) -1 else 0
                                if (skip != 0) {
                                    player?.let { pl ->
                                        val d = pl.duration; val t = pl.currentPosition + skip * 10_000L
                                        pl.seekTo(if (d > 0L) t.coerceIn(0L, d) else t.coerceAtLeast(0L))
                                    }
                                    skipDir = skip; skipTick++
                                    buzz(hapticView, false)
                                } else if (zoom > 1.02f) {
                                    // ضغطتين وهو مقرّب: يرجع لحجمه بارتداد خفيف مع هزة
                                    buzz(hapticView, false)
                                    scope.launch {
                                        val t = Animatable(0f)
                                        t.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 380f)) {
                                            zoom = z0 + (1f - z0) * value
                                            panX = x0 * (1f - value); panY = y0 * (1f - value)
                                        }
                                        zoom = 1f; panX = 0f; panY = 0f
                                    }
                                } else {
                                    // ما عاد في تكبير بالضغطتين: ضغطتين بالنص تعامل كضغطة وحدة
                                    ui = !ui
                                }
                            }
                        }
                    }
            ) {
              Box(Modifier.fillMaxSize().graphicsLayer {
                // الفيديو يطير لبطاقة القناة: يصغر ويتحرك لمركزها، ومع السحب ينزل مع الإصبع
                val p = reveal.value.coerceIn(0f, 1f)
                val k0 = mix(origin.width.coerceAtLeast(1f) / size.width, 1f, p)
                val dy = dyNow()
                val kd = 1f - 0.2f * (dy / size.height).coerceIn(0f, 1f)
                scaleX = k0 * kd; scaleY = k0 * kd
                translationX = (origin.center.x - size.width / 2f) * (1f - p)
                translationY = (origin.center.y - size.height / 2f) * (1f - p) + dy
              }) {
                player?.let {
                    // نفس تحويل الفيديو (تدوير/تكبير/سحب) ينطبق على الإضاءة المحيطية
                    val videoXf: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit = {
                        val fp = fpNow()
                        val w = size.width; val h = size.height
                        val portrait = h > w
                        // معامل التحجيم عشان الفيديو يملأ الشاشة بعد التدوير وبالنسبة المختارة (16:9 / 4:3)
                        val b0 = baseScale(false, w, h)
                        val b1 = if (portrait) baseScale(true, w, h) else b0
                        val bx = b0.x + (b1.x - b0.x) * fp
                        val by = b0.y + (b1.y - b0.y) * fp
                        // خروج من أفقي حقيقي: الفيديو ينزل ويصغر مع الإصبع
                        val q = if (!portrait && full && fpDrag >= 0f) 1f - fp else 0f
                        val k = zoom * limScale.value * (1f - 0.15f * q)
                        scaleX = bx * k; scaleY = by * k
                        rotationZ = (if (portrait) 90f * fp else 0f) + limShake.value * 0.12f
                        translationX = panX + fsNowX() + limShake.value; translationY = panY + q * 0.4f * h + fsNowY()
                    }
                    AmbientGlow(pvRef, vAr, Modifier.graphicsLayer(videoXf))
                    Video(it, Modifier.graphicsLayer(videoXf), onView = { v -> pvRef = v })
                }
                // الأزرار تتدور مع الفيديو: نعيد ترتيبها بمقاس الشاشة بعد التدوير
                Box(Modifier.fillMaxSize().layout { m, cs ->
                    val rot = ctlRot && cs.maxHeight > cs.maxWidth
                    val w = if (rot) cs.maxHeight else cs.maxWidth
                    val h = if (rot) cs.maxWidth else cs.maxHeight
                    val pl = m.measure(Constraints.fixed(w, h))
                    layout(cs.maxWidth, cs.maxHeight) {
                        pl.placeWithLayer((cs.maxWidth - w) / 2, (cs.maxHeight - h) / 2) { rotationZ = if (rot) 90f else 0f }
                    }
                }) {
                    Box(Modifier.fillMaxSize()) {
                        error?.let {
                            Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.Center).padding(24.dp))
                        }
                        // أثر التقديم/الإرجاع بالضغطتين
                        if (skipDir != 0) Box(
                            Modifier.align(if (skipDir > 0) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 36.dp)
                                .background(Color.Black.copy(0.55f), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 10.dp)
                        ) { Text(if (skipDir > 0) "+10" else "-10", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        // مؤشر الصوت/السطوع/التقديم أثناء الإيماءات الجانبية
                        if (hud != 0) {
                            if (hud == 3) Box(
                                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp)
                                    .background(Color.Black.copy(0.6f), RoundedCornerShape(18.dp)).padding(horizontal = 18.dp, vertical = 10.dp)
                            ) { Text(hudText, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
                            else Column(
                                Modifier.align(if (hud == 1) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 22.dp)
                                    .background(Color.Black.copy(0.6f), RoundedCornerShape(26.dp)).padding(horizontal = 12.dp, vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(if (hud == 1) "🔊" else "☀", fontSize = 18.sp, color = Color.White)
                                Box(Modifier.width(6.dp).height(130.dp).background(Color.White.copy(0.28f), RoundedCornerShape(3.dp)),
                                    contentAlignment = Alignment.BottomCenter) {
                                    Box(Modifier.fillMaxWidth().fillMaxHeight(hudLevel.coerceIn(0f, 1f)).background(Color.White, RoundedCornerShape(3.dp)))
                                }
                                Text("${(hudLevel * 100).toInt()}", color = Color.White, fontSize = 12.sp)
                            }
                        }
                        // بانر: الفيديو مشغّل قبل أكثر من دقيقة، نسأل لو يبي يبدأ من البداية
                        if (resumeAsk > 0L && !full) {
                            val rbPad by animateDpAsState(if (ui) 150.dp else 28.dp, tween(260), label = "rb")
                            Row(
                                Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                                    .padding(start = 16.dp, end = 16.dp, bottom = rbPad).fillMaxWidth()
                                    .background(Color.Black.copy(0.62f), RoundedCornerShape(22.dp))
                                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("كمّلنا من ${fmtDur(resumeAsk)}", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Box(Modifier.height(40.dp).liveGlass(14.dp) { player?.seekTo(0L); resumeAsk = 0L; uiTick++ }.padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center) {
                                    Text("من البداية", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Box(Modifier.size(40.dp).liveGlass(14.dp) { resumeAsk = 0L }, contentAlignment = Alignment.Center) {
                                    Text("✕", color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }
                        if (uiAlpha > 0.01f) Box(Modifier.fillMaxSize().graphicsLayer {
                            val fp = fpNow()
                    val steady = kotlin.math.max((1f - fp * 4f).coerceIn(0f, 1f), ((fp - 0.75f) * 4f).coerceIn(0f, 1f))
                    val dd = if (flying) 0f else (1f - dyNow() / (size.height * 0.1f)).coerceIn(0f, 1f)
                    alpha = uiAlpha * ((reveal.value - 0.6f) / 0.4f).coerceIn(0f, 1f) * steady * dd * (1f - kotlin.math.sqrt(fsNowX() * fsNowX() + fsNowY() * fsNowY()) / (size.height * 0.1f)).coerceIn(0f, 1f)
                        }) {
                            Row(
                                Modifier.align(Alignment.TopStart).graphicsLayer { translationY = -(1f - uiAlpha) * 28.dp.toPx() }
                                    .fillMaxWidth().statusBarsPadding().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassIcon(IBack, Modifier.width(64.dp)) { close() }
                                Text(cleanName(c.name), color = Color.White, maxLines = 1, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                // ثلاث نقاط: الإيماءات (خيار وحد)، السرعة، وضع الخروج (PiP / صوت بالخلفية)، نسبة الشاشة
                                Box {
                                    GlassButton("⋮", Modifier.width(64.dp)) { uiTick++; buzz(hapticView, false); menuPage = 0; menu = true }
                                    val vis = remember { androidx.compose.animation.core.MutableTransitionState(false) }
                                    vis.targetState = menu
                                    if (menu || vis.currentState || !vis.isIdle) androidx.compose.ui.window.Popup(
                                        alignment = Alignment.TopEnd,
                                        offset = androidx.compose.ui.unit.IntOffset(0, with(androidx.compose.ui.platform.LocalDensity.current) { 60.dp.roundToPx() }),
                                        onDismissRequest = { menu = false },
                                        properties = androidx.compose.ui.window.PopupProperties(focusable = true)
                                    ) {
                                        androidx.compose.animation.AnimatedVisibility(
                                            visibleState = vis,
                                            enter = androidx.compose.animation.fadeIn(tween(140)) + androidx.compose.animation.scaleIn(
                                                tween(200), initialScale = 0.85f,
                                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)),
                                            exit = androidx.compose.animation.fadeOut(tween(120)) + androidx.compose.animation.scaleOut(
                                                tween(150), targetScale = 0.85f,
                                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f))
                                        ) {
                                            Row(
                                                Modifier.background(Color.Black.copy(0.55f), RoundedCornerShape(26.dp)).glass(26.dp).padding(6.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (menuPage == 0) {
                                                    MenuIcon(ITouch, "الإيماءات", on = swipeOn) { buzz(hapticView, false); swipeOn = !swipeOn; store.setSwipeGestures(swipeOn); menu = false }
                                                    MenuIcon(ISpeed, "السرعة", on = speed != 1f) { buzz(hapticView, false); menuPage = 1 }
                                                    MenuIcon(if (pipMode) IPip else IHead, if (pipMode) "نافذة عائمة" else "صوت بالخلفية", on = true) { buzz(hapticView, false); pipMode = !pipMode; store.setPipMode(pipMode); menu = false }
                                                } else {
                                                    MenuIcon(ISpeed, "السرعة", on = false) { buzz(hapticView, false); menuPage = 0 }
                                                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                                                        Box(
                                                            Modifier.height(44.dp).widthIn(min = 44.dp)
                                                                .background(if (sp == speed) Color.White.copy(0.22f) else Color.Transparent, RoundedCornerShape(20.dp))
                                                                .clip(RoundedCornerShape(20.dp))
                                                                .clickable { buzz(hapticView, false); speed = sp; player?.setPlaybackSpeed(sp); menu = false }
                                                                .padding(horizontal = 8.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) { Text(fmtSpeed(sp), color = Color.White, fontSize = 13.sp, fontWeight = if (sp == speed) FontWeight.Bold else FontWeight.Normal, maxLines = 1) }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Column(
                                Modifier.align(Alignment.BottomCenter).graphicsLayer { translationY = (1f - uiAlpha) * 28.dp.toPx() }
                                    .fillMaxWidth().navigationBarsPadding().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                            player?.let { GlassSeek(it, { uiTick++ }) }
                            }
                            // زر التشغيل/الإيقاف بمنتصف الشاشة
                            PlayButton(playing, Modifier.align(Alignment.Center), if (winPortraitNow) 62.dp else 88.dp) {
                                buzz(hapticView, false)
                                uiTick++
                                player?.let { if (it.isPlaying) it.pause() else it.play() }
                            }
                        }

                    }
                }
              }
            }
        }
        }
    }


    if (showSettings) SettingsDialog(
        mode = themeMode, style = themeStyle,
        onMode = { onTheme(it, themeStyle) }, onStyle = { onTheme(themeMode, it) },
        hapAll = hapAll, hapList = hapList, hapVideo = hapVideo,
        hapScroll = hapScroll,
        onHapAll = { v ->
            hapAll = v; Hap.all = v; store.setHap("hap_all", v)
            // لو شغّلت العام والفرعيين كلهم مطفيين، نرجّعهم يشتغلون
            if (v && !hapList && !hapVideo && !hapScroll) {
                hapList = true; hapVideo = true; hapScroll = true
                Hap.list = true; Hap.video = true; Hap.scroll = true
                store.setHap("hap_list", true); store.setHap("hap_video", true); store.setHap("hap_scroll", true)
            }
            if (v) buzz(hapticView, false)
        },
        onHapList = { v ->
            hapList = v; Hap.list = v; store.setHap("hap_list", v)
            // لو انطفت الثلاثة الفرعية ينطفي العام تلقائياً
            if (!v && !hapVideo && !hapScroll) { hapAll = false; Hap.all = false; store.setHap("hap_all", false) }
            else if (v) buzz(hapticView, false)
        },
        onHapVideo = { v ->
            hapVideo = v; Hap.video = v; store.setHap("hap_video", v)
            if (!v && !hapList && !hapScroll) { hapAll = false; Hap.all = false; store.setHap("hap_all", false) }
        },
        onHapScroll = { v ->
            hapScroll = v; Hap.scroll = v; store.setHap("hap_scroll", v)
            if (!v && !hapList && !hapVideo) { hapAll = false; Hap.all = false; store.setHap("hap_all", false) }
            else if (v) scrollTick(hapticView)
        },
        onAddLink = { showSettings = false; showAdd = true },
        onOpenFile = { showSettings = false; pickLauncher.launch(arrayOf("video/*")) },
        onLogs = { pullLogs() },
        onClose = { showSettings = false }
    )

    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false },
        title = { Text("فتح رابط فيديو") },
        text = {
            TextField(value = url, onValueChange = { url = it }, singleLine = true,
                placeholder = { Text("رابط فيديو مباشر") })
        },
        confirmButton = { TextButton(onClick = { addUrl() }) { Text("تشغيل") } },
        dismissButton = { TextButton(onClick = { showAdd = false }) { Text("إلغاء") } }
    )
}
