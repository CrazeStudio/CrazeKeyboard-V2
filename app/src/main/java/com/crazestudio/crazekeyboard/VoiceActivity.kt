package com.crazestudio.crazekeyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast

class VoiceActivity : Activity() {
    companion object { var callback: ((String) -> Unit)? = null }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        try {
            startActivityForResult(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM), 7)
        } catch (e: Exception) {
            Toast.makeText(this, "No voice recognizer installed", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onActivityResult(rq: Int, rs: Int, d: Intent?) {
        super.onActivityResult(rq, rs, d)
        if (rs == RESULT_OK) d?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { callback?.invoke(it) }
        finish()
    }
}
