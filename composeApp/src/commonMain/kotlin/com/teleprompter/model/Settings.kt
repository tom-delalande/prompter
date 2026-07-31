package com.teleprompter.model

import kotlinx.serialization.Serializable

@Serializable
enum class CameraMode {
    PictureInPicture, FullScreen
}

@Serializable
data class Settings(
    val defaultCameraMode: CameraMode = CameraMode.PictureInPicture
)
