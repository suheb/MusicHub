package com.haloappstudio.musichub;

import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.FilterQueryProvider;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.haloappstudio.musichub.utils.CustomCursorAdapter;
import com.haloappstudio.musichub.utils.Utils;
import com.haloappstudio.musichub.utils.WifiApManager;

import java.util.HashMap;


public class SongsListActivity extends ActionBarActivity
        implements LoaderManager.LoaderCallbacks<Cursor> {
    static final String[] PROJECTION = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA
    };
    static final String SORT_ORDER = MediaStore.Audio.Media.TITLE + " COLLATE LOCALIZED ASC";
    private SimpleCursorAdapter mAdapter;
    private ListView mListView;
    private String mSelectionClause = null;
    private String[] mSelectionArgs = null;
    private HashMap<String, String> mCheckedItems;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_songs_list);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mCheckedItems = new HashMap<>();
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
        mListView = (ListView) findViewById(R.id.songs_list);
        mListView.setEmptyView(progressBar);
        mListView.setFastScrollEnabled(true);
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        mListView.setTextFilterEnabled(true);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Cursor cursor = (Cursor) mAdapter.getItem(position);
                if (mListView.isItemChecked(position)) {
                    mCheckedItems.put(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media._ID)),
                            cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA)));
                    Toast.makeText(SongsListActivity.this, cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA)),
                            Toast.LENGTH_SHORT).show();
                }
                else{
                    mCheckedItems.remove(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media._ID)));
                }
            }
        });

        Button playButton = (Button) findViewById(R.id.play_button);
        playButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                String[] outputStrArr = mCheckedItems.values().toArray(new String[0]);
                Intent serviceIntent = new Intent(getApplicationContext(), ServerService.class);
                Bundle bundle = new Bundle();
                bundle.putStringArray("playlist", outputStrArr);
                serviceIntent.putExtras(bundle);
                startService(serviceIntent);
                Intent activityIntent = new Intent(getApplicationContext(), ServerActivity.class);
                startActivity(activityIntent);
            }
        });

        // For the cursor adapter, specify which columns go into which views
        String[] fromColumns = {
                MediaStore.Audio.Media.TITLE,
                //MediaStore.Audio.Media.ARTIST
        };
        int[] toViews = {
                android.R.id.text1,
                // R.id.artist_name
        };

        // Create an empty adapter we will use to display the loaded data.
        // We pass null for the cursor, then update it in onLoadFinished()
        mAdapter = new CustomCursorAdapter(this, R.layout.item_songs_list, null, fromColumns, toViews, 0);
        mListView.setAdapter(mAdapter);
        mAdapter.setFilterQueryProvider(new FilterQueryProvider() {
            @Override
            public Cursor runQuery(CharSequence query) {
                mSelectionClause = MediaStore.Audio.Media.TITLE + " LIKE ?";
                mSelectionArgs = new String[1];
                mSelectionArgs[0] = "%" + query + "%";
                getSupportLoaderManager().restartLoader(0, null, SongsListActivity.this);
                return null;
            }
        });
        // Prepare the loader.  Either re-connect with an existing one,
        // or start a new one.
        getSupportLoaderManager().initLoader(0, null, this);

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            mAdapter.getFilter().filter(query);
        }
    }

    // Called when a new Loader needs to be created
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // Now create and return a CursorLoader that will take care of
        // creating a Cursor for the data being displayed.
        return new CursorLoader(this, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                PROJECTION, mSelectionClause, mSelectionArgs, SORT_ORDER);
    }

    // Called when a previously created loader has finished loading
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Swap the new cursor in.  (The framework will take care of closing the
        // old cursor once we return.)
        mAdapter.swapCursor(data);
        for(int i =  0; i < mAdapter.getCount(); i++){
            Cursor cursor = (Cursor) mAdapter.getItem(i);
            int index = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
            if(mCheckedItems.get(cursor.getString(index)) != null){
                if(!mListView.isItemChecked(i))
                    mListView.setItemChecked(i, true);
                Toast.makeText(SongsListActivity.this, "Position-" + i,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Called when a previously created loader is reset, making the data unavailable
    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed.  We need to make sure we are no
        // longer using it.
        mAdapter.swapCursor(null);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.songs_list, menu);
        // Get the SearchView and set the searchable configuration
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        MenuItem searchItem = menu.findItem(R.id.search);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        // Assumes current activity is the searchable activity
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                mAdapter.getFilter().filter(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                mAdapter.getFilter().filter(query);
                return false;
            }
        });
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
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle("Music Hub")
                .setMessage("Do you want to close the hub and exit?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        intent.putExtra(Utils.ACTION_EXIT, true);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }
}
