package co.delalande.prompter.log

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

fun initLogging() {
    Napier.base(DebugAntilog(defaultTag = "Teleprompter"))
}
