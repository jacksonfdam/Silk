package com.afollestad.silk.cache;

import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Aidan Follestad (afollestad)
 */
class SilkCacheManagerBase<T extends SilkComparable> {

    public SilkCacheManagerBase(String cacheName, File cacheDir) {
        buffer = new ArrayList<T>();
        if (cacheName == null || cacheName.trim().isEmpty())
            cacheName = "default";
        mHandler = new Handler();
        if (cacheDir == null)
            cacheDir = new File(Environment.getExternalStorageDirectory(), "Silk");
        if (!cacheDir.exists())
            cacheDir.mkdirs();
        cacheFile = new File(cacheDir, cacheName.toLowerCase() + ".cache");
    }

    protected List<T> buffer;
    private File cacheFile;
    protected Handler mHandler;

    protected void log(String message) {
        Log.d("SilkCacheManager", message);
    }

    protected void runPriorityThread(Runnable runnable) {
        Thread t = new Thread(runnable);
        t.setPriority(Thread.MAX_PRIORITY);
        t.start();
    }

    protected void runOnUiThread(Runnable runnable) {
        mHandler.post(runnable);
    }

    protected File getCacheFile() {
        return cacheFile;
    }

    protected void reloadIfNeeded() {
        if (buffer.size() > 0) return;
        buffer = read();
    }

    private List<T> read() {
        try {
            final List<T> results = new ArrayList<T>();
            if (cacheFile.exists()) {
                FileInputStream fileInputStream = new FileInputStream(cacheFile);
                ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
                while (true) {
                    try {
                        final T item = (T) objectInputStream.readObject();
                        if (item != null) results.add(item);
                    } catch (EOFException eof) {
                        break;
                    }
                }
                objectInputStream.close();
            }
            log("Read " + results.size() + " items from " + cacheFile.getName());
            return results;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Commits all changes to the cache file. This is from the calling thread.
     */
    public boolean commit() throws Exception {
        if (buffer == null) return false;
        else if (buffer.size() == 0) {
            if (cacheFile.exists()) {
                log("Deleting: " + cacheFile.getName());
                return cacheFile.delete();
            }
            return true;
        }
        int subtraction = 0;
        FileOutputStream fileOutputStream = new FileOutputStream(cacheFile);
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
        for (T item : buffer) {
            if (item.shouldIgnore()) {
                subtraction++;
                continue;
            }
            objectOutputStream.writeObject(item);
        }
        objectOutputStream.close();
        log("Committed " + (buffer.size() - subtraction) + " items to " + cacheFile.getName());
        buffer = null;
        return true;
    }

    /**
     * Commits all changes to the cache file. This is run on a separate thread and the results are posted to a callback.
     */
    public void commitAsync(final SilkCacheManager.SimpleCommitCallback callback) {
        runPriorityThread(new Runnable() {
            @Override
            public void run() {
                try {
                    final boolean result = commit();
                    if (callback != null) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (callback instanceof SilkCacheManager.CommitCallback)
                                    ((SilkCacheManager.CommitCallback) callback).onCommitted(result);
                            }
                        });
                    }
                } catch (final Exception e) {
                    e.printStackTrace();
                    log("Cache find error: " + e.getMessage());
                    if (callback != null) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onError(e);
                            }
                        });
                    }
                }
            }
        });
    }
}