package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.graphics.toColorInt

object InAppBrowser {
    fun openUrl(context: Context, url: String) {
        if (url.isBlank()) return
        
        val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }

        try {
            val darkParams = CustomTabColorSchemeParams.Builder()
                .setToolbarColor("#121212".toColorInt())
                .setSecondaryToolbarColor("#000000".toColorInt())
                .setNavigationBarColor("#000000".toColorInt())
                .build()

            val customTabsIntent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(darkParams)
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .build()

            customTabsIntent.launchUrl(context, Uri.parse(formattedUrl))
        } catch (e: Exception) {
            // Fallback to default system intent if Custom Tabs fails
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl))
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }
}
