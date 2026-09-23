package com.aistudio.escala.util

import android.content.Context
import android.content.SharedPreferences

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("escala_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_NOME_USUARIO = "key_nome_usuario"
        private const val KEY_COORDENADOR_CHAVE = "key_coordenador_chave"
        private const val KEY_THEME_MODE = "key_theme_mode"
    }

    var themeMode: ThemeMode
        get() {
            val str = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
            return try {
                ThemeMode.valueOf(str ?: ThemeMode.SYSTEM.name)
            } catch (_: Exception) {
                ThemeMode.SYSTEM
            }
        }
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.name).apply()

    var nomeUsuario: String?
        get() = prefs.getString(KEY_NOME_USUARIO, null)
        set(value) = prefs.edit().putString(KEY_NOME_USUARIO, value).apply()

    var coordenadorChave: String?
        get() = prefs.getString(KEY_COORDENADOR_CHAVE, null)
        set(value) = prefs.edit().putString(KEY_COORDENADOR_CHAVE, value).apply()

    fun limparSessaoCoordenador() {
        prefs.edit().remove(KEY_COORDENADOR_CHAVE).apply()
    }
}
