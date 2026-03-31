package com.example.reshqmess.model

import java.io.*
import java.util.UUID

data class SosPayload(
    val victimName: String,
    val lat: Double,
    val lng: Double,
    val message: String,
    val type: String, // "CHAT", "CRITICAL", or "AUDIO"
    val audioData: ByteArray? = null, // Fixes 'audioData' error
    val id: String = UUID.randomUUID().toString(), // Fixes 'id' error
    val time: Long = System.currentTimeMillis()
) : Serializable {

    fun toBytes(): ByteArray {
        val bos = ByteArrayOutputStream()
        val out = ObjectOutputStream(bos)
        out.writeObject(this)
        return bos.toByteArray()
    }

    companion object {
        fun fromBytes(bytes: ByteArray): SosPayload? {
            return try {
                val bis = ByteArrayInputStream(bytes)
                val `in` = ObjectInputStream(bis)
                `in`.readObject() as SosPayload
            } catch (e: Exception) {
                null
            }
        }
    }
}