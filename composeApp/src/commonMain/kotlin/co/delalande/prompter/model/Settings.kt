package co.delalande.prompter.model

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
enum class VideoResolution {
    HD_720, FHD_1080, UHD_4K
}

@Serializable
enum class VideoFrameRate {
    FPS_24, FPS_30, FPS_60
}

@Serializable
data class Settings(
    val defaultCameraMode: CameraMode = CameraMode.PictureInPicture,
    val textDisplayStyle: TextDisplayStyle = TextDisplayStyle(),
    val videoResolution: VideoResolution = VideoResolution.FHD_1080,
    val videoFrameRate: VideoFrameRate = VideoFrameRate.FPS_30
)
