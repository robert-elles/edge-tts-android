package com.istomyang.edgetss.service

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale
import com.istomyang.edgetss.data.LogRepository
import com.istomyang.edgetss.data.SpeakerRepository
import com.istomyang.edgetss.data.repositoryLog
import com.istomyang.edgetss.data.repositorySpeaker
import com.istomyang.edgetss.utils.Codec
import com.istomyang.tts_engine.TTS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class EdgeTTSService : TextToSpeechService() {
    companion object {
        private const val LOG_NAME = "EdgeTTSService"

        // Languages supported by Microsoft Edge TTS
        val SUPPORTED_LANGUAGES = setOf(
            "af", "am", "ar", "az", "bg", "bn", "bs", "ca", "cs", "cy",
            "da", "de", "el", "en", "es", "et", "eu", "fa", "fi", "fil",
            "fr", "ga", "gl", "gu", "he", "hi", "hr", "hu", "hy", "id",
            "is", "it", "ja", "jv", "ka", "kk", "km", "kn", "ko", "lo",
            "lt", "lv", "mk", "ml", "mn", "mr", "ms", "mt", "my", "nb",
            "ne", "nl", "pl", "ps", "pt", "ro", "ru", "si", "sk", "sl",
            "so", "sq", "sr", "su", "sv", "sw", "ta", "te", "th", "tr",
            "uk", "ur", "uz", "vi", "zh", "zu"
        )
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private lateinit var logRepository: LogRepository
    private lateinit var speakerRepository: SpeakerRepository

    private var serviceStarted = false
    private var prepared = false
    private var locale: String? = null
    private var voiceName: String? = null
    private var outputFormat: String? = null
    private var sampleRate = 24000

    private fun ensureRepositoriesInitialized() {
        if (!::speakerRepository.isInitialized) {
            val context = this.applicationContext
            logRepository = context.repositoryLog
            speakerRepository = context.repositorySpeaker
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureRepositoriesInitialized()
        serviceStarted = true

        // Load the active voice configuration
        scope.launch {
            speakerRepository.getActiveFlow().collect { voice ->
                if (voice != null) {
                    locale = voice.locale
                    voiceName = voice.name
                    outputFormat = voice.suggestedCodec
                    prepared = true
                    info("use speaker: $voiceName - $locale")
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        serviceStarted = false
        super.onDestroy()
    }

    override fun onStop() {
        // Nothing to stop - synthesis is synchronous per request
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        if (lang == null) return TextToSpeech.LANG_NOT_SUPPORTED

        val supportedLang = SUPPORTED_LANGUAGES.contains(lang.lowercase())
        if (!supportedLang) {
            return TextToSpeech.LANG_NOT_SUPPORTED
        }

        ensureRepositoriesInitialized()
        val targetLocale = if (country != null) "$lang-$country" else lang
        val voices = runBlocking {
            speakerRepository.getFlow().first()
        }
        val hasExactMatch = voices.any { it.locale.equals(targetLocale, ignoreCase = true) }
        val hasLangMatch = voices.any { it.locale.startsWith(lang, ignoreCase = true) }

        return when {
            hasExactMatch -> TextToSpeech.LANG_COUNTRY_AVAILABLE
            hasLangMatch -> TextToSpeech.LANG_AVAILABLE
            else -> TextToSpeech.LANG_AVAILABLE
        }
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        if (lang == null) return TextToSpeech.LANG_NOT_SUPPORTED

        if (!SUPPORTED_LANGUAGES.contains(lang.lowercase())) {
            return TextToSpeech.LANG_NOT_SUPPORTED
        }

        ensureRepositoriesInitialized()
        val targetLocale = if (country != null) "$lang-$country" else lang
        val voices = runBlocking {
            speakerRepository.getFlow().first()
        }
        val voice = voices.firstOrNull { it.locale.equals(targetLocale, ignoreCase = true) }
            ?: voices.firstOrNull { it.locale.startsWith(lang, ignoreCase = true) }
            ?: voices.firstOrNull()

        if (voice != null) {
            locale = voice.locale
            voiceName = voice.name
            outputFormat = voice.suggestedCodec
            prepared = true
            return TextToSpeech.LANG_AVAILABLE
        }

        return TextToSpeech.LANG_AVAILABLE
    }

    override fun onGetLanguage(): Array<String> {
        val loc = locale?.let { parseLocale(it) } ?: Locale.US
        return arrayOf(loc.language, loc.country, loc.variant)
    }

    override fun onGetVoices(): MutableList<Voice> {
        ensureRepositoriesInitialized()
        val voices = runBlocking {
            speakerRepository.getFlow().first()
        }
        Log.d(LOG_NAME, "onGetVoices: returning ${voices.size} voices")
        return voices.map { voice ->
            val loc = parseLocale(voice.locale)
            Voice(
                voice.shortName,
                loc,
                Voice.QUALITY_VERY_HIGH,
                Voice.LATENCY_HIGH,
                false,
                emptySet()
            )
        }.toMutableList()
    }

    override fun onIsValidVoiceName(voiceName: String?): Int {
        if (voiceName == null) return TextToSpeech.ERROR
        ensureRepositoriesInitialized()
        val voices = runBlocking {
            speakerRepository.getFlow().first()
        }
        return if (voices.any { it.shortName == voiceName }) {
            TextToSpeech.SUCCESS
        } else {
            TextToSpeech.ERROR
        }
    }

    override fun onGetDefaultVoiceNameFor(
        lang: String?,
        country: String?,
        variant: String?
    ): String? {
        if (lang == null) return null
        ensureRepositoriesInitialized()
        val targetLocale = if (country != null) "${lang}-${country}" else lang
        val voices = runBlocking {
            speakerRepository.getFlow().first()
        }
        Log.d(LOG_NAME, "onGetDefaultVoiceNameFor: $lang-$country, found ${voices.size} voices")
        return voices.firstOrNull { it.locale.startsWith(targetLocale, ignoreCase = true) }?.shortName
            ?: voices.firstOrNull()?.shortName
    }

    override fun onLoadVoice(voiceName: String?): Int {
        if (voiceName == null) return TextToSpeech.ERROR
        ensureRepositoriesInitialized()
        val voice = runBlocking {
            speakerRepository.getFlow().first().firstOrNull { it.shortName == voiceName }
        }
        if (voice != null) {
            locale = voice.locale
            this.voiceName = voice.name
            outputFormat = voice.suggestedCodec
            prepared = true
            info("loaded voice: $voiceName")
            return TextToSpeech.SUCCESS
        }
        return TextToSpeech.ERROR
    }

    private fun parseLocale(localeStr: String): Locale {
        val parts = localeStr.split("-")
        return when (parts.size) {
            1 -> Locale(parts[0])
            2 -> Locale(parts[0], parts[1])
            else -> Locale(parts[0], parts[1], parts.drop(2).joinToString("-"))
        }
    }

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (request == null || callback == null) {
            callback?.error()
            return
        }

        if (!prepared) {
            Log.e(LOG_NAME, "TTS not prepared - no voice configured")
            callback.error()
            return
        }

        val text = request.charSequenceText.toString()
        if (text.isBlank()) {
            callback.done()
            return
        }

        val pitch = request.pitch - 100
        val rate = request.speechRate - 100

        info("start synthesizing text: ${text.take(50)}...")

        runBlocking {
            try {
                // Create a new TTS engine for this request
                val engine = TTS()

                // Start the callback with PCM format
                val maxBufferSize = callback.maxBufferSize
                callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)

                // Create metadata for the request
                val metadata = TTS.AudioMetaData(
                    locale = locale!!,
                    voiceName = voiceName!!,
                    volume = "+0%",
                    outputFormat = outputFormat!!,
                    pitch = "${pitch}Hz",
                    rate = "${rate}%",
                )

                // Launch engine in background
                val engineJob = launch {
                    try {
                        engine.run()
                    } catch (e: Throwable) {
                        Log.e(LOG_NAME, "Engine error: ${e.message}")
                    }
                }

                // Send the text to the engine
                engine.input(text, metadata)

                // Convert engine output to Codec input format
                // Use a wrapper that tracks when we've received all audio
                var gotAudioComplete = false

                val codecInput = flow {
                    engine.output().takeWhile { frame ->
                        when {
                            frame.audioCompleted -> {
                                emit(Codec.Frame(null, endOfFrame = true))
                                gotAudioComplete = true
                                false // Stop collecting
                            }
                            frame.textCompleted -> {
                                Log.d(LOG_NAME, "Text accepted by engine")
                                true // Continue collecting
                            }
                            frame.data != null -> {
                                emit(Codec.Frame(frame.data))
                                true // Continue collecting
                            }
                            else -> true
                        }
                    }.collect { /* takeWhile handles the logic */ }
                }

                // Decode MP3 to PCM and send to callback
                val codec = Codec(codecInput, applicationContext)

                codec.run(Dispatchers.IO).takeWhile { frame ->
                    if (frame.endOfFrame) {
                        false // Stop collecting
                    } else {
                        frame.data?.let { pcmData ->
                            // Write PCM data to callback in chunks
                            var offset = 0
                            while (offset < pcmData.size) {
                                val chunkSize = minOf(maxBufferSize, pcmData.size - offset)
                                val result = callback.audioAvailable(pcmData, offset, chunkSize)
                                if (result != TextToSpeech.SUCCESS) {
                                    Log.e(LOG_NAME, "Failed to write audio data")
                                    break
                                }
                                offset += chunkSize
                            }
                        }
                        true // Continue collecting
                    }
                }.collect { /* takeWhile handles the logic */ }

                // Clean up
                engine.close()
                engineJob.cancel()

                callback.done()
                info("synthesis completed for: ${text.take(30)}...")

            } catch (e: Throwable) {
                Log.e(LOG_NAME, "Synthesis error: ${e.message}", e)
                callback.error()
            }
        }
    }

    private fun info(message: String) {
        Log.i(LOG_NAME, message)
        if (::logRepository.isInitialized) {
            logRepository.info(LOG_NAME, message)
        }
    }
}
