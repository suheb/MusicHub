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
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
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
    final static int CHUNK_SIZE = 10000;
    private final int mNotificationId = 1;
    private NotificationCompat.Builder mBuilder;
    private List<WebSocket> mSockets;
    private AsyncHttpServer mAsyncHttpServer;
    private AsyncHttpServer.WebSocketRequestCallback mWebSocketCallback;
    private MediaPlayer mMediaPlayer;
    private String[] mPlaylist;
    private int mCurrentSongIndex;
    private File mCurrentSong;
    private BroadcastReceiver mServerReceiver;
    private NotificationManager mNotificationManager;
    private MediaMetadataRetriever mMediaMetadataRetriever;

    @Override
    public void onCreate() {
        super.onCreate();
        Intent prevIntent = new Intent(Utils.ACTION_PREV);
        PendingIntent prevPendingIntent = PendingIntent.getBroadcast(this, 0,
                prevIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        Intent syncIntent = new Intent(Utils.ACTION_SYNC);
        PendingIntent syncPendingIntent = PendingIntent.getBroadcast(this, 0,
                syncIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        Intent nextIntent = new Intent(Utils.ACTION_NEXT);
        PendingIntent nextPendingIntent = PendingIntent.getBroadcast(this, 0,
                nextIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setStyle(new NotificationCompat.BigTextStyle())
                .addAction(android.R.drawable.ic_media_previous, getString(R.string.prev), prevPendingIntent)
                .addAction(android.R.drawable.ic_popup_sync, getString(R.string.sync), syncPendingIntent)
                .addAction(android.R.drawable.ic_media_next, getString(R.string.next), nextPendingIntent);
        // Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(this, ServerActivity.class);
        PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent, 0);
        mBuilder.setContentIntent(resultPendingIntent);

        mServerReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(Utils.ACTION_SYNC)) {
                    sync();
                } else if (action.equals(Utils.ACTION_STOP)) {
                    stopSelf();
                } else if (action.equals(Utils.ACTION_PREV)) {
                    playPrev();
                } else if (action.equals(Utils.ACTION_NEXT)) {
                    playNext();
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Utils.ACTION_SYNC);
        intentFilter.addAction(Utils.ACTION_STOP);
        intentFilter.addAction(Utils.ACTION_PREV);
        intentFilter.addAction(Utils.ACTION_NEXT);
        registerReceiver(mServerReceiver, intentFilter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(mNotificationId, mBuilder.build());
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mPlaylist = intent.getExtras().getStringArray("playlist");
        mSockets = new ArrayList<>();
        mAsyncHttpServer = new AsyncHttpServer();
        mMediaPlayer = new MediaPlayer();
        mCurrentSongIndex = 0;
        mCurrentSong = new File(mPlaylist[mCurrentSongIndex]);
        mMediaMetadataRetriever = new MediaMetadataRetriever();
        // Set up MediaPlayer
        try {
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setDataSource(this, Uri.fromFile(mCurrentSong));
            mMediaPlayer.prepareAsync();
            mMediaMetadataRetriever.setDataSource(this, Uri.fromFile(mCurrentSong));

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Set MediaPlayer listeners
        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                mediaPlayer.start();
                String bigContent = mMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) + "\n"
                        + mMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                mBuilder.setContentTitle(mMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
                mBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(bigContent));
                mNotificationManager.notify(mNotificationId, mBuilder.build());
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
        unregisterReceiver(mServerReceiver);
        mAsyncHttpServer.stop();
        mMediaPlayer.release();
        mNotificationManager.cancel(mNotificationId);
    }

    // Utility methods
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
            mMediaMetadataRetriever.setDataSource(this, Uri.fromFile(mCurrentSong));
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
