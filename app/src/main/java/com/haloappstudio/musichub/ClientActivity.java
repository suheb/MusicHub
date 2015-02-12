package com.haloappstudio.musichub;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

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
import java.util.Timer;
import java.util.TimerTask;


public class ClientActivity extends ActionBarActivity{
    private AsyncHttpClient  mAsyncHttpClient;
    private AsyncHttpClient.WebSocketConnectCallback mWebSocketConnectCallback;
    private WebSocket mWebSocket;
    private MediaPlayer mMediaPlayer;
    private File mCurrentSong;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);
        File dir =  new File(Environment.getExternalStorageDirectory(),"/MusicHub");
        if(!dir.exists()) {
            dir.mkdirs();
        }
        final File file = new File(dir, "temp.mp3");
        if(file.exists()) {
            file.delete();
        }

        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mMediaPlayer.setScreenOnWhilePlaying(true);
        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                mWebSocket.send("prepared");
                mMediaPlayer.start();
                Toast.makeText(getApplicationContext(), "Prepared", Toast.LENGTH_LONG).show();
            }
        });
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
                        if(s.contains("file")){
                            String[] strings = s.split("-");
                            File dir =  new File(Environment.getExternalStorageDirectory(),"/MusicHub");
                            if(!dir.exists()) {
                                dir.mkdirs();
                            }
                            mCurrentSong = new File(dir, strings[1]);
                            if(mCurrentSong.exists()) {
                                mCurrentSong.delete();
                            }
                        }
                        else if(s.contains("prepare")){
                            mMediaPlayer.reset();
                            try {
                                mMediaPlayer.setDataSource(ClientActivity.this, Uri.fromFile(mCurrentSong));
                                mMediaPlayer.prepareAsync();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        else if(s.contains("start")){
                            String[] strings = s.split("-");
                            mMediaPlayer.seekTo(Integer.parseInt(strings[1])+200);
                        }
                        else if(s.contains("seek")){
                            String[] strings = s.split("-");
                            Log.d("TAG", "lag:" + (Integer.parseInt(strings[1]) - mMediaPlayer.getCurrentPosition())
                                    + "latency:" + System.currentTimeMillis());
                            mMediaPlayer.seekTo(Integer.parseInt(strings[1])+200);
                        }
                    }
                });
                webSocket.setDataCallback(new DataCallback() {
                    @Override
                    public void onDataAvailable(DataEmitter emitter, final ByteBufferList bb) {
                        byte[] bytes = bb.getAllByteArray();
                        Log.d("TAG", "Got "+ bytes.length +" bytes");
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
        mAsyncHttpClient = AsyncHttpClient.getDefaultInstance();
        mAsyncHttpClient.websocket("ws://192.168.43.1:8585", null, mWebSocketConnectCallback);

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mWebSocket!=null) {
            mWebSocket.close();
        }
        mMediaPlayer.release();
    }

    private void setTimer() {
        new Timer("keep_alive").scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (mWebSocket != null) {
                    mWebSocket.send("random");
                }
            }
        }, 2000, 3000);
    }
}
