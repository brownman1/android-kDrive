/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.infomaniak.drive

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.facebook.stetho.Stetho
import com.infomaniak.drive.GeniusScanUtils.initGeniusScanSdk
import com.infomaniak.drive.MatomoDrive.buildTracker
import com.infomaniak.drive.data.api.ErrorCode
import com.infomaniak.drive.data.documentprovider.CloudStorageProvider.Companion.initRealm
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.data.services.MqttClientWrapper
import com.infomaniak.drive.ui.LaunchActivity
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.NotificationUtils.buildGeneralNotification
import com.infomaniak.drive.utils.NotificationUtils.initNotificationChannel
import com.infomaniak.lib.core.InfomaniakCore
import com.infomaniak.lib.core.auth.TokenInterceptorListener
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.utils.CoilUtils
import com.infomaniak.lib.core.utils.NotificationUtilsCore.Companion.pendingIntentFlags
import com.infomaniak.lib.core.utils.clearStack
import com.infomaniak.lib.login.ApiToken
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.SentryAndroidOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.matomo.sdk.Tracker
import java.util.Locale
import java.util.UUID

class MainApplication : Application(), ImageLoaderFactory {

    val matomoTracker: Tracker by lazy { buildTracker() }
    var geniusScanIsReady = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setDefaultLocal()
    }

    override fun onCreate() {
        super.onCreate()

        setDefaultLocal()

        AppCompatDelegate.setDefaultNightMode(UiSettings(this).nightMode)

        if (BuildConfig.DEBUG) {
            Stetho.initializeWithDefaults(this)
            StrictMode.setVmPolicy(
                VmPolicy.Builder().apply {
                    detectActivityLeaks()
                    detectLeakedClosableObjects()
                    detectLeakedRegistrationObjects()
                    detectFileUriExposure()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) detectContentUriWithoutPermission()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) detectCredentialProtectedWhileLocked()
                }.build()
            )
        } else {
            // For Microsoft Office app. Show File.getCloudAndFileUris()
            StrictMode.setVmPolicy(VmPolicy.Builder().build())
        }

        SentryAndroid.init(this) { options: SentryAndroidOptions ->
            // register the callback as an option
            options.beforeSend = SentryOptions.BeforeSendCallback { event: SentryEvent?, _: Any? ->
                //if the application is in debug mode discard the events
                if (BuildConfig.DEBUG) null else event
            }
        }

        runBlocking { initRealm() }

        geniusScanIsReady = initGeniusScanSdk()

        AccountUtils.reloadApp = { bundle ->
            Intent(this, LaunchActivity::class.java).apply {
                putExtras(bundle)
                clearStack()
                startActivity(this)
            }
        }

        InfomaniakCore.apply {
            init(
                appVersionCode = BuildConfig.VERSION_CODE,
                appVersionName = BuildConfig.VERSION_NAME,
                clientId = BuildConfig.CLIENT_ID,
            )
            apiErrorCodes = ErrorCode.apiErrorCodes
        }

        AccountUtils.onRefreshTokenError = refreshTokenError
        initNotificationChannel()
        HttpClient.init(tokenInterceptorListener())
        MqttClientWrapper.init(applicationContext)
    }

    private fun setDefaultLocal() = with(resources) {
        if (Locale.getDefault().language in acceptedLocale) return@with

        Locale.setDefault(defaultLocale)
        configuration.setLocale(defaultLocale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            createConfigurationContext(configuration)
        } else {
            updateConfiguration(configuration, displayMetrics)
        }
    }

    override fun newImageLoader(): ImageLoader = CoilUtils.newImageLoader(applicationContext, tokenInterceptorListener(), true)

    private val refreshTokenError: (User) -> Unit = { user ->
        val openAppIntent = Intent(this, LaunchActivity::class.java).clearStack()
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, pendingIntentFlags)
        val notificationManagerCompat = NotificationManagerCompat.from(this)
        buildGeneralNotification(getString(R.string.refreshTokenError)).apply {
            setContentIntent(pendingIntent)
            notificationManagerCompat.notify(UUID.randomUUID().hashCode(), build())
        }
        Sentry.withScope { scope ->
            scope.level = SentryLevel.ERROR
            scope.setExtra("userId", "${user.id}")
            Sentry.captureMessage("Refresh Token Error")
        }

        CoroutineScope(Dispatchers.IO).launch {
            AccountUtils.removeUserAndDeleteToken(this@MainApplication, user)
        }
    }

    private fun tokenInterceptorListener() = object : TokenInterceptorListener {
        override suspend fun onRefreshTokenSuccess(apiToken: ApiToken) {
            AccountUtils.setUserToken(AccountUtils.currentUser!!, apiToken)
        }

        override suspend fun onRefreshTokenError() {
            refreshTokenError(AccountUtils.currentUser!!)
        }

        override suspend fun getApiToken(): ApiToken {
            return AccountUtils.currentUser!!.apiToken
        }
    }

    private companion object {
        const val COIL_CACHE_DIR = "coil_cache"

        private val acceptedLocale = arrayOf("fr", "de", "it", "en", "es")
        private val defaultLocale = Locale.ENGLISH
    }
}