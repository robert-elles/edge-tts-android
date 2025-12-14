package com.istomyang.edgetss.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import com.istomyang.edgetss.data.repositorySpeaker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Activity that handles ACTION_CHECK_TTS_DATA intent.
 * This is required for third-party apps to detect this TTS engine.
 */
class CheckVoiceData : Activity() {

    companion object {
        private const val TAG = "CheckVoiceData"

        // Default locales to report when no voices are configured
        // These are common Edge TTS locales
        val DEFAULT_AVAILABLE_LOCALES = arrayListOf(
            "en-US", "en-GB", "en-AU", "en-IN",
            "de-DE", "de-AT", "de-CH",
            "es-ES", "es-MX", "es-AR",
            "fr-FR", "fr-CA", "fr-BE",
            "it-IT",
            "pt-BR", "pt-PT",
            "zh-CN", "zh-TW", "zh-HK",
            "ja-JP",
            "ko-KR",
            "ru-RU",
            "ar-SA", "ar-EG",
            "hi-IN",
            "nl-NL", "nl-BE",
            "pl-PL",
            "tr-TR",
            "vi-VN",
            "th-TH",
            "id-ID",
            "sv-SE",
            "da-DK",
            "fi-FI",
            "nb-NO",
            "cs-CZ",
            "el-GR",
            "he-IL",
            "hu-HU",
            "ro-RO",
            "sk-SK",
            "uk-UA",
            "bg-BG",
            "hr-HR",
            "ms-MY",
            "ta-IN",
            "te-IN",
            "bn-IN",
            "gu-IN",
            "kn-IN",
            "ml-IN",
            "mr-IN",
            "af-ZA",
            "ca-ES",
            "fil-PH",
            "sw-KE"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "CheckVoiceData activity started")

        val result = Intent()
        val speakerRepository = applicationContext.repositorySpeaker

        // Get configured voices from the repository
        val configuredVoices = runBlocking {
            speakerRepository.getFlow().first()
        }

        Log.d(TAG, "Found ${configuredVoices.size} configured voices")

        // Build list of available voices
        // Format expected by Android: "lang-COUNTRY" or "lang-COUNTRY-variant"
        // e.g., "en-US", "de-DE", "zh-CN"
        val availableVoices = ArrayList<String>()

        if (configuredVoices.isNotEmpty()) {
            // Add all configured voice locales
            configuredVoices.forEach { voice ->
                availableVoices.add(voice.locale)
                Log.d(TAG, "Adding configured voice locale: ${voice.locale}")
            }
        } else {
            // If no voices configured, report common locales as available
            // This allows the engine to be discovered even without configuration
            availableVoices.addAll(DEFAULT_AVAILABLE_LOCALES)
            Log.d(TAG, "No configured voices, using ${DEFAULT_AVAILABLE_LOCALES.size} default locales")
        }

        Log.d(TAG, "Returning ${availableVoices.size} available voices")

        result.putStringArrayListExtra(
            TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES,
            availableVoices
        )
        result.putStringArrayListExtra(
            TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES,
            ArrayList()
        )
        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, result)

        Log.d(TAG, "CheckVoiceData activity finishing with CHECK_VOICE_DATA_PASS")

        finish()
    }
}
