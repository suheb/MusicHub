package com.haloappstudio.musichub.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.haloappstudio.musichub.R;
import com.haloappstudio.musichub.utils.Utils;

import java.util.List;

/**
 * Created by suheb on 26/8/14.
 */
public class JoinHubDialog extends DialogFragment {

    private WifiManager mWifiManager;
    private WifiConfiguration mWifiConf;
    private ProgressDialog mProgressDialog;
    private FragmentActivity mActivity;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mActivity =  getActivity();
        mWifiManager = (WifiManager) mActivity.getSystemService(Context.WIFI_SERVICE);

        final List<ScanResult> scanResultList = mWifiManager.getScanResults();
        WifiListAdapter wifiListAdapter = new WifiListAdapter(mActivity, R.layout.joinhub_dialog, scanResultList);

        mWifiConf = new WifiConfiguration();
        mProgressDialog = new ProgressDialog(mActivity);

        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setTitle("Select Hub");
        if(scanResultList.size() != 0){
            builder.setAdapter(wifiListAdapter, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    mWifiConf.SSID = "\"" + scanResultList.get(i).SSID + "\"";
                    mWifiConf.status = WifiConfiguration.Status.ENABLED;
                    mWifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                    int id  = mWifiManager.addNetwork(mWifiConf);
                    mWifiManager.enableNetwork(id, true);
                    mProgressDialog.setMessage("Connecting..");
                    mProgressDialog.show();
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            while(!Utils.isWifiConnected(mActivity.getApplication())) {
                                try{
                                    Thread.sleep(500);
                                }
                                catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            mActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mProgressDialog.dismiss();
                                }
                            });
                        }
                    }).start();
                }
            });
        }
        else {
            builder.setMessage("No hub Available.\nTry again!!");
        }


        return builder.create();
    }

    public class WifiListAdapter extends ArrayAdapter<ScanResult> {
        private Context mContext;
        private List<ScanResult> mList;
        private int mResourceId;

        public WifiListAdapter(Context context, int resource, List<ScanResult> list) {
            super(context, resource, list);
            this.mList = list;
            this.mContext = context;
            this.mResourceId = resource;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) mContext
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            if(convertView == null){
                convertView = inflater.inflate(mResourceId, parent, false);
            }
            TextView textView = (TextView) convertView.findViewById(R.id.textView);
            textView.setText(mList.get(position).SSID);
            return convertView;
        }
    }
}
