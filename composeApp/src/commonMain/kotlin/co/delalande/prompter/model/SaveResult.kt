package co.delalande.prompter.model

data class SaveResult(
    val uri: String? = null,
    val error: String? = null
) {
    val isSuccess: Boolean get() = uri != null
}
