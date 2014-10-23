package com.haloappstudio.musichub;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.haloappstudio.musichub.utils.Utils;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;


public class ClientActivity extends ActionBarActivity {
    private AsyncHttpClient  mAsyncHttpClient;
    private WifiManager mWifiManager;
    private AsyncHttpClient.WebSocketConnectCallback mWebSocketConnectCallback;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        //Resolve IP address
        int ipAddress = mWifiManager.getConnectionInfo().getIpAddress();
        String hostAddress = Formatter.formatIpAddress(ipAddress);
        hostAddress = "http://" + hostAddress + ":" +Utils.PORT_NUMBER;
        Log.d("CLIENTTAG", "address is " + hostAddress);
        mWebSocketConnectCallback = new AsyncHttpClient.WebSocketConnectCallback() {
            @Override
            public void onCompleted(Exception ex, WebSocket webSocket) {
                if (ex != null) {
                    ex.printStackTrace();
                    return;
                }
                webSocket.send("Hello Server");
                webSocket.setStringCallback(new WebSocket.StringCallback() {
                    @Override
                    public void onStringAvailable(final String s) {
                        Log.d("CLIENTTAG",s);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                });
            }
        };
        mAsyncHttpClient = AsyncHttpClient.getDefaultInstance();
        mAsyncHttpClient.websocket(hostAddress, null, mWebSocketConnectCallback);

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.client, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
