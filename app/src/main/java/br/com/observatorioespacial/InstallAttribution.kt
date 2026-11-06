package br.com.observatorioespacial

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails

/**
 * Obtém a atribuição de instalação fornecida pela Google Play.
 *
 * Os dados são guardados apenas localmente neste aparelho. Esta classe não
 * transmite identificadores nem dados de instalação para servidores externos.
 */
object InstallAttribution {
    private const val PREFS = "play_install_attribution"

    fun capture(context: Context) {
        val appContext = context.applicationContext
        val client = InstallReferrerClient.newBuilder(appContext).build()

        client.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                try {
                    if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                        val details: ReferrerDetails = client.installReferrer
                        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit()
                            .putString("install_referrer", details.installReferrer)
                            .putLong("referrer_click_timestamp_seconds", details.referrerClickTimestampSeconds)
                            .putLong("install_begin_timestamp_seconds", details.installBeginTimestampSeconds)
                            .putLong("referrer_retrieved_at_millis", System.currentTimeMillis())
                            .apply()
                    }
                } catch (_: Exception) {
                    // A atribuição é opcional; falhas não impedem a abertura do observatório.
                } finally {
                    try { client.endConnection() } catch (_: Exception) {}
                }
            }

            override fun onInstallReferrerServiceDisconnected() {
                try { client.endConnection() } catch (_: Exception) {}
            }
        })
    }
}
