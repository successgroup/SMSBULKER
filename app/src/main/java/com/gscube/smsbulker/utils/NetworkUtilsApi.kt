package com.gscube.smsbulker.utils

interface NetworkUtilsApi {
    fun isNetworkAvailable(): Boolean
    fun isConnectedToWifi(): Boolean
    fun isConnectedToCellular(): Boolean
}