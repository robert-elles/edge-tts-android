package com.istomyang.edgetss.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech

/**
 * Activity that handles ACTION_GET_SAMPLE_TEXT intent.
 * Returns sample text for the requested language.
 */
class GetSampleText : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val result = Intent()

        // Get the requested language from the intent
        val language = intent?.getStringExtra("language") ?: "en-US"

        // Provide sample text based on language
        val sampleText = getSampleTextForLanguage(language)

        result.putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, sampleText)
        setResult(RESULT_OK, result)

        finish()
    }

    private fun getSampleTextForLanguage(language: String): String {
        return when {
            language.startsWith("en") -> "This is a sample of text to speech synthesis."
            language.startsWith("es") -> "Esta es una muestra de síntesis de texto a voz."
            language.startsWith("fr") -> "Ceci est un exemple de synthèse vocale."
            language.startsWith("de") -> "Dies ist ein Beispiel für Sprachsynthese."
            language.startsWith("it") -> "Questo è un esempio di sintesi vocale."
            language.startsWith("pt") -> "Este é um exemplo de síntese de texto para fala."
            language.startsWith("zh") -> "这是文字转语音合成的示例。"
            language.startsWith("ja") -> "これはテキスト読み上げ合成のサンプルです。"
            language.startsWith("ko") -> "이것은 텍스트 음성 합성의 샘플입니다."
            language.startsWith("ru") -> "Это пример синтеза речи."
            language.startsWith("ar") -> "هذا نموذج لتحويل النص إلى كلام."
            language.startsWith("hi") -> "यह टेक्स्ट टू स्पीच सिंथेसिस का एक नमूना है।"
            else -> "This is a sample of text to speech synthesis."
        }
    }
}
