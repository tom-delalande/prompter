package co.delalande.prompter.model

import kotlinx.serialization.Serializable

/**
 * Developer-only settings, persisted separately from user [Settings] so debug
 * state never leaks into the user-facing settings file.
 */
@Serializable
data class DebugSettings(
    /** Gates [co.delalande.prompter.log.Log] v/d/i output at runtime. */
    val verboseLogging: Boolean = true,
    /** When on, the teleprompter auto-advances words to simulate speech. */
    val autoAdvanceWords: Boolean = false,
)
