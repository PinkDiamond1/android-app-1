package net.ivpn.core.vpn.controller;

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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import net.ivpn.core.IVPNApplication;
import net.ivpn.core.common.multihop.MultiHopController;
import net.ivpn.core.common.pinger.PingProvider;
import net.ivpn.core.common.prefs.ServersRepository;
import net.ivpn.core.common.prefs.Settings;
import net.ivpn.core.common.utils.DomainResolver;
import net.ivpn.core.rest.data.model.Server;
import net.ivpn.core.rest.data.model.ServerType;
import net.ivpn.core.v2.connect.createSession.ConnectionState;
import net.ivpn.core.vpn.OnVpnStatusChangedListener;
import net.ivpn.core.vpn.ServiceConstants;
import net.ivpn.core.vpn.VPNConnectionState;
import net.ivpn.core.vpn.openvpn.IVPNService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import de.blinkt.openvpn.core.ConnectionStatus;
import de.blinkt.openvpn.core.VpnStatus;
import kotlin.jvm.Volatile;

import static net.ivpn.core.v2.connect.createSession.ConnectionState.CONNECTED;
import static net.ivpn.core.v2.connect.createSession.ConnectionState.CONNECTING;
import static net.ivpn.core.v2.connect.createSession.ConnectionState.DISCONNECTING;
import static net.ivpn.core.v2.connect.createSession.ConnectionState.NOT_CONNECTED;
import static net.ivpn.core.v2.connect.createSession.ConnectionState.PAUSED;
import static net.ivpn.core.v2.connect.createSession.ConnectionState.PAUSING;

