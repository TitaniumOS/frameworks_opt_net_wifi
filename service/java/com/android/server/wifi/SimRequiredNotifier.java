/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wifi;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Icon;
import android.net.wifi.WifiConfiguration;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.wifi.resources.R;

import java.util.List;

/**
 * Helper class to generate SIM required notification
 *
 */
public class SimRequiredNotifier {

    private static final String TAG = "SimRequiredNotifier";
    private final WifiContext mContext;
    private final FrameworkFacade mFrameworkFacade;
    private final NotificationManager mNotificationManager;

    public SimRequiredNotifier(WifiContext context, FrameworkFacade framework) {
        mContext = context;
        mFrameworkFacade = framework;
        mNotificationManager =
                mContext.getSystemService(NotificationManager.class);
    }

    /**
     * Show notification
     */
    public void showSimRequiredNotification(WifiConfiguration config, String carrier) {
        String name;
        if (config.isPasspoint()) {
            name = config.providerFriendlyName;
        } else {
            name = config.SSID;
        }
        showNotification(name, carrier);
    }

    /**
     * Dismiss notification
     */
    public void dismissSimRequiredNotification() {
        mNotificationManager.cancel(null, SystemMessage.NOTE_ID_WIFI_SIM_REQUIRED);
    }

    private String getSettingsPackageName() {
        Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
        List<ResolveInfo> resolveInfos = mContext.getPackageManager().queryIntentActivitiesAsUser(
                intent, PackageManager.MATCH_SYSTEM_ONLY | PackageManager.MATCH_DEFAULT_ONLY,
                UserHandle.of(ActivityManager.getCurrentUser()));
        if (resolveInfos == null || resolveInfos.isEmpty()) {
            Log.e(TAG, "Failed to resolve wifi settings activity");
            return null;
        }
        // Pick the first one if there are more than 1 since the list is ordered from best to worst.
        return resolveInfos.get(0).activityInfo.packageName;
    }

    private void showNotification(String ssid, String carrier) {
        String settingsPackage = getSettingsPackageName();
        if (settingsPackage == null) return;
        Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setPackage(settingsPackage);

        String title = mContext.getResources().getString(
                R.string.wifi_sim_required_title);
        String message = mContext.getResources().getString(
                R.string.wifi_sim_required_message,
                (ssid == null ? "" : ssid),
                (carrier == null ? "" : carrier));
        Notification.Builder builder = mFrameworkFacade.makeNotificationBuilder(mContext,
                WifiService.NOTIFICATION_NETWORK_ALERTS)
                .setAutoCancel(true)
                .setShowWhen(false)
                .setLocalOnly(true)
                .setColor(mContext.getResources().getColor(
                        android.R.color.system_notification_accent_color, mContext.getTheme()))
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setSmallIcon(Icon.createWithResource(mContext.getWifiOverlayApkPkgName(),
                        R.drawable.stat_notify_wifi_in_range))
                .setContentIntent(mFrameworkFacade.getActivity(
                        mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT));
        mNotificationManager.notify(SystemMessage.NOTE_ID_WIFI_SIM_REQUIRED,
                builder.build());
    }
}
