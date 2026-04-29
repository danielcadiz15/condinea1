package com.forenserecovery.android.ui.viewmodel

import com.forenserecovery.android.domain.model.ScanMode
import com.forenserecovery.android.monetization.MonetizationProduct

data class MonetizationUiState(
    val isReady: Boolean = false,
    val isPremiumUnlocked: Boolean = false,
    val products: List<MonetizationProduct> = emptyList(),
    val showPaywall: Boolean = false,
    val pendingMode: ScanMode? = null,
    val message: String? = null
)