public class OpenVpnBehavior extends VpnBehavior implements OnVpnStatusChangedListener, ServiceConstants {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenVpnBehavior.class);
    private static final String TAG = OpenVpnBehavior.class.getSimpleName();
    private static final long COMMON_TIME_OUT = 15000L;
    private static final long PORT_CHECK_TIME_OUT = 11000L;
    private static final long NO_NETWORK_TIME_OUT = 2000L;

    private ConnectionStatus status;
    private ConnectionState state;
    private final List<VpnStateListener> listeners = new ArrayList<>();
    private PauseTimer timer;

    private ServersRepository serversRepository;
    private Settings settings;
    private PingProvider pingProvider;
    private DomainResolver domainResolver;
    private BroadcastReceiver connectionStatusReceiver;
    private MultiHopController multiHopController;

    private Handler handler;
    private Runnable commonRunnable = () -> {
        onTimeOut();
        reset();
    };
    private Runnable portCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (status != null && status == ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET) {
                LOGGER.info("notifyAnotherPortUsedToConnect state = " + state);
                reset();
                tryAnotherPort();
            }
        }
    };
    private Runnable noNetworkRunnable = () -> {
        LOGGER.info("no network runnable");
        for (VpnStateListener listener : listeners) {
            listener.notifyNoNetworkConnection();
        }
        stopVpn();
        reset();
    };

    @Volatile
    private Server _fastestServer = null;
    private final Observer<Server> fastestServerObserver = server -> {
        _fastestServer = server;
    };
    private final LiveData<Server> fastestServer;

    @Inject
    OpenVpnBehavior(ServersRepository serversRepository,
                    Settings settings, PingProvider pingProvider,
                    DomainResolver domainResolver, MultiHopController multiHopController) {
        LOGGER.info("OpenVpn behaviour");
        this.serversRepository = serversRepository;
        this.settings = settings;
        this.pingProvider = pingProvider;
        this.domainResolver = domainResolver;
        this.multiHopController = multiHopController;
        handler = new Handler(Looper.myLooper());
        listeners.add(pingProvider.getVPNStateListener());

        fastestServer = pingProvider.getFastestServer();
        fastestServer.observeForever(fastestServerObserver);

        init();
    }

    private void init() {
        if (isVpnActive()) {
            state = CONNECTED;
        } else {
            state = NOT_CONNECTED;
        }
        timer = new PauseTimer(new PauseTimer.PauseTimerListener() {
            @Override
            public void onTick(long millisUntilFinished) {
                for (VpnStateListener listener : listeners) {
                    listener.onTimeTick(millisUntilFinished);
                }
            }

            @Override
            public void onFinish() {
                resume();
                for (VpnStateListener listener : listeners) {
                    listener.onTimerFinish();
                }
            }
        });
        registerReceivers();
    }

    @Override
    public void disconnect() {
        LOGGER.info("disconnect state = " + state);
        //ToDo should we use NOT_CONNECTED state too?
        if (state == CONNECTING || state == CONNECTED) {
            state = DISCONNECTING;
            sendConnectionState();
        }
        stopVpn();
    }

    @Override
    public void pause(long pauseDuration) {
        LOGGER.info("Pause, state = " + state);
        timer.startTimer(pauseDuration);
        state = PAUSING;
        sendConnectionState();
        pauseVpn(pauseDuration);
    }

    @Override
    public void resume() {
        LOGGER.info("Resume, state = " + state);
        timer.stopTimer();
        state = CONNECTING;
        sendConnectionState();
        handler.postDelayed(commonRunnable, COMMON_TIME_OUT);
        handler.postDelayed(portCheckRunnable, PORT_CHECK_TIME_OUT);
        domainResolver.tryResolveCurrentServerDomain(null);
        resumeVpn();
    }

    @Override
    public void stop() {
        LOGGER.info("Stop, state = " + state);
        timer.stopTimer();
        state = NOT_CONNECTED;
        sendConnectionState();
        forceStopVpn();
    }

    @Override
    public void startConnecting() {
        LOGGER.info("startConnecting, state = " + state);
        if (state == NOT_CONNECTED || state == PAUSED) {
            if (isFastestServerEnabled() && !multiHopController.isEnabled()) {
                startConnectWithFastestServer();
            } else {
                checkRandomServerOptions();
                startConnectProcess();
            }
        }
    }

    @Override
    public void startConnecting(boolean force) {
        startConnecting();
    }

    @Override
    public void addStateListener(VpnStateListener stateListener) {
        Log.d(TAG, "setStateListener: ");
        listeners.add(stateListener);
        if (stateListener != null) {
            stateListener.onConnectionStateChanged(state);
        }
    }

    @Override
    public void removeStateListener(VpnStateListener stateListener) {
        Log.d(TAG, "removeStateListener: ");
        listeners.remove(stateListener);
    }

    @Override
    public void destroy() {
        LOGGER.info("destroy");
        stop();
        unregisterReceivers();
        listeners.clear();
        fastestServer.removeObserver(fastestServerObserver);
    }

    @Override
    public void notifyVpnState() {
        sendConnectionState();
    }

    @Override
    public void actionByUser() {
        LOGGER.info("Connection init by user");
        if (state.equals(DISCONNECTING)) {
            return;
        }

        performConnectionAction();
    }

    @Override
    public void reconnect() {
        LOGGER.info("Reconnect, state = " + state);
        if (isFastestServerEnabled() && !multiHopController.isEnabled()) {
            startReconnectWithFastestServer();
        } else {
            checkRandomServerOptions();
            startReconnectProcess();
        }
    }

    @Override
    public void regenerateKeys() {
    }

    private void registerReceivers() {
        connectionStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null) {
                    return;
                }
                if (action.equals(VPN_STATUS)) {
                    String name = intent.getStringExtra(VPN_EXTRA_STATUS);
                    if (name == null) {
                        return;
                    }
                    ConnectionStatus status = ConnectionStatus.valueOf(name);
                    onReceiveConnectionStatus(status);
                } else if (action.equals(NOTIFICATION_ACTION)) {
                    onNotificationAction(intent);
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(VPN_STATUS);
        intentFilter.addAction(NOTIFICATION_ACTION);

        LocalBroadcastManager.getInstance(IVPNApplication.application).registerReceiver(connectionStatusReceiver, intentFilter);
    }

    private void unregisterReceivers() {
        LocalBroadcastManager.getInstance(IVPNApplication.application).unregisterReceiver(connectionStatusReceiver);
    }

    private void onNotificationAction(Intent intent) {
        String actionExtra = intent.getStringExtra(NOTIFICATION_ACTION_EXTRA);
        if (actionExtra == null) {
            return;
        }
        switch (actionExtra) {
            case DISCONNECT_ACTION: {
                behaviourListener.disconnect();
                break;
            }
            case PAUSE_ACTION: {
                behaviourListener.pauseActionByUser();
                break;
            }
            case RESUME_ACTION: {
                behaviourListener.resumeActionByUser();
                break;
            }
            case STOP_ACTION: {
                behaviourListener.stopActionByUser();
                break;
            }
        }
    }

    private void performConnectionAction() {
        LOGGER.info("performConnectionAction: state = " + state);
        if (state.equals(CONNECTING) || state.equals(CONNECTED)) {
            startDisconnectProcess();
        } else {
            if (isFastestServerEnabled() && !multiHopController.isEnabled()) {
                startConnectWithFastestServer();
            } else {
                checkRandomServerOptions();
                startConnectProcess();
            }
        }
    }

    private void startConnectWithFastestServer() {
        LOGGER.info("startConnectWithFastestServer: state = " + state);

        for (VpnStateListener listener : listeners) {
            listener.onFindingFastestServer();
        }

        //ToDo Migrate class to Kotlin and rewrite with coroutines
        if (_fastestServer != null) {
            for (VpnStateListener listener : listeners) {
                listener.notifyServerAsFastest(_fastestServer);
            }
            serversRepository.setCurrentServer(ServerType.ENTRY, _fastestServer);

            startConnectProcess();
        } else {
            new Handler().postDelayed(this::checkFastestServerAndConnect, 1000);
        }
    }

    private void checkFastestServerAndConnect() {
        Server serverToConnect = _fastestServer != null ?
                _fastestServer : serversRepository.getDefaultServer(ServerType.ENTRY);

        for (VpnStateListener listener : listeners) {
            listener.notifyServerAsFastest(serverToConnect);
        }
        serversRepository.setCurrentServer(ServerType.ENTRY, serverToConnect);

        startConnectProcess();
    }

    private void startReconnectWithFastestServer() {
        LOGGER.info("startReconnectWithFastestServer: state = " + state);
        for (VpnStateListener listener : listeners) {
            listener.onFindingFastestServer();
        }

        if (_fastestServer != null) {
            for (VpnStateListener listener : listeners) {
                listener.notifyServerAsFastest(_fastestServer);
            }
            serversRepository.setCurrentServer(ServerType.ENTRY, _fastestServer);

            startReconnectProcess();
        } else {
            new Handler().postDelayed(this::checkFastestServerAndReconnect, 1000);
        }
    }

    private void checkFastestServerAndReconnect() {
        Server serverToConnect = _fastestServer != null ?
                _fastestServer : serversRepository.getDefaultServer(ServerType.ENTRY);

        for (VpnStateListener listener : listeners) {
            listener.notifyServerAsFastest(serverToConnect);
        }
        serversRepository.setCurrentServer(ServerType.ENTRY, serverToConnect);

        startReconnectProcess();
    }

    private void startConnectProcess() {
        LOGGER.info("startConnectProcess: state = " + state);
        state = CONNECTING;
        sendConnectionState();
        handler.postDelayed(commonRunnable, COMMON_TIME_OUT);
        handler.postDelayed(portCheckRunnable, PORT_CHECK_TIME_OUT);
        domainResolver.tryResolveCurrentServerDomain(null);
        startVpn();
    }

    private void startReconnectProcess() {
        state = CONNECTING;
        sendConnectionState();
        handler.postDelayed(commonRunnable, COMMON_TIME_OUT);
        handler.postDelayed(portCheckRunnable, PORT_CHECK_TIME_OUT);
        domainResolver.tryResolveCurrentServerDomain(null);
        reconnectVpn();
    }

    private void startDisconnectProcess() {
        LOGGER.info("startDisconnectProcess: state = " + state);
        state = DISCONNECTING;
        sendConnectionState();
        stopVpn();
    }

    private void onAuthFailed() {
        LOGGER.info("onAuthFailed: state = " + state);
        handler.removeCallbacksAndMessages(null);
        stopVpn();
        for (VpnStateListener listener : listeners) {
            listener.onAuthFailed();
        }
        state = NOT_CONNECTED;
        sendConnectionState();
    }

    private void reset() {
        LOGGER.info("Reset");
        handler.removeCallbacksAndMessages(null);
        state = NOT_CONNECTED;
        for (VpnStateListener listener : listeners) {
            listener.onCheckSessionState();
        }
        sendConnectionState();
    }

    private void tryAnotherPort() {
        LOGGER.info("Try another port");
        stopVpn();

        for (VpnStateListener listener : listeners) {
            listener.notifyAnotherPortUsedToConnect();
        }

        new Handler().postDelayed(() -> {
            selectNextPort();
            performConnectionAction();
        }, 500);
    }

    private void onTimeOut() {
        LOGGER.info("onTimeOut");
        stopVpn();
        for (VpnStateListener listener : listeners) {
            listener.onTimeOut();
        }
    }

    private void selectNextPort() {
        LOGGER.info("selectNextPort");
        settings.nextPort();
    }

    private void onReceiveConnectionStatus(ConnectionStatus status) {
        if (status == null) {
            return;
        }
        this.status = status;
        LOGGER.info("onReceiveConnectionStatus: status = " + status);
        LOGGER.info("onReceiveConnectionStatus: state = " + state);
        switch (status) {
            case LEVEL_CONNECTED:
                behaviourListener.updateVpnConnectionState(VPNConnectionState.CONNECTED);
                state = CONNECTED;
                sendConnectionState();
                handler.removeCallbacksAndMessages(null);
                break;
            case UNKNOWN_LEVEL:
            case LEVEL_AUTH_FAILED:
                behaviourListener.updateVpnConnectionState(VPNConnectionState.ERROR);
                onAuthFailed();
                break;
            case LEVEL_NOTCONNECTED:
                behaviourListener.updateVpnConnectionState(VPNConnectionState.DISCONNECTED);
                if (state.equals(NOT_CONNECTED) || state.equals(CONNECTING) || state.equals(PAUSED)) {
                    return;
                }
                if (state.equals(PAUSING)) {
                    state = PAUSED;
                } else {
                    state = NOT_CONNECTED;
                    for (VpnStateListener listener : listeners) {
                        listener.onCheckSessionState();
                    }
                }
                sendConnectionState();
                handler.removeCallbacksAndMessages(null);
                break;
            case LEVEL_CONNECTING_NO_SERVER_REPLY_YET:
                handler.removeCallbacks(noNetworkRunnable);
                //ToDo should we change state to Connecting?
                break;
            case LEVEL_NONETWORK:
                if (state.equals(CONNECTED)) {
                    return;
                }
                handler.removeCallbacks(noNetworkRunnable);
                handler.postDelayed(noNetworkRunnable, NO_NETWORK_TIME_OUT);
                break;
            case LEVEL_START:
                break;
            case LEVEL_CONNECTING_SERVER_REPLIED:
                handler.removeCallbacks(noNetworkRunnable);
                break;
        }
    }

    private boolean isVpnActive() {
        return VpnStatus.isVPNActive()
                || (VpnStatus.lastLevel == ConnectionStatus.LEVEL_NONETWORK && IVPNService.isRunning.get());
    }

    private boolean isFastestServerEnabled() {
        return serversRepository.getSettingFastestServer();
    }

    private void checkRandomServerOptions() {
        if (isRandomEntryServerEnabled()) {
            serversRepository.getRandomServerFor(ServerType.ENTRY, getRandomServerSelectionListener());
        }
        if (isRandomExitServerEnabled()) {
            serversRepository.getRandomServerFor(ServerType.EXIT, getRandomServerSelectionListener());
        }
    }

    private boolean isRandomEntryServerEnabled() {
        return serversRepository.getSettingRandomServer(ServerType.ENTRY);
    }

    private boolean isRandomExitServerEnabled() {
        return serversRepository.getSettingRandomServer(ServerType.EXIT);
    }

    private void sendConnectionState() {
        LOGGER.info("sendConnectionState: state = " + state);
        for (VpnStateListener listener : listeners) {
            listener.onConnectionStateChanged(state);
        }
    }

    private void stopVpn() {
        LOGGER.info("stopVpn: state = " + state);
        LOGGER.info("IVPNService.isRunning.get() = " + IVPNService.isRunning.get());
        if (!IVPNService.isRunning.get()) {
            return;
        }

        behaviourListener.onDisconnectingFromVpn();
        Context context = IVPNApplication.application;
        Intent disconnectIntent = new Intent(context, IVPNService.class);
        disconnectIntent.setAction(DISCONNECT_VPN);
        startService(context, disconnectIntent);
    }

    private void forceStopVpn() {
        if (!IVPNService.isRunning.get()) {
            return;
        }
        behaviourListener.onDisconnectingFromVpn();
        Context context = IVPNApplication.application;
        Intent forceStopIntent = new Intent(context, IVPNService.class);
        forceStopIntent.setAction(STOP_VPN);
        startService(context, forceStopIntent);
    }

    private void pauseVpn(long pauseDuration) {
        behaviourListener.onDisconnectingFromVpn();
        Context context = IVPNApplication.application;
        Intent pauseIntent = new Intent(context, IVPNService.class);
        pauseIntent.setAction(PAUSE_VPN);
        pauseIntent.putExtra(VPN_PAUSE_DURATION_EXTRA, pauseDuration);
        startService(context, pauseIntent);
    }

    private void resumeVpn() {
        behaviourListener.onConnectingToVpn();
        Context context = IVPNApplication.application;
        Intent resumeIntent = new Intent(context, IVPNService.class);
        resumeIntent.setAction(RESUME_VPN);
        startService(context, resumeIntent);
    }

    private void reconnectVpn() {
        behaviourListener.onConnectingToVpn();
        Context context = IVPNApplication.application;
        Intent reconnectIntent = new Intent(context, IVPNService.class);
        reconnectIntent.setAction(RECONNECTING_VPN);
        startService(context, reconnectIntent);
    }

    private void startVpn() {
        Log.d(TAG, "startVpn: ");
        behaviourListener.onConnectingToVpn();
        Context context = IVPNApplication.application;
        Intent startServiceIntent = new Intent(context, IVPNService.class);
        startService(context, startServiceIntent);
    }

    private void startService(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onStatusChanged(ConnectionStatus status) {
        onReceiveConnectionStatus(status);
    }

    private OnRandomServerSelectionListener getRandomServerSelectionListener() {
        return (server, serverType) -> {
            for (VpnStateListener listener : listeners) {
                listener.notifyServerAsRandom(server, serverType);
            }
        };
    }
}