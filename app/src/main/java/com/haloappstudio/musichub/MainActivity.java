package com.haloappstudio.musichub;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.haloappstudio.musichub.utils.WifiApManager;
import com.haloappstudio.musichub.dialogs.JoinHubDialog;


public class MainActivity extends ActionBarActivity {
    private WifiApManager mWifiApManager;
    private WifiManager mWifiManager;
    private WifiConfiguration mWifiConf;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mWifiApManager = new WifiApManager(this);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        Button createHubButton = (Button) findViewById(R.id.createHubButton);
        createHubButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WifiConfiguration config = new WifiConfiguration();
                config.SSID = Build.MODEL;
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                if(mWifiApManager.setWifiApEnabled(config,true))
                    Log.d("TAG", "Success");
                else
                    Log.d("TAG","Faluire");
            }
        });

        Button joinHubButton = (Button) findViewById(R.id.joinHubButton);
        final DialogFragment dialog = new JoinHubDialog();
        joinHubButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                dialog.show(getSupportFragmentManager(), "Join Hub");
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
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
