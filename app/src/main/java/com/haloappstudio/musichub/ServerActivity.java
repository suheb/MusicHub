package com.haloappstudio.musichub;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

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


public class ServerActivity extends ActionBarActivity {
    private List<WebSocket> mSockets;
    private AsyncHttpServer mAsyncHttpServer;
    private AsyncHttpServer.WebSocketRequestCallback mWebSocketCallback;
    private MediaPlayer mMediaPlayer;
    private String[] mPlaylist;
    private int mCurrentSongIndex;
    private File mCurrentSong;

    final static int CHUNK_SIZE = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);

        if (savedInstanceState == null) {
            Bundle extras = getIntent().getExtras();
            mPlaylist = extras.getStringArray("playlist");
        } else {
            mPlaylist = savedInstanceState.getStringArray("playlist");
        }

        mSockets = new ArrayList<WebSocket>();
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
                        ServerActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
                            }
                        });
                        if (s.contains("prepared")) {
                            for (WebSocket socket : mSockets) {
                                socket.send("seek</>" + mMediaPlayer.getCurrentPosition());
                            }
                        }
                    }
                });
            }
        };
        // Set WebsocketCallback and listen on port
        mAsyncHttpServer.websocket("/", mWebSocketCallback);
        mAsyncHttpServer.listen(Utils.PORT_NUMBER);

        Button syncButton = (Button) findViewById(R.id.sync_button);
        syncButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                for (WebSocket socket : mSockets) {
                    socket.send("seek</>" + mMediaPlayer.getCurrentPosition());
                }
            }
        });
        Button prevButton = (Button) findViewById(R.id.prev_button);
        prevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playPrev();
            }
        });
        Button nextButton = (Button) findViewById(R.id.next_button);
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playNext();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArray("playlist", mPlaylist);
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
    public void playSong(){
        mCurrentSong = new File(mPlaylist[mCurrentSongIndex]);
        int fileSize = (int) mCurrentSong.length();
        int lastOffset = fileSize - (fileSize % CHUNK_SIZE);
        byte[] bytes = new byte[fileSize];
        mMediaPlayer.reset();
        try {
            // Set up MediaPlayer
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setDataSource(ServerActivity.this, Uri.fromFile(mCurrentSong));
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
}
