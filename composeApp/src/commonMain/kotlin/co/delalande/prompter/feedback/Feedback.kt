package co.delalande.prompter.feedback

import co.delalande.prompter.BuildInfo

val FEEDBACK_EMAIL_ADDRESS: String = BuildInfo.FEEDBACK_EMAIL_ADDRESS

enum class FeedbackCategory(val label: String) {
    BugReport("Bug Report"),
    FeatureRequest("Feature Request"),
    General("General Feedback");

    val emailSubject: String get() = "Prompter: $label"
}
