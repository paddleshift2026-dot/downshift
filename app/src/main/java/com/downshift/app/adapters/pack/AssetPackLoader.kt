package com.downshift.app.adapters.pack

import android.content.Context
import android.util.Log
import com.downshift.app.core.audio.WavLoader
import com.downshift.app.core.build.EngineId
import com.downshift.app.core.engine.Engines
import com.downshift.app.core.pack.ClipCodec
import com.downshift.app.core.pack.EnginePackManifest
import com.downshift.app.core.pack.LoadedPack
import com.downshift.app.core.pack.PackRules
import kotlinx.serialization.json.Json
import java.io.FileNotFoundException

/**
 * Loads assets/engines/<engine>/ (manifest.json plus .dsa or .wav clips, built by tools/pack_builder.py).
 * Licensed packs are not in git, so a missing or broken pack is normal: it logs why and returns null,
 * and the caller falls back to SynthPack.
 */
object AssetPackLoader {
    private const val TAG = "DownshiftPack"
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context, engine: EngineId): LoadedPack? {
        val dir = "engines/${engine.name.lowercase()}"
        val assets = context.assets
        val text = try {
            assets.open("$dir/manifest.json").bufferedReader().use { it.readText() }
        } catch (e: FileNotFoundException) {
            Log.i(TAG, "no pack in assets/$dir, using the synth pack")
            return null
        }
        return try {
            val m = json.decodeFromString(EnginePackManifest.serializer(), text)
            val problems = PackRules.problems(m)
            if (problems.isNotEmpty()) {
                Log.w(TAG, "assets/$dir rejected: ${problems.joinToString("; ")}")
                return null
            }
            val clips = LinkedHashMap<String, FloatArray>()
            for (c in m.clips) {
                val bytes = assets.open("$dir/${c.file}").use { it.readBytes() }
                val wav = if (c.file.endsWith(".dsa")) ClipCodec.decode(c.name, bytes) else WavLoader.load(bytes)
                require(wav.sampleRate == m.sampleRate) { "${c.file} is ${wav.sampleRate} Hz but the manifest says ${m.sampleRate}" }
                clips[c.name] = wav.mono
            }
            Log.i(TAG, "loaded assets/$dir: ${clips.size} clips from ${m.source}")
            LoadedPack(engine, m.sampleRate, clips, PackRules.voicing(m, Engines.spec(engine)), m.source)
        } catch (e: Exception) {
            Log.w(TAG, "assets/$dir unusable: ${e.message}")
            null
        }
    }
}
