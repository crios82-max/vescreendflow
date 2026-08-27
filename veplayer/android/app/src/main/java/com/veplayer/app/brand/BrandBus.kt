package com.veplayer.app.brand

import android.content.Context
import com.veplayer.app.data.VePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live OEM brand for Compose (refresh after set_brand). */
object BrandBus {
    data class State(
        val brandId: String = "",
        val name: String = "",
        val logoPath: String = "",
        val accentArgb: Long = 0xFF2DD4BFL,
    ) {
        val hasLogo: Boolean get() = logoPath.isNotBlank()
        val displayName: String get() = name.ifBlank { brandId }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun refresh(context: Context) {
        val prefs = VePrefs(context)
        _state.value =
            State(
                brandId = prefs.brandId,
                name = prefs.brandName,
                logoPath = prefs.brandLogoPath,
                accentArgb = prefs.brandAccentArgb,
            )
    }
}
