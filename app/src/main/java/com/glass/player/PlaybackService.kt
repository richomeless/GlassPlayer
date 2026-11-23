package com.glass.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

// خدمة التشغيل: المشغّل يعيش هنا عشان الصوت يكمل لما التطبيق يروح للخلفية، وعشان إشعار التحكم
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var focusReq: AudioFocusRequest? = null
    private var pausedByFocus = false
    private var ducked = false

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
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
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
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
