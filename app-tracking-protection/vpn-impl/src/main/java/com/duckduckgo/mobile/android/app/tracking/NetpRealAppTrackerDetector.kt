/*
 * Copyright (c) 2022 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.mobile.android.app.tracking

import android.content.pm.PackageManager
import android.util.LruCache
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.apps.VpnExclusionList
import com.duckduckgo.mobile.android.vpn.apps.isSystemApp
import com.duckduckgo.mobile.android.vpn.dao.VpnAppTrackerBlockingDao
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.AppNameResolver
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.AppTrackerRecorder
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerRepository
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerType
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import logcat.logcat

class NetpRealAppTrackerDetector constructor(
    private val appTrackerRepository: AppTrackerRepository,
    private val appNameResolver: AppNameResolver,
    private val appTrackerRecorder: AppTrackerRecorder,
    private val vpnAppTrackerBlockingDao: VpnAppTrackerBlockingDao,
    private val packageManager: PackageManager,
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
) : AppTrackerDetector {

    private val isAppTpDisabled: Boolean
        get() = !vpnFeaturesRegistry.isFeatureRegistered(AppTpVpnFeature.APPTP_VPN)

    // cache packageId -> app name
    private val appNamesCache = LruCache<String, AppNameResolver.OriginatingApp>(100)

    override fun evaluate(domain: String, uid: Int): AppTrackerDetector.AppTracker? {
        // Check if AppTP is enabled first
        if (isAppTpDisabled) {
            logcat { "App tracker detector is DISABLED" }
            return null
        }

        // `null` package ID means unknown app, return null to not block
        val packageId = appNameResolver.getPackageIdForUid(uid) ?: return null

        if (VpnExclusionList.isDdgApp(packageId) || packageId.isInExclusionList()) {
            logcat { "shouldAllowDomain: $packageId is excluded, allowing $domain" }
            return null
        }

        when (val type = appTrackerRepository.findTracker(domain, packageId)) {
            AppTrackerType.NotTracker -> return null
            is AppTrackerType.FirstParty -> return null
            is AppTrackerType.ThirdParty -> {
                if (isTrackerInExceptionRules(packageId = packageId, hostname = domain)) return null

                val trackingApp = appNamesCache[packageId] ?: appNameResolver.getAppNameForPackageId(packageId)
                appNamesCache.put(packageId, trackingApp)

                // if the app name is unknown, do not block
                if (trackingApp.isUnknown()) return null

                VpnTracker(
                    trackerCompanyId = type.tracker.trackerCompanyId,
                    company = type.tracker.owner.name,
                    companyDisplayName = type.tracker.owner.displayName,
                    domain = type.tracker.hostname,
                    trackingApp = TrackingApp(trackingApp.packageId, trackingApp.appName),
                ).run {
                    appTrackerRecorder.insertTracker(this)
                }
                return AppTrackerDetector.AppTracker(
                    domain = type.tracker.hostname,
                    uid = uid,
                    trackerCompanyDisplayName = type.tracker.owner.displayName,
                    trackingAppId = trackingApp.packageId,
                    trackingAppName = trackingApp.appName,
                )
            }
        }
    }

    private fun isTrackerInExceptionRules(
        packageId: String,
        hostname: String,
    ): Boolean {
        return vpnAppTrackerBlockingDao.getRuleByTrackerDomain(hostname)?.let { rule ->
            logcat { "isTrackerInExceptionRules: found rule $rule for $hostname" }
            return rule.packageNames.contains(packageId)
        } ?: false
    }

    private fun String.isInExclusionList(): Boolean {
        if (packageManager.getApplicationInfo(this, 0).isSystemApp()) {
            // if system app is NOT overridden, it means it's in the exclusion list
            if (!appTrackerRepository.getSystemAppOverrideList().map { it.packageId }.contains(this)) {
                return true
            }
        }

        return appTrackerRepository.getManualAppExclusionList().firstOrNull { it.packageId == this }?.let {
            // if app is defined as "unprotected" by the user, then it is in exclusion list
            return !it.isProtected
            // else, app is in the exclusion list
        } ?: appTrackerRepository.getAppExclusionList().map { it.packageId }.contains(this)
    }
}

// TODO - this is a temporary solution to unblock NetP. We need to spend more time thinking about how to properly share and unify the AppTrackerDetector impl
// Also this file should not be in this module, it's temporarily here until we settle on how exclusion lists should work for NetP
// decide what needs to be extracted to API modules and how AppTrackerDetector should work / be shared
@ContributesTo(
    scope = VpnScope::class,
    replaces = [AppTrackerDetectorModule::class],
)
@Module
object NetpAppTrackerDetectorModule {
    @Provides
    fun provideNetpAppTrackerDetectorModule(
        appTrackerRepository: AppTrackerRepository,
        appNameResolver: AppNameResolver,
        appTrackerRecorder: AppTrackerRecorder,
        vpnDatabase: VpnDatabase,
        packageManager: PackageManager,
        vpnFeaturesRegistry: VpnFeaturesRegistry,
    ): AppTrackerDetector {
        return NetpRealAppTrackerDetector(
            appTrackerRepository = appTrackerRepository,
            appNameResolver = appNameResolver,
            appTrackerRecorder = appTrackerRecorder,
            vpnAppTrackerBlockingDao = vpnDatabase.vpnAppTrackerBlockingDao(),
            packageManager = packageManager,
            vpnFeaturesRegistry = vpnFeaturesRegistry,
        )
    }
}
