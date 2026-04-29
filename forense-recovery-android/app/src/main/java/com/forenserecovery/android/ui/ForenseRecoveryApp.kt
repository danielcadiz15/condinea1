package com.forenserecovery.android.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forenserecovery.android.ui.screens.MainScreen
import com.forenserecovery.android.ui.viewmodel.MainViewModel
import com.forenserecovery.android.ui.viewmodel.MonetizationViewModel

@Composable
fun ForenseRecoveryApp(
    viewModel: MainViewModel = viewModel(),
    monetizationViewModel: MonetizationViewModel = viewModel()
) {
    MainScreen(
        viewModel = viewModel,
        monetizationViewModel = monetizationViewModel
    )
}
