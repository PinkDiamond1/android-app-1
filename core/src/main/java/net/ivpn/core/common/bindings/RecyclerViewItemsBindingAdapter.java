package net.ivpn.core.common.bindings;

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

import android.view.View;
import android.view.ViewGroup;

import androidx.databinding.BindingAdapter;
import androidx.recyclerview.widget.RecyclerView;

import net.ivpn.core.common.pinger.PingResultFormatter;
import net.ivpn.core.rest.data.model.Server;
import net.ivpn.core.v2.serverlist.fastest.FastestSettingViewAdapter;
import net.ivpn.core.v2.serverlist.fastest.OnFastestSettingChangedListener;
import net.ivpn.core.v2.splittunneling.OnApplicationItemSelectionChangedListener;
import net.ivpn.core.v2.splittunneling.SplitTunnelingRecyclerViewAdapter;
import net.ivpn.core.v2.splittunneling.items.ApplicationItem;
import net.ivpn.core.v2.network.NetworkRecyclerViewAdapter;
import net.ivpn.core.v2.serverlist.ServerBasedRecyclerViewAdapter;
import net.ivpn.core.vpn.model.WifiItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class RecyclerViewItemsBindingAdapter {

    @BindingAdapter("items")
    public static void setItems(RecyclerView recyclerView, List<Server> items) {
        ServerBasedRecyclerViewAdapter adapter = (ServerBasedRecyclerViewAdapter) recyclerView.getAdapter();
        if (adapter != null) {
            adapter.replaceData(items);
        }
    }

    @BindingAdapter("pings")
    public static void setPings(RecyclerView recyclerView, Map<Server, PingResultFormatter> pings) {
        ServerBasedRecyclerViewAdapter adapter = (ServerBasedRecyclerViewAdapter) recyclerView.getAdapter();
        if (adapter != null) {
            adapter.setPings(pings);
        }
    }

    @BindingAdapter("forbiddenItem")
    public static void setForbiddenItem(RecyclerView recyclerView, Server server) {
        ServerBasedRecyclerViewAdapter adapter = (ServerBasedRecyclerViewAdapter) recyclerView.getAdapter();
        if (adapter != null) {
            adapter.setForbiddenServer(server);
        }
    }

    @BindingAdapter("servers")
    public static void setServerItems(RecyclerView recyclerView, List<Server> servers) {
        FastestSettingViewAdapter adapter = (FastestSettingViewAdapter) recyclerView.getAdapter();
        if (adapter != null) {
            adapter.replaceData(servers);
        }
    }

    @BindingAdapter("excludedServers")
    public static void setExcludedItems(RecyclerView recyclerView, List<Server> excludedServers) {
        FastestSettingViewAdapter adapter = (FastestSettingViewAdapter) recyclerView.getAdapter();
        if (adapter != null) {
            adapter.setExcludedServers(excludedServers);
        }
    }

    @BindingAdapter({"apps", "not_allowed_apps"})
    public static void setAppsList(RecyclerView recyclerView, ArrayList<ApplicationItem> apps, ArrayList<String> notAllowedApps) {
        SplitTunnelingRecyclerViewAdapter adapter = (SplitTunnelingRecyclerViewAdapter) recyclerView.getAdapter();
        if (adapter != null) {
            adapter.setApps(apps, notAllowedApps);
        }
    }

    @BindingAdapter("selection_listener")
    public static void setSelectionChangedListener(RecyclerView recyclerView, OnApplicationItemSelectionChangedListener listener) {
        SplitTunnelingRecyclerViewAdapter adapter = (SplitTunnelingRecyclerViewAdapter) recyclerView.getAdapter();
        if (adapter != null) {
            adapter.setSelectionChangedListener(listener);
        }
    }

    @BindingAdapter("fastest_setting_listener")
    public static void setSelectionChangedListener(RecyclerView recyclerView,
                                                   OnFastestSettingChangedListener listener) {
        FastestSettingViewAdapter adapter = (FastestSettingViewAdapter) recyclerView.getAdapter();
        if (adapter != null) {
            adapter.setSelectionChangedListener(listener);
        }
    }

    @BindingAdapter("wifi_list")
    public static void setWifiList(RecyclerView recyclerView, List<WifiItem> wifiList) {
        NetworkRecyclerViewAdapter adapter = (NetworkRecyclerViewAdapter) recyclerView.getAdapter();
        if (adapter != null) {
            adapter.setWiFiList(wifiList);
        }
    }

    @BindingAdapter("android:layout_height")
    public static void setHeight(View view, boolean isMatchParent) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = isMatchParent ? MATCH_PARENT : WRAP_CONTENT;
        view.setLayoutParams(params);
    }

    @BindingAdapter("android:adapter")
    public static void setAdapter(RecyclerView view, RecyclerView.Adapter adapter) {
        view.setAdapter(adapter);
    }
}