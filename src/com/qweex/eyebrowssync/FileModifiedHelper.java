package com.qweex.eyebrowssync;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;

public class FileModifiedHelper {
    private static final int DATABASE_VERSION = 1;
    /** The file containing the databases. */
    private static final String DATABASE= "EyebrowsSync_modified.db";
    /** One of the tables in the SQL database. */
    private static final String DATABASE_TABLE = "files";
    /** The database for the app. */
    private static SQLiteDatabase database;
    /** A tool to help with the opening of the database. It's in the Android doc examples, yo.*/
    private static DatabaseOpenHelper databaseOpenHelper;

    private String filepath;

    /** needs to be called once
     * @param context The context to associate with the connector.
     * */
    public static void initialize(Context context)
    {
        databaseOpenHelper = new DatabaseOpenHelper(context, DATABASE, null, DATABASE_VERSION);
        database = databaseOpenHelper.getWritableDatabase();
        FileModifiedHelper test = new FileModifiedHelper("/sdcard", "settings.k9s");
        Log.d("EyebrowsSync", test.get() + "~");
        Log.d("EyebrowsSync", "Initialized files DB");
    }

    public FileModifiedHelper(String folder, String name) {
        this.filepath = folder + File.separator + name;
    }

    public FileModifiedHelper(File f) {
        this.filepath = f.getAbsolutePath();
    }

    public synchronized long get() {
        if(android.os.Build.VERSION.SDK_INT >= 14) {
            open();
            Cursor c = database.query(DATABASE_TABLE, new String[]{"_id", "filepath", "last_modified"}, "filepath=?", new String[] {filepath}, null, null, null);
            Log.d("EyebrowsSync", "Getting: " + filepath);
            if(c.getCount()==0) {
                Log.d("EyebrowsSync", "Got: N/A");
                return 0;
            }
            c.moveToFirst();
            Log.d("EyebrowsSync", "Got: " + c.getLong(c.getColumnIndex("last_modified")));
            return c.getLong(c.getColumnIndex("last_modified"));
        }
         else {
            return new File(filepath).lastModified();
        }
    }

    public synchronized boolean set(long time) {
        if(android.os.Build.VERSION.SDK_INT >= 14) {
            open();
            ContentValues row = new ContentValues();
            row.put("last_modified", time);
            row.put("filepath", filepath);
            Cursor c = database.query(DATABASE_TABLE, new String[]{"_id", "filepath", "last_modified"}, "filepath=?", new String[] {filepath}, null, null, null);
            Log.d("EyebrowsSync", "Setting: " + filepath + " to " + time);
            if(c.getCount()==0)
                return database.insert(DATABASE_TABLE, null, row)>0;
            else
                return database.update(DATABASE_TABLE, row, "filepath=?", new String[]{ filepath})>0;
        } else {
            return new File(filepath).setLastModified(time);
        }
    }

    /** Opens the database so that it can be read or written. */
    private static void open() throws SQLException
    {
        if(database!=null && database.isOpen())
            return;
        database = databaseOpenHelper.getWritableDatabase();
    }

    /** Closes the database when you are done with it. */
    private static boolean close()
    {
        if (database != null)
            database.close();
        return true;
    }

    /** Helper open class for DatabaseConnector */
    private static class DatabaseOpenHelper extends SQLiteOpenHelper
    {
        public DatabaseOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version)
        {
            super(context, name, factory, version);
            Log.d("EyebrowsSync", "Created DatabaseOpenHelper, yo");
        }

        @Override
        public void onCreate(SQLiteDatabase db)
        {
            Log.d("EyebrowsSync", "onCreate");
            onUpgrade(db, 0, DATABASE_VERSION);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
        {
            for(int upgrading=oldVersion+1; upgrading<=newVersion; upgrading++) {
                Log.d("EyebrowsSync", "onUpgrade " + upgrading);
                switch(upgrading) {
                    case 1:
                        Log.d("EyebrowsSync", "Creating " + DATABASE_TABLE);
                        String createQuery = "CREATE TABLE " + DATABASE_TABLE + " " +
                                "(_id integer primary key autoincrement" +
                                ", filepath TEXT unique" +
                                ", last_modified INTEGER" +
                                ");";
                        db.execSQL(createQuery);
                        break;
                }
            }
        }
    }
}
