package com.haloappstudio.musichub;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import com.haloappstudio.musichub.utils.Utils;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.libcore.RequestHeaders;
import com.koushikdutta.async.http.server.AsyncHttpServer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ServerService extends Service {
    private final int mId = 1;
    private NotificationCompat.Builder mBuilder;
    private List<WebSocket> mSockets;
    private AsyncHttpServer mAsyncHttpServer;
    private AsyncHttpServer.WebSocketRequestCallback mWebSocketCallback;
    private MediaPlayer mMediaPlayer;
    private String[] mPlaylist;
    private int mCurrentSongIndex;
    private File mCurrentSong;
    private BroadcastReceiver mUpdateSongReceiver;
    private NotificationManager mNotificationManager;

    final static int CHUNK_SIZE = 10000;

    @Override
    public void onCreate() {
        super.onCreate();
        Intent prevIntent = new Intent(Utils.ACTION_UPDATE);
        prevIntent.putExtra(Utils.ACTION_UPDATE, Utils.ACTION_PREV);
        PendingIntent prevPendingIntent = PendingIntent.getBroadcast(this, 0,
                prevIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        Intent syncIntent = new Intent(Utils.ACTION_UPDATE);
        syncIntent.putExtra(Utils.ACTION_UPDATE, Utils.ACTION_SYNC);
        PendingIntent syncPendingIntent = PendingIntent.getBroadcast(this, 0,
                syncIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        Intent nextIntent = new Intent(Utils.ACTION_UPDATE);
        nextIntent.putExtra(Utils.ACTION_UPDATE, Utils.ACTION_NEXT);
        PendingIntent nextPendingIntent = PendingIntent.getBroadcast(this, 0,
                nextIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        Intent stopIntent = new Intent(Utils.ACTION_UPDATE);
        stopIntent.putExtra(Utils.ACTION_UPDATE, Utils.ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(this, 0,
                stopIntent, PendingIntent.FLAG_CANCEL_CURRENT);


        mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Music Hub").setContentText("Playing")
                .setStyle(new NotificationCompat.BigTextStyle())
                .addAction(android.R.drawable.ic_media_previous, null, prevPendingIntent)
                .addAction(R.drawable.ic_action_cancel, null, stopPendingIntent)
                .addAction(android.R.drawable.ic_popup_sync, null, syncPendingIntent)
                .addAction(android.R.drawable.ic_media_next, null, nextPendingIntent);
        // Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(this, ServerActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(ServerActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0,
                PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);

        mUpdateSongReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int action = intent.getIntExtra(Utils.ACTION_UPDATE, Utils.ACTION_SYNC);
                switch (action){
                    case Utils.ACTION_SYNC:
                        sync();
                        break;
                    case Utils.ACTION_PREV:
                        playPrev();
                        break;
                    case Utils.ACTION_NEXT:
                        playNext();
                        break;
                    case Utils.ACTION_STOP:
                        stopSelf();
                        break;
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Utils.ACTION_UPDATE);
        registerReceiver(mUpdateSongReceiver, intentFilter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(mId, mBuilder.build());
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mPlaylist = intent.getExtras().getStringArray("playlist");
        mSockets = new ArrayList<>();
        mAsyncHttpServer = new AsyncHttpServer();
        mMediaPlayer = new MediaPlayer();
        mCurrentSongIndex = 0;
        mCurrentSong = new File(mPlaylist[mCurrentSongIndex]);
        // Set up MediaPlayer
        try {
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setDataSource(this, Uri.fromFile(mCurrentSong));
            mMediaPlayer.prepareAsync();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Set MediaPlayer listeners
        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                mBuilder.setContentText("Playing: "+mCurrentSong.getName());
                mNotificationManager.notify(mId, mBuilder.build());
                mediaPlayer.start();
            }
        });
        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                playNext();
            }
        });
        // Set WebsocketCallback
        mWebSocketCallback = new AsyncHttpServer.WebSocketRequestCallback() {
            @Override
            public void onConnected(final WebSocket webSocket, RequestHeaders headers) {
                // Add to connected socket list
                mSockets.add(webSocket);

                int fileSize = (int) mCurrentSong.length();
                int lastOffset = fileSize - (fileSize % CHUNK_SIZE);
                byte[] bytes = new byte[fileSize];
                // Read file into bytes for sending
                try {
                    BufferedInputStream buf = new BufferedInputStream(new FileInputStream(mCurrentSong));
                    buf.read(bytes);
                    buf.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // Send file in chunks
                webSocket.send("file</>" + mCurrentSong.getName());
                //Toast.makeText(getApplicationContext(), mCurrentSong.getName(), Toast.LENGTH_SHORT).show();
                for (int offset = 0; offset < lastOffset; offset += CHUNK_SIZE) {
                    webSocket.send(bytes, offset, offset + CHUNK_SIZE);
                }
                webSocket.send(bytes, lastOffset, fileSize);
                webSocket.send("File sent");
                webSocket.send("prepare");
                // Use this to clean up any references to your websocket
                webSocket.setClosedCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        try {
                            if (ex != null)
                                Log.e("WebSocket", "Error");
                        } finally {
                            mSockets.remove(webSocket);
                        }
                    }
                });
                webSocket.setStringCallback(new WebSocket.StringCallback() {
                    @Override
                    public void onStringAvailable(final String s) {
                        if (s.contains("prepared")) {
                            sync();
                        }
                    }
                });
            }
        };
        // Set WebsocketCallback and listen on port
        mAsyncHttpServer.websocket("/", mWebSocketCallback);
        mAsyncHttpServer.listen(Utils.PORT_NUMBER);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mUpdateSongReceiver);
        mAsyncHttpServer.stop();
        mMediaPlayer.stop();
        mNotificationManager.cancel(mId);

    }

    public void playSong(){
        mCurrentSong = new File(mPlaylist[mCurrentSongIndex]);
        int fileSize = (int) mCurrentSong.length();
        int lastOffset = fileSize - (fileSize % CHUNK_SIZE);
        byte[] bytes = new byte[fileSize];
        mMediaPlayer.reset();
        try {
            // Set up MediaPlayer
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setDataSource(ServerService.this, Uri.fromFile(mCurrentSong));
            mMediaPlayer.prepareAsync();
            // Read file into bytes for sending
            BufferedInputStream buf = new BufferedInputStream(new FileInputStream(mCurrentSong));
            buf.read(bytes);
            buf.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (WebSocket webSocket : mSockets) {
            // Send file in chunks
            webSocket.send("file</>" + mCurrentSong.getName());
            for (int offset = 0; offset < lastOffset; offset += CHUNK_SIZE) {
                webSocket.send(bytes, offset, offset + CHUNK_SIZE);
            }
            webSocket.send(bytes, lastOffset, fileSize);
            webSocket.send("File sent");
            webSocket.send("prepare");
        }
    }
    public void playNext(){
        mCurrentSongIndex++;
        if(mCurrentSongIndex == mPlaylist.length){
            mCurrentSongIndex = 0;
        }
        playSong();
    }
    public void playPrev(){
        mCurrentSongIndex--;
        if(mCurrentSongIndex < 0){
            mCurrentSongIndex = mPlaylist.length - 1;
        }
        playSong();
    }
    public void sync(){
        for (WebSocket socket : mSockets) {
            socket.send("seek</>" + mMediaPlayer.getCurrentPosition());
        }
    }
}