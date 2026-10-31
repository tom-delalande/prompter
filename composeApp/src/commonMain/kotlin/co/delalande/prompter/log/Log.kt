package co.delalande.prompter.log

import io.github.aakira.napier.Napier
import kotlin.concurrent.Volatile

/**
 * App-wide logging facade. Backed by Napier so logs from commonMain land in
 * logcat on Android and the Xcode console on iOS.
 *
 * Levels:
 * - [v]/[d]/[i] are gated by [verboseEnabled] and stripped entirely from
 *   release builds via R8 `assumenosideeffects` rules.
 * - [w]/[e] always log (unless R8 decides otherwise) so real problems remain
 *   visible in production.
 *
 * Messages are lazy lambdas so the interpolation is skipped when the level is
 * not emitted.
 */
object Log {

    /** When false, [v], [d] and [i] become no-ops at runtime (debug builds only). */
    @Volatile
    var verboseEnabled: Boolean = true

    fun v(tag: String, message: () -> String) {
        if (verboseEnabled) Napier.v(tag = tag) { message() }
    }

    fun d(tag: String, message: () -> String) {
        if (verboseEnabled) Napier.d(tag = tag) { message() }
    }

    fun i(tag: String, message: () -> String) {
        if (verboseEnabled) Napier.i(tag = tag) { message() }
    }

    fun w(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (throwable != null) Napier.w(tag = tag, throwable = throwable) { message() }
        else Napier.w(tag = tag) { message() }
    }

    fun e(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (throwable != null) Napier.e(tag = tag, throwable = throwable) { message() }
        else Napier.e(tag = tag) { message() }
    }
}
