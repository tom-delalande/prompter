package com.teleprompter.model

import kotlinx.serialization.Serializable

@Serializable
enum class CameraMode {
    PictureInPicture, FullScreen
}

@Serializable
data class TextDisplayStyle(
    val fontSizeSp: Float = 42f,
    val textColorHex: String = "#CCCCCC",
    val borderWidthDp: Float = 0f,
    val borderColorHex: String = "#000000",
    val fontFamily: String = "Default"
)

@Serializable
data class Settings(
    val defaultCameraMode: CameraMode = CameraMode.PictureInPicture,
    val textDisplayStyle: TextDisplayStyle = TextDisplayStyle()
)
