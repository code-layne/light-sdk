package com.thelightphone.sdk.server

/**
 * In-memory store of push registrations.
 * Maps token -> (packageName, endpoint).
 */
object LightPushRegistry {

    private const val TAG = "LightPushRegistry"

    data class Registration(
        val packageName: String,
        val endpoint: String,
        val channel: String? = null,
        val vapid: String? = null,
    )

    private val registrations = mutableMapOf<String, Registration>()

    fun register(token: String, packageName: String, endpoint: String, channel: String? = null, vapid: String? = null) {
        registrations[token] = Registration(packageName, endpoint, channel, vapid)
    }

    fun remove(token: String): Registration? {
        return registrations.remove(token)
    }

    fun get(token: String): Registration? {
        return registrations[token]
    }

    fun getAll(): Map<String, Registration> {
        return registrations.toMap()
    }
}
