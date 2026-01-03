package io.benwiegand.attitude.man;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Set;

public class PrefMan {
    private static final String TAG = PrefMan.class.getSimpleName();

    private static final String PREF_FILE = "io.benwiegand.attitude_preferences";

    public static final String KEY_WIFI_CONNECT_ON_BOOT = "wifi_connect_on_boot";
    public static final String KEY_WIFI_CONNECT_ON_WAKE = "wifi_connect_on_wake";
    public static final String KEY_WIFI_NAME = "wifi_name";

    private final Context context;
    private SharedPreferences prefs = null;

    public PrefMan(Context context) {
        this.context = context;
    }

    private SharedPreferences getPrefs() {
        if (prefs != null) return prefs;
        prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        return prefs;
    }

    public boolean hasKey(String key) {
        return getPrefs().contains(key);
    }

    public <T> T read(Class<T> type, String key, T defaultValue) {
        assert type != null;

        SharedPreferences prefs = getPrefs();
        T result;

        if (defaultValue == null && !prefs.contains(key)) {
            return null;
        }

        // sea of cast warnings I know, but trust me bro it's fine
        if (type.equals(Integer.class)) {
            result = (T) (Integer) prefs.getInt(key, defaultValue != null ? (int) defaultValue : -1);
        } else if (type.equals(Boolean.class)) {
            result = (T) (Boolean) prefs.getBoolean(key, defaultValue != null ? (boolean) defaultValue : false);
        } else if (type.equals(Float.class)) {
            result = (T) (Float) prefs.getFloat(key, defaultValue != null ? (float) defaultValue : Float.NaN);
        } else if (type.equals(Long.class)) {
            result = (T) (Long) prefs.getLong(key, defaultValue != null ? (long) defaultValue : -1L);
        } else if (type.equals(String.class)) {
            result = (T) prefs.getString(key, defaultValue != null ? (String) defaultValue : null);
        } else if (type.equals(Set.class)) {
            // ok this one might actually not be fine but shhhhhhhh
            result = (T) prefs.getStringSet(key, defaultValue != null ? (Set<String>) defaultValue : null);
        } else {
            Log.wtf(TAG, "invalid preference type requested for read: " + type.getSimpleName());
            throw new IllegalArgumentException("invalid preference type");
        }
        return result;
    }

    private <T> SharedPreferences.Editor prepareWrite(Class<T> type, String key, T value) {
        if (value == null) return getPrefs().edit().remove(key);

        if (type.equals(Integer.class)) {
            return getPrefs().edit().putInt(key, (int) value);
        } else if (type.equals(Boolean.class)) {
            return getPrefs().edit().putBoolean(key, (boolean) value);
        } else if (type.equals(Float.class)) {
            return getPrefs().edit().putFloat(key, (float) value);
        } else if (type.equals(Long.class)) {
            return getPrefs().edit().putLong(key, (long) value);
        } else if (type.equals(String.class)) {
            return getPrefs().edit().putString(key, (String) value);
        } else if (type.equals(Set.class)) {
            return getPrefs().edit().putStringSet(key, (Set<String>) value);    // this is not fine
        } else {
            Log.wtf(TAG, "invalid preference type requested for write: " + type.getSimpleName());
            throw new IllegalArgumentException("invalid preference type");
        }
    }

    public <T> boolean writeNow(String key, T value) {
        if (value == null) {
            Log.w(TAG, "null value for write, removing from kv store. use removeNow() or removeLater() instead");
            assert false;
            return prepareWrite(null, key, null).commit();
        }

        return prepareWrite((Class<T>) value.getClass(), key, value).commit();  // this is fine
    }

    public <T> void writeLater(String key, T value) {
        if (value == null) {
            Log.w(TAG, "null value for write, removing from kv store. use removeNow() or removeLater() instead");
            assert false;
            prepareWrite(null, key, null).apply();
            return;
        }

        prepareWrite((Class<T>) value.getClass(), key, value).apply();  // this is fine
    }

    public boolean removeNow(String key) {
        return getPrefs().edit().remove(key).commit();
    }

    public void removeLater(String key) {
        getPrefs().edit().remove(key).apply();
    }

}
