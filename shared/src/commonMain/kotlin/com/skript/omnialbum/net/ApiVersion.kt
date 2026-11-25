package com.skript.omnialbum.net

interface ApiVersion {
    val id: String
    val isCurrent: Boolean
    val pathPrefix: String
}

class ApiVersionCatalog<V : ApiVersion>(val versions: List<V>) {
    init {
        require(versions.isNotEmpty()) { "At least one API version is required" }
        require(versions.count { it.isCurrent } == 1) { "Exactly one API version must be current" }
    }

    val current: V get() = versions.first { it.isCurrent }
    val supportedIds: List<String> get() = versions.map { it.id }

    fun isSupported(id: String): Boolean = versions.any { it.id == id }

    fun resolve(id: String?): V = versions.firstOrNull { it.id == id } ?: current

    fun requireSupported(id: String): V =
        versions.firstOrNull { it.id == id }
            ?: throw UnsupportedApiVersionException(id, supportedIds)
}

class UnsupportedApiVersionException(requested: String, supported: List<String>) :
    Exception("Unsupported API version '$requested'. Supported versions: ${supported.joinToString()}")
