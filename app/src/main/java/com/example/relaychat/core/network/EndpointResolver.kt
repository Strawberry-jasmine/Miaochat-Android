package com.example.relaychat.core.network

import java.net.URI

object EndpointResolver {
    fun buildUrl(baseUrl: String, path: String): String? {
        val trimmedBase = baseUrl.trim()
        if (trimmedBase.isEmpty()) {
            return null
        }

        val baseUri = runCatching { URI(trimmedBase) }.getOrNull() ?: return null
        val baseParts = baseUri.path.split('/').filter { it.isNotBlank() }
        val pathParts = path.split('/').filter { it.isNotBlank() }
        val combined = combinePathSegments(baseParts = baseParts, pathParts = pathParts)
        val resolvedPath = if (combined.isEmpty()) "/" else "/" + combined.joinToString("/")

        return runCatching {
            URI(
                baseUri.scheme,
                baseUri.userInfo,
                baseUri.host,
                baseUri.port,
                resolvedPath,
                baseUri.query,
                baseUri.fragment,
            ).toString()
        }.getOrNull()
    }

    fun buildDisplayString(baseUrl: String, path: String): String =
        buildUrl(baseUrl = baseUrl, path = path) ?: "Invalid endpoint"

    private fun combinePathSegments(
        baseParts: List<String>,
        pathParts: List<String>,
    ): List<String> {
        if (pathParts.isEmpty()) {
            return baseParts
        }
        if (baseParts.isEmpty()) {
            return pathParts
        }
        if (pathParts.size >= baseParts.size && pathParts.subList(0, baseParts.size) == baseParts) {
            return pathParts
        }

        val overlap = overlappingSegmentCount(baseParts, pathParts)
        return baseParts + pathParts.drop(overlap)
    }

    private fun overlappingSegmentCount(
        baseParts: List<String>,
        pathParts: List<String>,
    ): Int {
        val maxOverlap = minOf(baseParts.size, pathParts.size)
        for (count in maxOverlap downTo 1) {
            val baseSuffix = baseParts.takeLast(count).map { it.lowercase() }
            val pathPrefix = pathParts.take(count).map { it.lowercase() }
            if (baseSuffix == pathPrefix) {
                return count
            }
        }
        return 0
    }
}
