package com.gscube.smsbulker.utils

interface IPermissionManager {
    fun checkStoragePermission(): Boolean
    fun checkContactsPermission(): Boolean
    fun getRequiredPermissions(): Array<String>
}
