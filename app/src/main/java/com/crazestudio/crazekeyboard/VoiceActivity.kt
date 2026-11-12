package com.crazestudio.crazekeyboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast

// Transparent helper activity: the keyboard service cannot hold the microphone permission prompt or a SpeechRecognizer
// UI itself, so voice typing runs here and the text is handed back through a small thread-safe mailbox.
class VoiceActivity : Activity() {
    companion object {
        @Volatile var callback: (() -> Unit)? = null
        private var pending: String? = null
        @Synchronized fun post(t: String) { pending = t }
        @Synchronized fun take(): String? { val p = pending; pending = null; return p }
    }

    private val reqCode = 702
    private var recognizer: SpeechRecognizer? = null
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
        else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), reqCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != reqCode) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) start()
        else { toast("Microphone permission was not granted"); finishVoice() }
    }

    private fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { toast("No speech recognizer available on this device"); finishVoice(); return }
        try {
            val r = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { toast("Listening…") }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!text.isNullOrBlank()) post(text)
                    finishVoice()
                }
                override fun onError(error: Int) {
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> toast("Didn't catch that")
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> toast("Microphone permission is missing")
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> toast("Voice typing needs a connection for this recognizer")
                        else -> toast("Voice typing failed")
                    }
                    finishVoice()
                }
            })
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            r.startListening(i)
        } catch (e: Exception) {
            toast("Voice typing is unavailable")
            finishVoice()
        }
    }

    private fun toast(m: String) = Toast.makeText(applicationContext, m, Toast.LENGTH_SHORT).show()

    private fun finishVoice() {
        if (done) return
        done = true
        finish()
        // Give focus a moment to return to the editor, then let the service commit the text.
        Handler(Looper.getMainLooper()).postDelayed({ callback?.invoke() }, 350)
    }

    override fun onDestroy() {
        try { recognizer?.cancel(); recognizer?.destroy() } catch (e: Exception) { }
        recognizer = null
        super.onDestroy()
    }
}
