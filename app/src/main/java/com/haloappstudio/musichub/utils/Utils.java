package com.haloappstudio.musichub.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * Created by suheb on 20/10/14.
 */
public class Utils {
    public static final int PORT_NUMBER = 8585;
    public static final String KEY_PREPARE = "prepare";
    public static final String KEY_SEEK = "seek";
    public static final String KEY_FILE = "file";
    public static final String KEY_SYNC = "sync";

    public static Boolean isWifiConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        return wifiInfo.isConnected();
    }
}
