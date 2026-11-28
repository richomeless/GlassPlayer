package com.glass.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.session.MediaSessionService

// نخفي السابق/التالي من التحكم عشان يحل محلهم زرّي تقديم/ترجيع 10 ثواني في الإشعار
private class NoSkipPlayer(p: Player) : ForwardingPlayer(p) {
    private val hidden = intArrayOf(
        Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
    )
    override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon().removeAll(*hidden).build()
    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)
}

private const val ACT_BACK = "glass.back10"
private const val ACT_FWD = "glass.fwd10"

// خدمة التشغيل: المشغّل يعيش هنا عشان الصوت يكمل لما التطبيق يروح للخلفية، وعشان إشعار التحكم
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var focusReq: AudioFocusRequest? = null
    private var pausedByFocus = false
    private var ducked = false

    override fun onCreate() {
        super.onCreate()
        // الروابط: نسمح بالتحويل بين http و https، ومهلة أطول، ونفس هوية المتصفح (بعض السيرفرات ترفض غيرها)
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setUserAgent("Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36")
        // لو الديكودر الأساسي فشل (دقة 2K/4K أو ترميز ثقيل) يجرب ديكودر ثاني بدل ما يفشل التشغيل
        val renderers = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        val player = ExoPlayer.Builder(this, renderers, DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                false   // نتولى الفوكس بأنفسنا: لو النظام/الشيزوكو منع الفوكس (Mixed Audio) الفيديو يشتغل عادي بدل ما يتوقف
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        // الفوكس اليدوي: نطلبه لما يبدأ التشغيل ونتجاهل الرفض (يعني تشغيل مختلط)، ونتجاوب مع فقدانه لو انمنح
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val listener = AudioManager.OnAudioFocusChangeListener { c ->
            when (c) {
                AudioManager.AUDIOFOCUS_LOSS -> { pausedByFocus = false; player.pause() }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { if (player.playWhenReady) { pausedByFocus = true; player.pause() } }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { ducked = true; player.volume = 0.25f }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    if (ducked) { ducked = false; player.volume = 1f }
                    if (pausedByFocus) { pausedByFocus = false; player.play() }
                }
                else -> {}
            }
        }
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                try {
                    if (playWhenReady) {
                        if (focusReq == null) focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE).build())
                            .setOnAudioFocusChangeListener(listener).build()
                        am.requestAudioFocus(focusReq!!)   // النتيجة نتجاهلها عمداً
                    } else if (!pausedByFocus) {
                        focusReq?.let { am.abandonAudioFocusRequest(it) }
                    }
                } catch (_: Throwable) {}
            }
        })
        // الضغط على الإشعار يفتح الفيديو الشغال
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .putExtra("open_player", true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val cmdBack = SessionCommand(ACT_BACK, Bundle.EMPTY)
        val cmdFwd = SessionCommand(ACT_FWD, Bundle.EMPTY)
        val layout = ImmutableList.of(
            CommandButton.Builder().setDisplayName("رجوع 10 ثواني").setIconResId(R.drawable.ic_back10).setSessionCommand(cmdBack).build(),
            CommandButton.Builder().setDisplayName("تقديم 10 ثواني").setIconResId(R.drawable.ic_fwd10).setSessionCommand(cmdFwd).build()
        )
        val cb = object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(cmdBack).add(cmdFwd).build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(cmds).build()
            }
            override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo,
                                         customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                val p = session.player
                when (customCommand.customAction) {
                    ACT_BACK -> p.seekTo((p.currentPosition - 10_000L).coerceAtLeast(0L))
                    ACT_FWD -> {
                        val d = p.duration
                        val t = p.currentPosition + 10_000L
                        p.seekTo(if (d != C.TIME_UNSET) t.coerceAtMost(d) else t)
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }
        session = MediaSession.Builder(this, NoSkipPlayer(player)).setSessionActivity(open).setCallback(cb).setCustomLayout(layout).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    // سحبت التطبيق من الأخيرة: نوقف التشغيل ونقفل الخدمة، ما يكمل أي فيديو بعد الإغلاق
    override fun onTaskRemoved(rootIntent: Intent?) {
        session?.player?.let { it.stop(); it.clearMediaItems() }
        stopSelf()
    }

    override fun onDestroy() {
        try { focusReq?.let { (getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(it) } } catch (_: Throwable) {}
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
