package com.forenserecovery.android.monetization

import android.app.Activity
import android.app.Application
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class AdsManager(
    private val application: Application
) {
    private val tag = "AdsManager"
    private var interstitialAd: InterstitialAd? = null
    private var loaded = false

    fun initialize() {
        runCatching {
            MobileAds.initialize(application)
        }.onSuccess {
            preloadInterstitial()
        }.onFailure { error ->
            Log.w(tag, "No se pudo inicializar AdMob", error)
        }
    }

    fun preloadInterstitial() {
        if (loaded) return
        runCatching {
            InterstitialAd.load(
                application,
                MonetizationConfig.interstitialAdUnitId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        loaded = true
                    }
                }
            )
        }.onFailure { error ->
            Log.w(tag, "No se pudo cargar interstitial", error)
        }
    }

    fun showInterstitialIfAvailable(activity: Activity, onDone: () -> Unit) {
        val ad = interstitialAd
        if (ad == null) {
            preloadInterstitial()
            onDone()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loaded = false
                interstitialAd = null
                preloadInterstitial()
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                loaded = false
                interstitialAd = null
                preloadInterstitial()
                onDone()
            }
        }
        ad.show(activity)
    }

    companion object
}
