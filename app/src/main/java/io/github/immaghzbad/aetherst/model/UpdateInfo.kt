package io.github.immaghzbad.aetherst.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UpdateInfo(
    @field:Json(name = "version") val version: String,
    @field:Json(name = "version_code") val versionCode: Int,
    @field:Json(name = "is_beta") val isBeta: Boolean,
    @field:Json(name = "changelog") val changelog: String,
    @field:Json(name = "release_url") val releaseUrl: String
)
