package com.example.reshqmess.model

import com.google.gson.Gson
import java.nio.charset.StandardCharsets

data class SosPayload(
    val victimName: String,
    val lat: Double,
    val lng: Double,
    val message: String
) {
    // Helper: Pack object into Bytes
    fun toBytes(): ByteArray {
        return Gson().toJson(this).toByteArray(StandardCharsets.UTF_8)
    }

    // Helper: Unpack Bytes back to Object
    companion object {
        fun fromBytes(bytes: ByteArray): SosPayload? {
            return try {
                val json = String(bytes, StandardCharsets.UTF_8)
                Gson().fromJson(json, SosPayload::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}