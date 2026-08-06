package com.psiphon3.azadi

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.psiphon3.BuildConfig

/**
 * Centralized manager for Firebase Analytics to ensure privacy and multi-process safety.
 */
object FirebaseAnalyticsManager {
    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun initialize(context: Context) {
        if (firebaseAnalytics == null) {
            firebaseAnalytics = Firebase.analytics
            // Default: enabled, but we can respect a user toggle here if one existed.
            firebaseAnalytics?.setAnalyticsCollectionEnabled(true)
        }
    }

    /**
     * Internal helper to log events with safety checks.
     */
    private fun logEvent(name: String, params: Bundle? = null) {
        // Multi-process safety: Only log if initialized (which only happens in main process)
        firebaseAnalytics?.logEvent(name, params)
    }

    fun logVpnConnectStarted(mode: String) {
        val bundle = Bundle().apply {
            putString("connection_mode", mode)
            putString("app_version", BuildConfig.VERSION_NAME)
        }
        logEvent("vpn_connect_started", bundle)
    }

    fun logVpnConnectSuccess(mode: String, protocol: String?) {
        val bundle = Bundle().apply {
            putString("connection_mode", mode)
            putString("protocol", protocol ?: "unknown")
        }
        logEvent("vpn_connect_success", bundle)
    }

    fun logVpnConnectFailed(mode: String, errorType: String) {
        val bundle = Bundle().apply {
            putString("connection_mode", mode)
            putString("normalized_error_type", errorType)
        }
        logEvent("vpn_connect_failed", bundle)
    }

    fun logVpnDisconnected() {
        logEvent("vpn_disconnected")
    }

    fun logConnectionModeSelected(mode: String) {
        val bundle = Bundle().apply {
            putString("connection_mode", mode)
        }
        logEvent("connection_mode_selected", bundle)
    }

    fun logServerSelectionChanged(region: String) {
        val bundle = Bundle().apply {
            putString("region", region)
        }
        logEvent("server_selection_changed", bundle)
    }

    fun logPaywallViewed(source: String) {
        val bundle = Bundle().apply {
            putString("source", source)
        }
        logEvent("paywall_viewed", bundle)
    }

    fun logPurchaseStarted(productId: String) {
        val bundle = Bundle().apply {
            putString("product_id", productId)
        }
        logEvent("purchase_started", bundle)
    }

    fun logPurchaseCompleted(productId: String) {
        val bundle = Bundle().apply {
            putString("product_id", productId)
        }
        logEvent("purchase_completed", bundle)
    }

    fun logPurchaseFailed(productId: String, error: String) {
        val bundle = Bundle().apply {
            putString("product_id", productId)
            putString("normalized_error_type", error)
        }
        logEvent("purchase_failed", bundle)
    }

    fun logRestoreStarted() {
        logEvent("restore_purchase_started")
    }

    fun logRestoreCompleted(success: Boolean) {
        val bundle = Bundle().apply {
            putBoolean("success", success)
        }
        logEvent("restore_purchase_completed", bundle)
    }
}
