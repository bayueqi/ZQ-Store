package com.bayueqi.zqstore.data.api

import android.content.Context
import android.content.SharedPreferences
import com.bayueqi.zqstore.data.model.ProxyConfig

object ProxyManager {
    private const val PREFS_NAME = "proxy_settings"
    private const val KEY_PROXY_TYPE = "proxy_type"
    private const val TYPE_NONE = "none"
    private const val TYPE_SYSTEM = "system"
    
    private var currentProxyConfig: ProxyConfig = ProxyConfig.None
    
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val proxyType = prefs.getString(KEY_PROXY_TYPE, TYPE_NONE)
        currentProxyConfig = when (proxyType) {
            TYPE_SYSTEM -> ProxyConfig.System
            else -> ProxyConfig.None
        }
    }
    
    fun getCurrentProxy(): ProxyConfig {
        return currentProxyConfig
    }
    
    fun setNoProxy(context: Context) {
        currentProxyConfig = ProxyConfig.None
        saveProxyType(context, TYPE_NONE)
    }
    
    fun setSystemProxy(context: Context) {
        currentProxyConfig = ProxyConfig.System
        saveProxyType(context, TYPE_SYSTEM)
    }
    
    private fun saveProxyType(context: Context, type: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PROXY_TYPE, type).apply()
    }
}
