package com.local.dfcraftmonitor.ui.login

import android.content.Context
import com.local.dfcraftmonitor.data.model.AmsCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        context.getSharedPreferences("ams_credential_store", Context.MODE_PRIVATE)
    }

    fun load(): AmsCredential? {
        val credential = AmsCredential.create(
            openid = prefs.getString(KEY_OPENID, "").orEmpty(),
            acctype = prefs.getString(KEY_ACCTYPE, "").orEmpty(),
            appid = prefs.getString(KEY_APPID, "").orEmpty(),
            accessToken = prefs.getString(KEY_ACCESS_TOKEN, "").orEmpty(),
        )
        return credential.takeIf { it.isComplete() }
    }

    fun save(credential: AmsCredential) {
        prefs.edit()
            .putString(KEY_OPENID, credential.openid)
            .putString(KEY_ACCTYPE, credential.acctype)
            .putString(KEY_APPID, credential.appid)
            .putString(KEY_ACCESS_TOKEN, credential.accessToken)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        private const val KEY_OPENID = "openid"
        private const val KEY_ACCTYPE = "acctype"
        private const val KEY_APPID = "appid"
        private const val KEY_ACCESS_TOKEN = "access_token"
    }
}
