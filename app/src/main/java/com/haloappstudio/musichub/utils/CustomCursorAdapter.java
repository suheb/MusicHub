package com.haloappstudio.musichub.utils;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;
import android.support.v4.widget.SimpleCursorAdapter;
import android.widget.SectionIndexer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;

/**
 * Created by suheb on 25/1/15.
 */
public class CustomCursorAdapter extends SimpleCursorAdapter implements SectionIndexer {

    HashMap<String, Integer> mapIndex;
    String[] sections;
    Cursor cursor;
    public CustomCursorAdapter(Context context, int layout, Cursor cursor, String[] from, int[] to, int flags){
        super(context, layout, cursor, from, to, flags);
        this.cursor = cursor;
    }
    @Override
    public Cursor swapCursor(Cursor cursor){
        super.swapCursor(cursor);
        this.cursor = cursor;
        mapIndex = new LinkedHashMap<>();
        if(cursor != null){
            int index = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            for (cursor.moveToLast(); !cursor.isBeforeFirst(); cursor.moveToPrevious()) {
                String song = cursor.getString(index);
                String ch = song.substring(0, 1);
                if(!Character.isLetter(ch.charAt(0))){
                    ch = "#";
                }
                ch = ch.toUpperCase(Locale.US);

                // HashMap will prevent duplicates
                mapIndex.put(ch, cursor.getPosition());
            }
        }
        Set<String> sectionLetters = mapIndex.keySet();

        // create a list from the set to sort
        ArrayList<String> sectionList = new ArrayList<String>(sectionLetters);

        Collections.sort(sectionList);

        sections = new String[sectionList.size()];

        sectionList.toArray(sections);
        return null;
    }
    public int getPositionForSection(int section) {
        return mapIndex.get(sections[section]);
    }

    public int getSectionForPosition(int position) {
        return 0;
    }

    public Object[] getSections() {
        return sections;
    }

}
