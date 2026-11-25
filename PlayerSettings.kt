package com.example.archiveplayer

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.playerSettings by preferencesDataStore(name = "player_settings")

class PlayerSettings(private val context: Context) {
    companion object {
        val SPEED = floatPreferencesKey("speed")
        val PITCH = floatPreferencesKey("pitch")
        val CRT = booleanPreferencesKey("crt")
        val SHUFFLE = booleanPreferencesKey("shuffle")
        val REPEAT = booleanPreferencesKey("repeat")
        val BASS = intPreferencesKey("bass")
        val REVERB = intPreferencesKey("reverb")
    }

    val settings = context.playerSettings.data.map { p ->
        PlayerSettingsState(
            speed = p[SPEED] ?: 1f,
            pitch = p[PITCH] ?: 1f,
            crt = p[CRT] ?: false,
            shuffle = p[SHUFFLE] ?: false,
            repeat = p[REPEAT] ?: false,
            bass = p[BASS] ?: 0,
            reverb = p[REVERB] ?: 0
        )
    }

    suspend fun setSpeed(v: Float) { context.playerSettings.edit { it[SPEED] = v.coerceIn(0.25f, 8f) } }
    suspend fun setPitch(v: Float) { context.playerSettings.edit { it[PITCH] = v.coerceIn(0.5f, 2f) } }
    suspend fun setCrt(v: Boolean) { context.playerSettings.edit { it[CRT] = v } }
    suspend fun setShuffle(v: Boolean) { context.playerSettings.edit { it[SHUFFLE] = v } }
    suspend fun setRepeat(v: Boolean) { context.playerSettings.edit { it[REPEAT] = v } }
    suspend fun setBass(v: Int) { context.playerSettings.edit { it[BASS] = v.coerceIn(0, 12) } }
    suspend fun setReverb(v: Int) { context.playerSettings.edit { it[REVERB] = v.coerceIn(0, 12) } }
}

data class PlayerSettingsState(
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val crt: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Boolean = false,
    val bass: Int = 0,
    val reverb: Int = 0
)
