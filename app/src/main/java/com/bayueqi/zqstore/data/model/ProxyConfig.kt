package com.bayueqi.zqstore.data.model

sealed class ProxyConfig {
    data object None : ProxyConfig()
    data object System : ProxyConfig()
}
