package com.example.hama2

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
    val hour: Float,
    val value: Float
)

/**
 * Data model for card icon.
 */
data class IconData(
    val signal: String,
    val lastUpdate: String,
    val vibration: String,
    val sunlight: String
)

/**
 * Single feature (subtitle + value + optional action button text).
 */
data class FeatureData(
    val subtitle: String,
    val value: String,
    val action: String? = null
)


/**
 * All four features, matching your `/api/features` JSON.
 */
data class FeaturesResponse(
    val volume: FeatureData,
    val luxThreshold: FeatureData,
    val autoHour: FeatureData
)

data class ToggleResponse(
    val success: Boolean,
    val message: String? = null
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
