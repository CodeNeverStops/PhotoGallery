package io.github.uv_lab.photogallery;

import android.graphics.Bitmap;
import android.support.v4.util.LruCache;

/**
 * Created by youwei on 2016/11/15.
 */

public class BitmapCache {
    private LruCache<String, Bitmap> mMemoryCache;

    public BitmapCache() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize);
    }

    public void addBitmapToCache(String key, Bitmap bitmap) {
        mMemoryCache.put(key, bitmap);
    }

    public Bitmap getBitmapFromCache(String key) {
        return mMemoryCache.get(key);
    }

}
