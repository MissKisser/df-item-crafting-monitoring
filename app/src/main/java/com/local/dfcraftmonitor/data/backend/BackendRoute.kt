package com.local.dfcraftmonitor.data.backend

enum class BackendRouteCategory {
    PUBLIC_READ,
    AUTH_READ,
    AUTH_WRITE,
    STATIC_ASSET,
    LOGIN_AUTH,
    BLOCKED_OR_UNKNOWN,
}

data class BackendRoute(
    val id: String,
    val localRoute: String?,
    val category: BackendRouteCategory,
    val remoteEndpointId: String,
    val remoteUrl: String,
    val remoteMethods: List<String> = emptyList(),
    val query: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
)
