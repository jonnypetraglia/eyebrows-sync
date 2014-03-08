package com.qweex.eyebrowssync;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.util.Log;
import com.qweex.utils.Crypt;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;


/********************* DB stuff for getting/setting saved servers *********************/
public class SavedServers {
    /** The file containing the databases. */
    private static final String DATABASE= "EyebrowsSync.db";
    /** One of the tables in the SQL database. */
    private static final String DATABASE_TABLE = "sites";
    /** The database for the app. */
    private static SQLiteDatabase database;
    /** A tool to help with the opening of the database. It's in the Android doc examples, yo.*/
    private static DatabaseOpenHelper databaseOpenHelper;

    /** needs to be called once
     * @param context The context to associate with the connector.
     * */
    public static void initialize(Context context)
    {
        databaseOpenHelper = new DatabaseOpenHelper(context, DATABASE, null, 1);
        Log.d("EyebrowsSync", "Initialized DB; " + getAll().getCount());
    }

    public static synchronized Cursor getAll() {
        open();
        Cursor c = database.query(DATABASE_TABLE, new String[]{"_id", "name", "host", "port", "ssl", "auth", "foreign_path", "local_path", "mask", "last_updated"},
                null, null, null, null, null);
        c.moveToFirst();
        close();
        return c;
    }

    public static synchronized Bundle get(Context context, String name) {
        open();
        Bundle b = new Bundle();
        try {
            Cursor c = database.query(DATABASE_TABLE, new String[]{"_id", "name", "host", "port", "ssl", "auth", "foreign_path", "local_path", "mask", "last_updated"},
                    "name=?", new String[] {name}, null, null, null);
            if(c.getCount()==0)
                return null;
            c.moveToFirst();
            b.putLong("id", c.getLong(c.getColumnIndex("_id")));
            b.putString("name", c.getString(c.getColumnIndex("name")));
            b.putString("host", c.getString(c.getColumnIndex("host")));
            b.putInt("port", c.getInt(c.getColumnIndex("port")));
            b.putBoolean("ssl", c.getInt(c.getColumnIndex("ssl"))>0);
            b.putString("foreign_path", c.getString(c.getColumnIndex("foreign_path")));
            b.putString("local_path", c.getString(c.getColumnIndex("local_path")));
            b.putString("mask", c.getString(c.getColumnIndex("mask")));
            b.putLong("last_updated", c.getLong(c.getColumnIndex("last_updated")));

            String auth = c.getString(c.getColumnIndex("auth"));
            if(auth!=null && auth.length()>0) {
                if(UserConfig.hasMasterPass(context))
                    b.putString("auth",
                            Crypt.decrypt(auth, UserConfig.masterKey)
                    );
                else
                    b.putString("auth", auth);
            }

        } catch(InvalidKeyException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }
        close();
        return b;
    }

    public static synchronized void remove(String name) {
        open();
        database.delete(DATABASE_TABLE, "name=?", new String[] {name});
        close();
    }

    public static synchronized boolean add(Context c, Bundle values) {
        try {
            open();
            ContentValues newFav = new ContentValues();
            newFav.put("name", values.getString("name"));

            newFav.put("host", values.getString("host"));
            newFav.put("port", values.getInt("port"));
            newFav.put("ssl", values.getBoolean("ssl") ? 1 : 0);
            if(values.containsKey("auth")) {
                if(UserConfig.hasMasterPass(c))
                    newFav.put("auth",
                            Crypt.encrypt(values.getString("auth"), UserConfig.masterKey)
                    );
                else
                    newFav.put("auth", values.getString("auth"));
            }
            newFav.put("foreign_path", values.getString("foreign_path"));
            newFav.put("local_path", values.getString("local_path"));
            newFav.put("mask", values.getString("mask"));
            return database.insert(DATABASE_TABLE, null, newFav)!=-1 && close();
        } catch(Exception e) {
            e.printStackTrace();
            close();
            return false;
        }
    }

    //true if successful
    public static synchronized boolean update(Context c, String oldname, Bundle values) {
        try {
            open();
            ContentValues newFav = new ContentValues();

            if(values.containsKey("name"))
                newFav.put("name", values.getString("name"));
            if(values.containsKey("host"))
                newFav.put("host", values.getString("host"));
            if(values.containsKey("port"))
                newFav.put("port", values.getInt("port"));
            if(values.containsKey("ssl"))
                newFav.put("ssl", values.getBoolean("ssl") ? 1 : 0);
            if(values.containsKey("foreign_path"))
                newFav.put("foreign_path", values.getString("foreign_path"));
            if(values.containsKey("local_path"))
                newFav.put("local_path", values.getString("local_path"));
            if(values.containsKey("last_updated"))
                newFav.put("last_updated", values.getLong("last_updated"));
            if(values.containsKey("mask"))
                newFav.put("mask", values.getString("mask"));

            if(values.containsKey("auth")) {
                if(UserConfig.hasMasterPass(c))
                    newFav.put("auth",
                            Crypt.encrypt(values.getString("auth"), UserConfig.masterKey)
                    );
                else
                    newFav.put("auth", values.getString("auth"));
            }


            return database.update(DATABASE_TABLE, newFav, "name=?", new String[]{ oldname})>0;
        } catch(Exception e) {
            e.printStackTrace();
            close();
            return false;
        }
    }


    /** Opens the database so that it can be read or written. */
    private static void open() throws SQLException
    {
        if(database!=null && database.isOpen())
            return;
        database = databaseOpenHelper.getWritableDatabase();
        databaseOpenHelper.onUpgrade(database, 0, 1);
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
        }

        @Override
        public void onCreate(SQLiteDatabase db)
        {
            String createQuery = "CREATE TABLE " + DATABASE_TABLE + " " +
                    "(_id integer primary key autoincrement" +
                    ", name TEXT unique" +
                    ", host TEXT" +
                    ", port INTEGER" +
                    ", ssl INTEGER" +
                    ", auth TEXT" +
                    ", foreign_path TEXT" +
                    ", local_path TEXT" +
                    ", mask TEXT" +
                    ", last_updated INTEGER" +
                    ");";
            db.execSQL(createQuery);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
        {
            //db.execSQL("DROP TABLE " + DATABASE_TABLE + ";");
        }
    }
}
