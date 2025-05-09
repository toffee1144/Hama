package com.example.hama2

import android.R
import android.net.Uri

/**
 * Holds a chat or image message.
 */
data class Message(
    val text: String? = null,
    val imageUri: Uri? = null,
    val isUser: Boolean
)

/**
 * Generic server response for chat/Image endpoints.
 */
data class ServerResponse(
    val response: String? = null,
    val error: String? = null
)

/**
 * Data model for chart points (hour of day, measured value).
 */
data class ChartData(
    val hour: String,
    val value: Float,
    val index: Int
)

/**
 * Request bodies for your three endpoints.
 */
data class DeviceRequest(val deviceId: String)
data class VolumeRequest(val volume: Int)
data class LuxRequest(val lux: Int)

data class ApiResponse(
    val success: Boolean,
    val message: String? = null
)
