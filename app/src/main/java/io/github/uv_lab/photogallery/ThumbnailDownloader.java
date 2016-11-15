package io.github.uv_lab.photogallery;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by uv on 2016/11/12.
 */

public class ThumbnailDownloader<T> extends HandlerThread {
    private static final String TAG = "ThumbnailDownloader";
    private static final int MESSAGE_DOWNLOAD = 0;
    private static final int MESSAGE_PRELOAD = 1;

    private Boolean mHasQuit = false;
    private Handler mRequestHandler;
    private ConcurrentHashMap<T,String> mRequestMap = new ConcurrentHashMap<>();
    private Handler mResponseHandler;
    private ThumbnailDownloadListener<T> mThumbnailDownloadListener;
    private BitmapCache mCache;

    public interface ThumbnailDownloadListener<T> {
        void onThumbnailDownloaded(T target, Bitmap thumbnail);
    }

    public void setThumbnailDownloaderListener(ThumbnailDownloadListener<T> listener) {
        mThumbnailDownloadListener = listener;
    }

    public ThumbnailDownloader(Handler responseHandler) {
        super(TAG);
        mResponseHandler = responseHandler;
        mCache = new BitmapCache();
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
                } else if (msg.what == MESSAGE_PRELOAD) {
                    String url = (String) msg.obj;
                    try {
                        Bitmap cache = mCache.getBitmapFromCache(url);
                        if (cache == null) {
                            byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(url);
                            Bitmap bitmap = BitmapFactory
                                    .decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
                            mCache.addBitmapToCache(url, bitmap);
                            Log.i(TAG, "Bitmap preload");
                        }
                    } catch (IOException ioe) {

                    }
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

    public void preloadThumbnail(String url) {
        if (url == null) {
            return;
        }
        mRequestHandler.obtainMessage(MESSAGE_PRELOAD, url)
                .sendToTarget();
    }

    public void clearQueue() {
        mRequestHandler.removeMessages(MESSAGE_DOWNLOAD);
        mRequestHandler.removeMessages(MESSAGE_PRELOAD);
    }

    private void handleRequest(final T target) {
        try {
            final String url = mRequestMap.get(target);

            if (url == null) {
                return;
            }

            Bitmap cache = mCache.getBitmapFromCache(url);
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
            mCache.addBitmapToCache(url, bitmap);

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
}
