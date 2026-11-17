package com.ngstudio.snowrace.ads

import com.ngstudio.snowrace.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ad integration point. Plug a real SDK (AdMob, AppLovin MAX, ...) in by implementing this
 * interface and returning it from [AdConfig.create]. Ads are NEVER shown during a race.
 */
interface AdProvider {
    val rewardedAvailable: Boolean
    fun showRewarded(placement: String, onResult: (rewarded: Boolean) -> Unit)
}

object AdConfig {
    /** Debug builds simulate rewarded ads so every ad-gated flow can be tested. */
    val mockAds: Boolean = BuildConfig.MOCK_ADS

    /**
     * Locked levels can be unlocked by watching an ad. Enabled in debug (mock ads);
     * flip to true in release once a real rewarded-ad provider is integrated.
     */
    val rewardedUnlockEnabled: Boolean = mockAds

    fun create(): AdProvider = if (mockAds) MockAdProvider() else NoAdProvider
}

/** Release default until a real SDK is added: no ads, ad buttons hidden. */
object NoAdProvider : AdProvider {
    override val rewardedAvailable: Boolean = false
    override fun showRewarded(placement: String, onResult: (Boolean) -> Unit) = onResult(false)
}

/** Debug-only fake rewarded ad: the UI shows a clearly labelled "DEBUG AD" overlay. */
class MockAdProvider : AdProvider {
    data class Request(val placement: String, val onResult: (Boolean) -> Unit)

    private val _request = MutableStateFlow<Request?>(null)
    val request: StateFlow<Request?> = _request.asStateFlow()

    override val rewardedAvailable: Boolean = true

    override fun showRewarded(placement: String, onResult: (Boolean) -> Unit) {
        _request.value = Request(placement, onResult)
    }

    fun complete(rewarded: Boolean) {
        val r = _request.value ?: return
        _request.value = null
        r.onResult(rewarded)
    }
}
