package io.github.uv_lab.photogallery;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.nfc.Tag;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.LruCache;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by uv on 2016/11/12.
 */

public class ThumbnailDownloader<T> extends HandlerThread {
    private static final String TAG = "ThumbnailDownloader";
    private static final int MESSAGE_DOWNLOAD = 0;

    private Boolean mHasQuit = false;
    private Handler mRequestHandler;
    private ConcurrentHashMap<T,String> mRequestMap = new ConcurrentHashMap<>();
    private Handler mResponseHandler;
    private ThumbnailDownloadListener<T> mThumbnailDownloadListener;
    private LruCache<String, Bitmap> mMemoryCache;

    public interface ThumbnailDownloadListener<T> {
        void onThumbnailDownloaded(T target, Bitmap thumbnail);
    }

    public void setThumbnailDownloaderListener(ThumbnailDownloadListener<T> listener) {
        mThumbnailDownloadListener = listener;
    }

    public ThumbnailDownloader(Handler responseHandler) {
        super(TAG);
        mResponseHandler = responseHandler;

        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize);
    }

    @Override
    protected void onLooperPrepared() {
        mRequestHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_DOWNLOAD) {
                    T target = (T) msg.obj;
                    Log.i(TAG, "Got a request for URL: " + mRequestMap.get(target));
                    handleRequest(target);
                }
            }
        };
    }

    @Override
    public boolean quit() {
        mHasQuit = true;
        return super.quit();
    }

    public void queueThumbnail(T target, String url) {
        Log.i(TAG, "Got a URL: " + url);

        if (url == null) {
            mRequestMap.remove(target);
        } else {
            mRequestMap.put(target, url);
            mRequestHandler.obtainMessage(MESSAGE_DOWNLOAD, target)
                    .sendToTarget();
        }
    }

    public void clearQueue() {
        mRequestHandler.removeMessages(MESSAGE_DOWNLOAD);
    }

    private void handleRequest(final T target) {
        try {
            final String url = mRequestMap.get(target);

            if (url == null) {
                return;
            }

            Bitmap cache = getBitmapFromCache(url);
            Bitmap tmp = null;

            if (cache == null) {
                byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(url);
                tmp = BitmapFactory
                        .decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
                Log.i(TAG, "Bitmap created");
            } else {
                tmp = cache;
                Log.i(TAG, "Bitmap cached");
            }

            final Bitmap bitmap = tmp;
            addBitmapToCache(url, bitmap);

            mResponseHandler.post(new Runnable() {
                public void run() {
                    if (mRequestMap.get(target) != url || mHasQuit) {
                        return;
                    }

                    mRequestMap.remove(target);
                    mThumbnailDownloadListener.onThumbnailDownloaded(target, bitmap);
                }
            });
        } catch (IOException ioe) {
            Log.e(TAG, "Error downloading image", ioe);
        }
    }

    private void addBitmapToCache(String key, Bitmap bitmap) {
        mMemoryCache.put(key, bitmap);
    }

    private Bitmap getBitmapFromCache(String key) {
        return mMemoryCache.get(key);
    }
}
