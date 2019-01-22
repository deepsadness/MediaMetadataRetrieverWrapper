package com.cry.mediametaretriverwrapper;

import android.graphics.Bitmap;

/*
MediaMetadataRetriever wrapper
 */
public class MediaMetadataRetrieverWrapper {

    private RetrieverProcessThread retrieverProcessThread;

    public MediaMetadataRetrieverWrapper() {
        retrieverProcessThread = new RetrieverProcessThread();

    }

    public void forceFallBack(boolean force){
        retrieverProcessThread.forceFallBack(force);
    }

    /**
     * 当前简单的实现，只写了本地视频。
     *
     * @param fileName
     */
    public void setDataSource(String fileName) {
        retrieverProcessThread.setDataSource(fileName);
    }

    public void extractMetadata(int keyCode, RetrieverProcessThread.MetaCallBack callBack) {
        retrieverProcessThread.extractMetadata(keyCode, callBack);
    }

    /**
     * @param interval ms
     */
    public void getFramesInterval(long interval, RetrieverProcessThread.BitmapCallBack callBack) {
        retrieverProcessThread.getFramesInterval(interval, 1, callBack);
    }

    public void getFramesInterval(long interval, int scale, RetrieverProcessThread.BitmapCallBack callBack) {
        retrieverProcessThread.getFramesInterval(interval, scale, callBack);
    }

    public void getFrameAtTime(long timeUs, RetrieverProcessThread.BitmapCallBack callBack) {
        retrieverProcessThread.getFrameAtTime(timeUs, 1, callBack);
    }

    public void getFrameAtTime(long timeUs, int scale, RetrieverProcessThread.BitmapCallBack callBack) {
        retrieverProcessThread.getFrameAtTime(timeUs, scale, callBack);
    }


    public void stop() {
        retrieverProcessThread.stopGetFrames();
    }

    public void release() {
        retrieverProcessThread.release();
    }
}
