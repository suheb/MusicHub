package com.haloappstudio.musichub;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.haloappstudio.musichub.dialogs.JoinHubDialog;
import com.haloappstudio.musichub.utils.Utils;
import com.haloappstudio.musichub.utils.WifiApManager;


public class MainActivity extends ActionBarActivity {
    private WifiApManager mWifiApManager;
    private WifiManager mWifiManager;
    private WifiConfiguration mWifiConf;
    private ProgressDialog mProgressDialog;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (getIntent().getBooleanExtra(Utils.ACTION_EXIT, false)) {
            finish();
        }
        mWifiApManager = new WifiApManager(this);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mWifiConf = new WifiConfiguration();

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setCancelable(false);

        Button createHubButton = (Button) findViewById(R.id.create_hub_button);
        createHubButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mWifiConf.SSID = Build.MODEL;
                mWifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                mProgressDialog.setMessage("Creating hub");
                mProgressDialog.show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mWifiApManager.setWifiApEnabled(mWifiConf, true);
                        while(!mWifiApManager.isWifiApEnabled()) {
                            try{
                                Thread.sleep(500);
                            }
                            catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mProgressDialog.dismiss();
                                startActivity(new Intent(getApplicationContext(),SongsListActivity.class));
                            }
                        });
                    }
                }).start();
            }
        });

        Button joinHubButton = (Button) findViewById(R.id.join_hub_button);
        final DialogFragment dialog = new JoinHubDialog();
        joinHubButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mProgressDialog.setMessage("Enabling wifi");
                mProgressDialog.show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        //Start Wifi scan
                        if(mWifiApManager.isWifiApEnabled()) {
                            mWifiApManager.setWifiApEnabled(mWifiConf,false);
                        }
                        mWifiManager.setWifiEnabled(true);
                        mWifiManager.startScan();
                        while(!mWifiManager.isWifiEnabled()) {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mProgressDialog.dismiss();
                                dialog.show(getSupportFragmentManager(), "Join Hub");
                            }
                        });
                    }
                }).start();

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mWifiApManager.setWifiApEnabled(mWifiConf, false);
        mWifiManager.setWifiEnabled(false);
    }
}
