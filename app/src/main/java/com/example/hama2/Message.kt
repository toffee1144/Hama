package com.example.hama2

import android.net.Uri

data class Message(
    val text: String?        = null,
    val imageUri: Uri?       = null,
    val isUser: Boolean
)
