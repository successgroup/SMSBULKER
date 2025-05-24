package com.gscube.smsbulker.data.model

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonClass(generateAdapter = true)
data class User(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String = "",
    @Json(name = "email") val email: String = "",
    @Json(name = "company") val company: String? = null,  // Changed from companyName to company
    @Json(name = "company_alias") val companyAlias: String = "",
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "credits") val credits: Int = 0,
    @Json(name = "is_active") val isActive: Boolean = true,
    @Json(name = "api_key") val apiKey: String = ""
) : Parcelable
