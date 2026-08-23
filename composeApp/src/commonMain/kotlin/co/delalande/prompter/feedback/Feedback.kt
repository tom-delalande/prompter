package co.delalande.prompter.feedback

const val FEEDBACK_EMAIL_ADDRESS = "tom@delalande.co"

enum class FeedbackCategory(val label: String) {
    BugReport("Bug Report"),
    FeatureRequest("Feature Request"),
    General("General Feedback");

    val emailSubject: String get() = "Prompter: $label"
}
