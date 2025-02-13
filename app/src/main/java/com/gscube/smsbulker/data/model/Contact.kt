package com.gscube.smsbulker.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Contact(
    val id: String? = null,
    val phoneNumber: String,
    val name: String,
    val group: String = "",
    val variables: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable 