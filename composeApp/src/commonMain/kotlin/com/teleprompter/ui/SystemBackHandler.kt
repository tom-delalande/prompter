package com.teleprompter.ui

import androidx.compose.runtime.Composable

@Composable
expect fun SystemBackHandler(onBack: () -> Unit)
