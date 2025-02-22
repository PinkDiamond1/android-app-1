package net.ivpn.core.common.utils;

/*
 IVPN Android app
 https://github.com/ivpn/android-app

 Created by Oleksandr Mykhailenko.
 Copyright (c) 2020 Privatus Limited.

 This file is part of the IVPN Android app.

 The IVPN Android app is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as published by the Free
 Software Foundation, either version 3 of the License, or (at your option) any later version.

 The IVPN Android app is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 details.

 You should have received a copy of the GNU General Public License
 along with the IVPN Android app. If not, see <https://www.gnu.org/licenses/>.
*/

import android.content.res.Resources;
import android.text.Html;
import android.text.Spanned;
import android.util.Patterns;

import net.ivpn.core.IVPNApplication;
import net.ivpn.core.R;

import java.util.regex.Matcher;

public class StringUtil {
    public static String getLocationFromGateway(String gateway) {
        String[] gatewayParts = gateway.split("\\.");
        return gatewayParts[0];
    }

    public static boolean validateUsername(String login) {
        if (login == null || login.isEmpty()) return false;
        Matcher matcher = Patterns.EMAIL_ADDRESS.matcher(login);
        return matcher.matches();
    }

    public static Spanned fromHtml(String unspanned){
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            return Html.fromHtml(unspanned, Html.FROM_HTML_MODE_LEGACY);
        } else {
            return Html.fromHtml(unspanned);
        }
    }

    public static String formatWifiSSID(String dirtyWifi) {
        if (dirtyWifi == null || dirtyWifi.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder(dirtyWifi);

        if (builder.charAt(0) == '\"') {
            builder.deleteCharAt(0);
        }
        if (builder.charAt(builder.length() - 1) == '\"') {
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder.toString();
    }

    public static String formatTimeUntilResumedWithText(long timeUntilResumed) {
        Resources resources = IVPNApplication.application.getResources();
        StringBuilder builder = new StringBuilder();
        builder.append(resources.getString(R.string.connect_resumed_in));
        builder.append(" ").append(DateUtil.formatTimerCountDown(timeUntilResumed));
        return builder.toString();
    }

    public static String formatTimeUntilResumed(long timeUntilResumed) {
        StringBuilder builder = new StringBuilder();
        builder.append(DateUtil.formatTimerCountDown(timeUntilResumed));
        return builder.toString();
    }

//    public static String formatPlanName(Plan plan) {
//        if (plan == null) {
//            return "";
//        }
//        return "IVPN " + plan.toString();
//    }
}