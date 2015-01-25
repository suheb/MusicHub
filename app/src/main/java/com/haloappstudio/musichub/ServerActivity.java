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
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.haloappstudio.musichub.utils.Utils;
import com.koushikdutta.async.AsyncServerSocket;
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


public class ServerActivity extends ActionBarActivity {
    private List<WebSocket> mSockets;
    private AsyncHttpServer mAsyncHttpServer;
    private AsyncHttpServer.WebSocketRequestCallback mWebSocketCallback;
    private MediaPlayer mMediaPlayer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);

        mSockets = new ArrayList<WebSocket>();
        mAsyncHttpServer = new AsyncHttpServer();
        mMediaPlayer = new MediaPlayer();

        File file = new File(Environment.getExternalStorageDirectory(),"/Music/preview.mp3");
        final int fileSize = (int) file.length();
        final int chunk = 10000;
        final int lastOffset = fileSize - (fileSize % chunk);
        final byte[] bytes = new byte[fileSize];

        try {
            // set file for playback
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setDataSource(this, Uri.fromFile(file));
            mMediaPlayer.prepareAsync();
            // read file into bytes for sending
            BufferedInputStream buf = new BufferedInputStream(new FileInputStream(file));
            buf.read(bytes);
            buf.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                mediaPlayer.start();
            }
        });

        Button sendButton = (Button) findViewById(R.id.sendButtonS);
        sendButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                for(WebSocket socket : mSockets) {
                    socket.send("seek-" + mMediaPlayer.getCurrentPosition() +"-"+ System.currentTimeMillis());
                }
            }
        });

        mWebSocketCallback = new AsyncHttpServer.WebSocketRequestCallback() {
            @Override
            public void onConnected(final WebSocket webSocket, RequestHeaders headers) {
                // add to connected socket list
                mSockets.add(webSocket);
                //send file in chunks
                for(int offset = 0; offset < lastOffset; offset += chunk) {
                    webSocket.send(bytes, offset, offset+chunk);
                }
                webSocket.send(bytes, lastOffset, fileSize);
                webSocket.send("File sent");
                webSocket.send("play-" + mMediaPlayer.getCurrentPosition());
                //Use this to clean up any references to your websocket
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
                        ServerActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
                            }
                        });
                        if ("Hello Server".equals(s))
                            webSocket.send("Welcome Client!");
                    }
                });
            }
        };

        mAsyncHttpServer.websocket("/", mWebSocketCallback);
        AsyncServerSocket soc = mAsyncHttpServer.listen(Utils.PORT_NUMBER);

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.server, menu);
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
        mAsyncHttpServer.stop();
        mMediaPlayer.release();
    }
}
