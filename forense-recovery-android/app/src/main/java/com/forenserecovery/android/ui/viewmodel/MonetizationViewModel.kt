package com.forenserecovery.android.ui.viewmodel

import android.app.Application
import android.app.Activity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forenserecovery.android.ForenseRecoveryApplication
import com.forenserecovery.android.domain.model.ScanMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MonetizationViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val app = application as ForenseRecoveryApplication
    private val monetizationManager = app.monetizationManager

    private val _ui = MutableStateFlow(MonetizationUiState())
    val ui: StateFlow<MonetizationUiState> = _ui

    init {
        viewModelScope.launch {
            monetizationManager.state.collectLatest { state ->
                _ui.update {
                    it.copy(
                        isReady = state.isReady,
                        isPremiumUnlocked = state.isPremiumUnlocked,
                        products = state.products
                    )
                }
            }
        }
    }

    fun startBilling() {
        monetizationManager.start()
    }

    fun preloadAds() {
        app.adsManager.preloadInterstitial()
    }

    fun ensureAccessOrShowPaywall(mode: ScanMode): Boolean {
        if (mode == ScanMode.BASIC || _ui.value.isPremiumUnlocked) return true
        _ui.update {
            it.copy(
                showPaywall = true,
                pendingMode = mode,
                message = "El modo ${mode.name} requiere Premium."
            )
        }
        return false
    }

    fun launchPurchase(activity: Activity, productId: String): Boolean {
        val ok = monetizationManager.launchPurchase(activity, productId)
        if (!ok) _ui.update { it.copy(message = "No se pudo iniciar la compra. Reintenta.") }
        return ok
    }

    fun restorePurchases() {
        monetizationManager.restorePurchases()
        _ui.update { it.copy(message = "Restauración solicitada.") }
    }

    fun consumePendingMode(): ScanMode? {
        if (!_ui.value.isPremiumUnlocked) return null
        val mode = _ui.value.pendingMode
        _ui.update { it.copy(showPaywall = false, pendingMode = null) }
        return mode
    }

    fun dismissPaywall() {
        _ui.update { it.copy(showPaywall = false, pendingMode = null) }
    }

    fun clearMessage() {
        _ui.update { it.copy(message = null) }
    }

    fun showInterstitial(activity: Activity?, onDone: () -> Unit = {}) {
        if (activity == null || _ui.value.isPremiumUnlocked) {
            onDone()
            return
        }
        app.adsManager.showInterstitialIfAvailable(activity, onDone)
    }

    override fun onCleared() {
        super.onCleared()
        monetizationManager.stop()
    }
}
