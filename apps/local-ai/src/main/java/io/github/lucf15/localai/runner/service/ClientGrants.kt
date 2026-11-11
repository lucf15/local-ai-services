package io.github.lucf15.localai.runner.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.lucf15.localai.core.sha256

data class AppIdentity(val packageName: String, val uid: Int, val certificates: String)

/** Android owns runtime grants. UID/signature identity still scopes individual operation handles. */
class ClientGrants(private val context: Context) {

    @Suppress("DEPRECATION")
    fun identity(packageName: String): AppIdentity {
        val pm = context.packageManager
        val info = pm.getPackageInfo(packageName, if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
        val uid = requireNotNull(info.applicationInfo).uid
        require(pm.getPackagesForUid(uid)?.toList() == listOf(packageName)) { "Shared UIDs are unsupported" }
        val signatures = if (Build.VERSION.SDK_INT >= 28) requireNotNull(info.signingInfo).apkContentsSigners else info.signatures
        val certificates = requireNotNull(signatures).map { sha256(it.toByteArray()) }.sorted().joinToString(":")
        require(certificates.isNotEmpty())
        return AppIdentity(packageName, uid, certificates)
    }

    fun requireAuthorized(uid: Int): AppIdentity {
        if (context.checkPermission(PERMISSION, -1, uid) != PackageManager.PERMISSION_GRANTED) throw SecurityException("CLIENT_NOT_AUTHORIZED")
        val packages = context.packageManager.getPackagesForUid(uid)
        val identity = if (packages?.size == 1) runCatching { identity(packages.single()) }.getOrNull() else null
        if (identity == null || identity.uid != uid) {
            throw SecurityException("CLIENT_NOT_AUTHORIZED")
        }
        return identity
    }
    companion object { const val PERMISSION = "io.github.lucf15.localai.runner.permission.USE_LOCAL_AI" }
}
