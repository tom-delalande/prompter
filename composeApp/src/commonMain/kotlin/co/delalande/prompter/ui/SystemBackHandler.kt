package co.delalande.prompter.ui

import androidx.compose.runtime.Composable

@Composable
expect fun SystemBackHandler(onBack: () -> Unit)
