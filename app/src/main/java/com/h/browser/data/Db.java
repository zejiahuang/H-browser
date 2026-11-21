package com.h.browser.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public final class Db extends SQLiteOpenHelper {

    private static Db instance;

    public static final class Item {
        public long id;
        public String title;
        public String url;
        public long time;

        public Item(long id, String title, String url, long time) {
            this.id = id;
            this.title = title;
            this.url = url;
            this.time = time;
        }
    }

    public static synchronized Db get(Context context) {
        if (instance == null) {
            instance = new Db(context.getApplicationContext());
        }
        return instance;
    }

    private Db(Context context) {
        super(context, "h.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE bookmarks(_id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT,url TEXT UNIQUE,time INTEGER)");
        db.execSQL("CREATE TABLE history(_id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT,url TEXT,time INTEGER)");
        db.execSQL("CREATE INDEX idx_history_time ON history(time)");
        db.execSQL("CREATE TABLE site_rules(host TEXT PRIMARY KEY,adblock INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public synchronized void addBookmark(String title, String url) {
        ContentValues values = new ContentValues();
        values.put("title", title == null ? url : title);
        values.put("url", url);
        values.put("time", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("bookmarks", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void removeBookmark(String url) {
        getWritableDatabase().delete("bookmarks", "url=?", new String[]{url});
    }

    public synchronized void removeBookmark(long id) {
        getWritableDatabase().delete("bookmarks", "_id=?", new String[]{String.valueOf(id)});
    }

    public synchronized boolean isBookmarked(String url) {
        Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM bookmarks WHERE url=? LIMIT 1", new String[]{url});
        boolean exists = c.moveToFirst();
        c.close();
        return exists;
    }

    public synchronized List<Item> bookmarks(int limit) {
        return query("SELECT _id,title,url,time FROM bookmarks ORDER BY time DESC LIMIT " + limit);
    }

    public synchronized void addHistory(String title, String url) {
        ContentValues values = new ContentValues();
        values.put("title", title == null ? url : title);
        values.put("url", url);
        values.put("time", System.currentTimeMillis());
        getWritableDatabase().insert("history", null, values);
        getWritableDatabase().execSQL("DELETE FROM history WHERE _id NOT IN (SELECT _id FROM history ORDER BY time DESC LIMIT 3000)");
    }

    private static final String NOT_SKIN = "url NOT LIKE 'https://appassets.androidplatform.net/skin/%'";

    public synchronized List<Item> history(int limit) {
        return query("SELECT _id,title,url,time FROM history WHERE " + NOT_SKIN + " ORDER BY time DESC LIMIT " + limit);
    }

    public synchronized List<Item> searchHistory(String query, int limit) {
        String like = "%" + query + "%";
        return query("SELECT _id,title,url,time FROM history WHERE (" + NOT_SKIN + ") AND (title LIKE ? OR url LIKE ?) ORDER BY time DESC LIMIT " + limit, like, like);
    }

    public synchronized void removeHistory(long id) {
        getWritableDatabase().delete("history", "_id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void clearHistory() {
        getWritableDatabase().delete("history", null, null);
    }

    public synchronized long historyCount() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM history", null);
        long count = c.moveToFirst() ? c.getLong(0) : 0;
        c.close();
        return count;
    }

    public synchronized boolean siteAdblock(String host, boolean fallback) {
        Cursor c = getReadableDatabase().rawQuery("SELECT adblock FROM site_rules WHERE host=?", new String[]{host});
        boolean value = fallback;
        if (c.moveToFirst()) {
            value = c.getInt(0) == 1;
        }
        c.close();
        return value;
    }

    public synchronized void setSiteAdblock(String host, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put("host", host);
        values.put("adblock", enabled ? 1 : 0);
        getWritableDatabase().insertWithOnConflict("site_rules", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private List<Item> query(String sql, String... args) {
        List<Item> items = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        while (c.moveToNext()) {
            items.add(new Item(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)));
        }
        c.close();
        return items;
    }
}
