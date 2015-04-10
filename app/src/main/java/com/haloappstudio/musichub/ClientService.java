package com.haloappstudio.musichub;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.haloappstudio.musichub.utils.Utils;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class ClientService extends Service {
    private int mNotificationId = 1;
    private AsyncHttpClient mAsyncHttpClient;
    private AsyncHttpClient.WebSocketConnectCallback mWebSocketConnectCallback;
    private WebSocket mWebSocket;
    private MediaPlayer mMediaPlayer;
    private File mHomeDir;
    private File mCurrentSong;
    private MediaMetadataRetriever mMediaMetadataRetriever;
    private NotificationCompat.Builder mBuilder;
    private NotificationManager mNotificationManager;
    private BroadcastReceiver mClientReceiver;


    @Override
    public void onCreate() {
        super.onCreate();
        mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(R.string.app_name));
        // Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(this, ClientActivity.class);
        PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent, 0);
        mBuilder.setContentIntent(resultPendingIntent);

        mHomeDir = new File(Environment.getExternalStorageDirectory(), "/MusicHub");
        if (!mHomeDir.exists()) {
            mHomeDir.mkdirs();
        }
        mClientReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(Utils.ACTION_STOP)) {
                    stopSelf();
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter(Utils.ACTION_STOP);
        registerReceiver(mClientReceiver, intentFilter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(mNotificationId, mBuilder.build());
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mMediaMetadataRetriever = new MediaMetadataRetriever();
        // Set up MediaPlayer
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mMediaPlayer.setScreenOnWhilePlaying(true);
        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                mWebSocket.send("prepared");
                mMediaPlayer.start();
                String bigContent = mMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) + "\n"
                        + mMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                mBuilder.setContentTitle(mMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
                mBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(bigContent));
                mNotificationManager.notify(mNotificationId, mBuilder.build());
                Toast.makeText(getApplicationContext(), "Prepared", Toast.LENGTH_LONG).show();
            }
        });
        // Set up WebSocketConnectCallback
        mWebSocketConnectCallback = new AsyncHttpClient.WebSocketConnectCallback() {
            @Override
            public void onCompleted(Exception ex, final WebSocket webSocket) {
                if (ex != null) {
                    ex.printStackTrace();
                    return;
                }
                mWebSocket = webSocket;
                webSocket.setStringCallback(new WebSocket.StringCallback() {
                    @Override
                    public void onStringAvailable(final String s) {
                        Log.d("TAG", s);
                        if (s.contains("file")) {
                            mMediaPlayer.reset();
                            String[] strings = s.split("</>");

                            mCurrentSong = new File(mHomeDir, strings[1]);
                            if (mCurrentSong.exists()) {
                                mCurrentSong.delete();
                            }
                        } else if (s.contains("prepare")) {
                            try {
                                mMediaPlayer.setDataSource(ClientService.this, Uri.fromFile(mCurrentSong));
                                mMediaPlayer.prepareAsync();
                                mMediaMetadataRetriever.setDataSource(ClientService.this, Uri.fromFile(mCurrentSong));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else if (s.contains("seek")) {
                            String[] strings = s.split("</>");
                            Log.d("TAG", "lag:" + (Integer.parseInt(strings[1]) - mMediaPlayer.getCurrentPosition())
                                    + "latency:" + System.currentTimeMillis());
                            mMediaPlayer.seekTo(Integer.parseInt(strings[1]) + 200);
                        }
                    }
                });
                webSocket.setDataCallback(new DataCallback() {
                    @Override
                    public void onDataAvailable(DataEmitter emitter, final ByteBufferList bb) {
                        byte[] bytes = bb.getAllByteArray();
                        Log.d("TAG", "Got " + bytes.length + " bytes");
                        webSocket.ping("pong");
                        try {
                            FileOutputStream fileOutputStream = new FileOutputStream(mCurrentSong, true);
                            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
                            bufferedOutputStream.write(bytes);

                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        bb.recycle();
                    }
                });
            }
        };
        // Connect to server
        mAsyncHttpClient = AsyncHttpClient.getDefaultInstance();
        mAsyncHttpClient.websocket(Utils.IP_ADDRESS, null, mWebSocketConnectCallback);
        return super.onStartCommand(intent, flags, startId);


    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mClientReceiver);
        mWebSocket.close();
        mMediaPlayer.release();
        String[] files = mHomeDir.list();
        for (String file : files) {
            File currentFile = new File(mHomeDir.getPath(), file);
            currentFile.delete();
        }
        mHomeDir.delete();
        mNotificationManager.cancel(mNotificationId);
    }
}
