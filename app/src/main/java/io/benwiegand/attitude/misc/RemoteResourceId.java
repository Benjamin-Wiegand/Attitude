package io.benwiegand.attitude.misc;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.util.Log;

import androidx.annotation.Nullable;

public class RemoteResourceId {
    private static final String TAG = RemoteResourceId.class.getSimpleName();

    private int id = 0;
    private final String name;


    public RemoteResourceId(String name) {
        this.name = name;
    }

    /**
     * returns cached resource ID or looks it up in the provided resources if not already cached.
     * if the lookup fails, 0 is returned.
     * @param resources resources to look for id in
     * @return the resource id, or 0 if not found
     */
    public int getOrFindId(Resources resources) {
        if (id == 0) id = findResourceId(resources);
        return id;
    }

    @SuppressLint("DiscouragedApi") // can't reference android internal ids
    private int findResourceId(Resources resources) {
        id = resources.getIdentifier(name, null, null);
        Log.d(TAG, name + " resolved to: " + id);
        if (id == 0) Log.w(TAG, "no resource ID for " + name);
        return id;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof RemoteResourceId rri) {
            if (rri.id != 0 && id != 0) return rri.id == id;    // id preferred
            return rri.name.equals(name);
        }

        return super.equals(obj);
    }
}
