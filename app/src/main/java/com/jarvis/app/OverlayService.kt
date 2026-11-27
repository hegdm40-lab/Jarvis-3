package com.jarvis.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.abs

class OverlayService : Service(), TextToSpeech.OnInitListener {

    private lateinit var wm: WindowManager
    private lateinit var bubble: TextView
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        tts = TextToSpeech(this, this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addBubble()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("tr", "TR")
            ttsReady = true
        }
    }

    private fun startAsForeground() {
        val channel = "jarvis"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channel, "Jarvis", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, channel)
            .setContentTitle("Jarvis çalışıyor")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, n)
        }
    }

    private fun addBubble() {
        bubble = TextView(this).apply {
            text = "J"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1565C0"))
            }
        }
        val size = (64 * resources.displayMetrics.density).toInt()
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 300
        }

        var sx = 0
        var sy = 0
        var tx = 0f
        var ty = 0f
        var moved = false
        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = lp.x; sy = lp.y
                    tx = e.rawX; ty = e.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt()
                    val dy = (e.rawY - ty).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    lp.x = sx + dx
                    lp.y = sy + dy
                    wm.updateViewLayout(bubble, lp)
                }
                MotionEvent.ACTION_UP -> if (!moved) onTap()
            }
            true
        }
        wm.addView(bubble, lp)
    }

    private fun setState(s: String) {
        bubble.text = s
    }

    private fun onTap() {
        if (listening) return
        tts?.stop()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Ses tanıma bu telefonda yok.", Toast.LENGTH_LONG).show()
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                listening = false
                setState("J")
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val q = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (q.isNullOrBlank()) {
                    setState("J")
                } else {
                    handleQuestion(q)
                }
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
        }
        listening = true
        setState("🎤")
        recognizer?.startListening(intent)
    }

    private fun handleQuestion(q: String) {
        setState("…")
        val key = getSharedPreferences("jarvis", MODE_PRIVATE).getString("key", "") ?: ""
        Thread {
            val answer = Gemini.ask(key, q)
            handler.post {
                setState("J")
                Toast.makeText(this, answer, Toast.LENGTH_LONG).show()
                if (ttsReady) tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
            }
        }.start()
    }

    override fun onDestroy() {
        try { wm.removeView(bubble) } catch (_: Exception) {}
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
