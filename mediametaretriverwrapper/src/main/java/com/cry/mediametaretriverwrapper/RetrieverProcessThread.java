package com.cry.mediametaretriverwrapper;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;

import com.example.yuv.YuvUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;

/**
 * 将文件通过MediaCodec解码。
 * 输出到ImageReader当中。来获取截图。
 * <p>
 * 一系列测试下来，应该是MetaRetriever更快。
 * 之所以开始慢的原因，估计是因为一次生成的Bitmap是原始的尺寸。所以慢了。
 * <p>
 * 当生成小图的时候，因为我们实现做了缩放的处理。所以变得更快了。
 * <p>
 * 但是当Scale为2时，用MediaCodec的速度就明显加快了。
 * 需要先去看一下MetaRetriever的原来
 */
public class RetrieverProcessThread extends HandlerThread {
    public static final String TAG = "RetrieverProcessThread";
    private final Handler handler;
    private boolean requestStop;
    private long intervalMs = 1000;//ms
    private long lastPresentationTimeUs;

    private boolean forceBack;

    private MediaExtractor extractor;
    private MediaFormat videoFormat;
    private MediaCodec mediaCodec;
    private ImageReader imageReader;
    private ImageReaderHandlerThread imageReaderHandlerThread;

    private MediaMetadataRetriever metadataRetriever;
    private long duration;


    public void forceFallBack(boolean force) {
        this.forceBack = force;
        handler.post(new Runnable() {
            @Override
            public void run() {
                _release();
                _init();
            }
        });
    }

    public interface BitmapCallBack {
        void onComplete(Bitmap frame);
    }

    public interface MetaCallBack {
        void onMeta(String meta);
    }

    public RetrieverProcessThread() {
        super("RetrieverProcessThread");
        start();
        Looper looper = getLooper();
        handler = new Handler(looper);
        init();
    }

    private boolean isExtractor() {
        return !forceBack && android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    private void init() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                _init();
            }
        });
    }

    private void _init() {
        if (isExtractor()) {
            extractor = new MediaExtractor();
        } else {
            metadataRetriever = new MediaMetadataRetriever();
        }
        requestStop = false;
    }

    public void setDataSource(final String fileName) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (isExtractor()) {
                    try {
                        //设置fileName
                        extractor.setDataSource(fileName);
                        //设置选中的
                        int trackCount = extractor.getTrackCount();
                        for (int i = 0; i < trackCount; i++) {
                            MediaFormat trackFormat = extractor.getTrackFormat(i);
                            if (trackFormat.getString(MediaFormat.KEY_MIME).contains("video")) {
                                videoFormat = trackFormat;
                                extractor.selectTrack(i);
                                break;
                            }
                        }
                        if (videoFormat == null) {
                            throw new IllegalArgumentException("Can not get video format");
                        }
                        int imageFormat = ImageFormat.YUV_420_888;
                        int colorFormat = COLOR_FormatYUV420Flexible;
                        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
                        videoFormat.setInteger(MediaFormat.KEY_WIDTH, videoFormat.getInteger(MediaFormat.KEY_WIDTH));
                        videoFormat.setInteger(MediaFormat.KEY_HEIGHT, videoFormat.getInteger(MediaFormat.KEY_HEIGHT));

                        duration = videoFormat.getLong(MediaFormat.KEY_DURATION);

                        mediaCodec = MediaCodec.createDecoderByType(videoFormat.getString(MediaFormat.KEY_MIME));
                        imageReader = ImageReader
                                .newInstance(
                                        videoFormat.getInteger(MediaFormat.KEY_WIDTH),
                                        videoFormat.getInteger(MediaFormat.KEY_HEIGHT),
                                        imageFormat,
                                        3);
                        imageReaderHandlerThread = new ImageReaderHandlerThread();
                        mediaCodec.configure(videoFormat, imageReader.getSurface(), null, 0);
                        mediaCodec.start();

                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new IllegalArgumentException(e.getMessage());
                    }
                } else {
                    metadataRetriever.setDataSource(fileName);
                }
            }
        });
    }


    public void extractMetadata(final int keyCode, final MetaCallBack callBack) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                String meta = null;
                switch (keyCode) {
                    case MediaMetadataRetriever.METADATA_KEY_DURATION:
                        if (isExtractor()) {
                            if (videoFormat != null) {
                                meta = "" + videoFormat.getLong(MediaFormat.KEY_DURATION);
                            }
                        } else {
                            meta = metadataRetriever.extractMetadata(keyCode);
                        }
                        break;
                    default:
                        meta = metadataRetriever.extractMetadata(keyCode);
                }

                if (callBack != null) {
                    callBack.onMeta(meta);
                }
            }
        });
    }

    public void getFramesInterval(final long interval, final int scale, final BitmapCallBack callBack) {
        this.intervalMs = interval;
        handler.post(new Runnable() {
            @Override
            public void run() {

                if (isExtractor()) {
                    if (videoFormat == null) {
                        throw new IllegalArgumentException("Please setDataSource first");
                    }
                    int rotation = videoFormat.getInteger(MediaFormat.KEY_ROTATION);
                    imageReader.setOnImageAvailableListener(new MyOnImageAvailableListener(rotation, scale, callBack), imageReaderHandlerThread.getHandler());
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    processByExtractor(scale, callBack);
                } else {
                    if (metadataRetriever == null) {
                        throw new IllegalArgumentException("Please setDataSource first");
                    }
                    processByRetriever(scale, callBack);
                }
            }
        });

    }

    public void stopGetFrames() {
        requestStop = true;
    }


    public void release() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                _release();
            }
        });
    }

    private void _release() {
        if (metadataRetriever != null) {
            metadataRetriever.release();
            metadataRetriever = null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (!requestStop) {
                requestStop = true;
            }


            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }

            if (mediaCodec != null) {
                mediaCodec.stop();
                mediaCodec.release();
                mediaCodec = null;
            }

            if (extractor != null) {
                extractor.release();
                extractor = null;
            }
        }

    }

    private void processByRetriever(int scale, MediaMetadataRetriever metadataRetriever, BitmapCallBack callBack) {
        String durationS = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        Log.d(TAG, "duration = " + durationS);
        duration = Integer.parseInt(durationS);
        if (intervalMs == 0) {
            intervalMs = 3;
        }
        //每秒取一次
        for (int i = 0; i < duration; i += intervalMs) {
            long start = System.nanoTime();
            Log.d(TAG, "getFrameAtTime time = " + i);
            //这里传入的是ms
            Bitmap frameAtIndex = metadataRetriever.getFrameAtTime(i * 1000);
            Bitmap frame;
            if (scale == 1) {
                frame = frameAtIndex;
            } else {
                frame = Bitmap.createScaledBitmap(frameAtIndex, frameAtIndex.getWidth() / scale, frameAtIndex.getHeight() / scale, false);
                frameAtIndex.recycle();
            }
            long end = System.nanoTime();
            long cost = end - start;
            Log.d(TAG, "cost time in millis = " + (cost * 1f / 1000000));

            if (callBack != null) {
                callBack.onComplete(frame);
            }
        }
    }

    private void processByRetriever(int scale, BitmapCallBack callBack) {
        processByRetriever(scale, metadataRetriever, callBack);
    }


    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void processByExtractor(int scale, BitmapCallBack callBack) {
        try {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            long timeOut = 5 * 1000;//5ms
            boolean inputDone = false;
            boolean outputDone = false;
            ByteBuffer[] inputBuffers = null;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                inputBuffers = mediaCodec.getInputBuffers();
            }
            //开始进行解码。
            int count = 1;
            while (!outputDone) {
                if (requestStop) {
                    return;
                }
                if (!inputDone) {
                    //feed data
                    int inputBufferIndex = mediaCodec.dequeueInputBuffer(timeOut);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                        } else {
                            inputBuffer = inputBuffers[inputBufferIndex];
                        }
                        int sampleData = extractor.readSampleData(inputBuffer, 0);
                        if (sampleData > 0) {
                            long sampleTime = extractor.getSampleTime();
                            mediaCodec.queueInputBuffer(inputBufferIndex, 0, sampleData, sampleTime, 0);
                            //继续
                            if (intervalMs == 0) {
                                extractor.advance();
                            } else {
                                if (count * intervalMs * 1000 > duration) {
                                    extractor.advance();
                                } else {
                                    extractor.seekTo(count * intervalMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                                    count++;
                                }
//                                        extractor.advance();
                            }
                        } else {
                            //小于0，说明读完了
                            mediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                            Log.d(TAG, "end of stream");
                        }
                    }
                }
                if (!outputDone) {
                    //get data
                    int status = mediaCodec.dequeueOutputBuffer(bufferInfo, timeOut);
                    if (status ==
                            MediaCodec.INFO_TRY_AGAIN_LATER) {
                        //继续
                    } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        //开始进行解码
                    } else if (status == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        //同样啥都不做
                    } else {
                        //在这里判断，当前编码器的状态
                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Log.d(TAG, "output EOS");
                            outputDone = true;
                        }
                        boolean doRender = (bufferInfo.size != 0);
                        long presentationTimeUs = bufferInfo.presentationTimeUs;
                        if (lastPresentationTimeUs == 0) {
                            lastPresentationTimeUs = presentationTimeUs;
                        } else {
                            long diff = presentationTimeUs - lastPresentationTimeUs;
                            if (intervalMs != 0) {
                                if (diff / 1000 < (intervalMs - 10)) {
                                    long lastDiff = duration - presentationTimeUs;
                                    Log.d(TAG, "duration=" + duration + ", lastDiff=" + lastDiff);
                                    if (lastDiff < 50 * 1000 && diff > 0) {//离最后50ms的
                                        //输出最后一帧.强制输出最后一帧附近的帧的话，会比用metaRetiriever多一帧
                                        lastPresentationTimeUs = duration;
                                    } else {
                                        doRender = false;
                                    }
                                } else {
                                    lastPresentationTimeUs = presentationTimeUs;
                                }
                                Log.d(TAG,
                                        "diff time in ms =" + diff / 1000);
                            }
                        }
                        //有数据了.因为会直接传递给Surface，所以说明都不做好了
                        Log.d(TAG, "surface decoder given buffer " + status +
                                " (size=" + bufferInfo.size + ")" + ",doRender = " + doRender + ", presentationTimeUs=" + presentationTimeUs);
                        //直接送显就可以了
                        mediaCodec.releaseOutputBuffer(status, doRender);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //暂时不做处理
//            if (mediaCodec != null) {
//                mediaCodec.stop();
//                mediaCodec.release();
//            }
//            if (extractor != null) {
//                extractor.release();
//            }
        }


    }

    public void getFrameAtTime(final long timeUs, final int scale, final BitmapCallBack callBack) {
        handler.post(new Runnable() {
            @Override
            public void run() {

                if (isExtractor()) {
                    if (videoFormat == null) {
                        throw new IllegalArgumentException("Please setDataSource first");
                    }
                    int rotation = videoFormat.getInteger(MediaFormat.KEY_ROTATION);
                    imageReader.setOnImageAvailableListener(new MyOnImageAvailableListener(rotation, scale, callBack), imageReaderHandlerThread.getHandler());
                    extractor.seekTo(timeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    try {
                        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                        long timeOut = 5 * 1000;//5ms
                        boolean inputDone = false;
                        boolean outputDone = false;
                        ByteBuffer[] inputBuffers = null;
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                            inputBuffers = mediaCodec.getInputBuffers();
                        }
                        while (!outputDone) {
                            if (requestStop) {
                                return;
                            }
                            if (!inputDone) {
                                //feed data
                                int inputBufferIndex = mediaCodec.dequeueInputBuffer(timeOut);
                                if (inputBufferIndex >= 0) {
                                    ByteBuffer inputBuffer;
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                        inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                                    } else {
                                        inputBuffer = inputBuffers[inputBufferIndex];
                                    }
                                    int sampleData = extractor.readSampleData(inputBuffer, 0);
                                    if (sampleData > 0) {
                                        long sampleTime = extractor.getSampleTime();
                                        mediaCodec.queueInputBuffer(inputBufferIndex, 0, sampleData, sampleTime, 0);
                                        //继续
                                        extractor.advance();
                                    } else {
                                        //小于0，说明读完了
                                        mediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                        inputDone = true;
                                        Log.d(TAG, "end of stream");
                                    }
                                }
                            }
                            if (!outputDone) {
                                //get data
                                int status = mediaCodec.dequeueOutputBuffer(bufferInfo, timeOut);
                                if (status ==
                                        MediaCodec.INFO_TRY_AGAIN_LATER) {
                                    //继续
                                } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                    //开始进行解码
                                } else if (status == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                                    //同样啥都不做
                                } else {
                                    //在这里判断，当前编码器的状态
                                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                        Log.d(TAG, "output EOS");
                                        outputDone = true;
                                    }
                                    outputDone = true;
                                    boolean doRender = (bufferInfo.size != 0);
                                    long presentationTimeUs = bufferInfo.presentationTimeUs;
//                                    if (lastPresentationTimeUs == 0) {
//                                        lastPresentationTimeUs = presentationTimeUs;
//                                    } else {
//                                        long diff = presentationTimeUs - lastPresentationTimeUs;
//                                        if (intervalMs != 0) {
//                                            if (diff < intervalMs * 1000) {
//                                                doRender = false;
//                                            } else {
//                                                lastPresentationTimeUs = presentationTimeUs;
//                                            }
//                                            Log.d(TAG,
//                                                    "diff time in ms =" + diff / 1000);
//                                        }
//                                    }
                                    //有数据了.因为会直接传递给Surface，所以说明都不做好了
                                    Log.d(TAG, "surface decoder given buffer " + status +
                                            " (size=" + bufferInfo.size + ")" + ",doRender = " + doRender + ", presentationTimeUs=" + presentationTimeUs);
                                    //直接送显就可以了
                                    mediaCodec.releaseOutputBuffer(status, doRender);

                                }
                            }
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                } else {
                    if (metadataRetriever == null) {
                        throw new IllegalArgumentException("Please setDataSource first");
                    }
                    Bitmap frameAtIndex = metadataRetriever.getFrameAtTime(timeUs);
                    if (callBack != null) {
                        callBack.onComplete(frameAtIndex);
                    }
                }
            }
        });


    }

    private class ImageReaderHandlerThread extends HandlerThread {

        private final Handler handler;

        public ImageReaderHandlerThread() {
            super("ImageReader");
            start();
            Looper looper = getLooper();
            handler = new Handler(looper);
        }

        public Handler getHandler() {
            return handler;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private static class MyOnImageAvailableListener implements ImageReader.OnImageAvailableListener {
        private final BitmapCallBack callBack;
        private int scale;
        private int rotation;

        private MyOnImageAvailableListener(int rotation, int scale, BitmapCallBack callBack) {
            this.callBack = callBack;
            this.scale = scale;
            this.rotation = rotation;
        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.i(TAG, "in OnImageAvailable");
            Image img = null;
            try {
                img = reader.acquireLatestImage();
                if (img != null) {
                    //这里得到的YUV的数据。需要将YUV的数据变成Bitmap
                    Image.Plane[] planes = img.getPlanes();
                    if (planes[0].getBuffer() == null) {
                        return;
                    }

//                    Bitmap bitmap = getBitmap(img);
                    Bitmap bitmap = getBitmapScale(img, scale, rotation);
//                    Bitmap bitmap = getBitmapFromNv21(img);
                    if (callBack != null && bitmap != null) {
                        Log.d(TAG, "onComplete bitmap ");
                        callBack.onComplete(bitmap);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (img != null) {
                    img.close();
                }
            }

        }

        @NonNull
        private Bitmap getBitmapScale(Image img, int scale, int rotation) {
            int width = img.getWidth() / scale;
            int height = img.getHeight() / scale;
            final byte[] bytesImage = getDataFromYUV420Scale(img, scale, rotation);
            Bitmap bitmap;
            if (rotation == 90 || rotation == 270) {
                bitmap = Bitmap.createBitmap(height, width, Bitmap.Config.ARGB_8888);
            } else {
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            }
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytesImage));
            return bitmap;
        }

        private byte[] getDataFromYUV420Scale(Image image, int scale, int rotation) {
            int width = image.getWidth();
            int height = image.getHeight();
            // Read image data
            Image.Plane[] planes = image.getPlanes();

            byte[] argb = new byte[width / scale * height / scale * 4];

            //值得注意的是在Java层传入byte[]以RGBA顺序排列时，libyuv是用ABGR来表示这个排列
            //libyuv表示的排列顺序和Bitmap的RGBA表示的顺序是反向的。
            // 所以实际要调用libyuv::ABGRToI420才能得到正确的结果。
            YuvUtils.yuvI420ToABGRWithScale(
                    argb,
                    planes[0].getBuffer(), planes[0].getRowStride(),
                    planes[1].getBuffer(), planes[1].getRowStride(),
                    planes[2].getBuffer(), planes[2].getRowStride(),
                    width, height,
                    scale,
                    rotation
            );
            return argb;
        }


        @NonNull
        private Bitmap getBitmap(Image img) {
            int width = img.getWidth();
            int height = img.getHeight();
            final byte[] bytesImage = getDataFromYUV420(img);
            Bitmap bitmap = null;
            bitmap = Bitmap.createBitmap(height, width, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytesImage));
            return bitmap;
        }

        private byte[] getDataFromYUV420(Image image) {
            int width = image.getWidth();
            int height = image.getHeight();
            // Read image data
            Image.Plane[] planes = image.getPlanes();

            byte[] argb = new byte[width * height * 4];

            //值得注意的是在Java层传入byte[]以RGBA顺序排列时，libyuv是用ABGR来表示这个排列
            //libyuv表示的排列顺序和Bitmap的RGBA表示的顺序是反向的。
            // 所以实际要调用libyuv::ABGRToI420才能得到正确的结果。
            YuvUtils.yuvI420ToABGR(
                    argb,
                    planes[0].getBuffer(), planes[0].getRowStride(),
                    planes[1].getBuffer(), planes[1].getRowStride(),
                    planes[2].getBuffer(), planes[2].getRowStride(),
                    width, height
            );
            return argb;
        }

        private Bitmap getBitmapFromNv21(Image image) {
            int width = image.getWidth();
            int height = image.getHeight();
            byte[] nv21 = getDataFromYUV4202(image);
            return generateBitmap(nv21, width, height);

        }

        private byte[] getDataFromYUV4202(Image image) {
            int width = image.getWidth();
            int height = image.getHeight();
            // Read image data
            Image.Plane[] planes = image.getPlanes();

            byte[] nv21 = new byte[width * height * 3 / 2];

            YuvUtils.yuvI420ToNV212(
                    nv21,
                    planes[0].getBuffer(), planes[0].getRowStride(),
                    planes[1].getBuffer(), planes[1].getRowStride(),
                    planes[2].getBuffer(), planes[2].getRowStride(),
                    width, height
            );
            return nv21;
        }

        private Bitmap generateBitmap(byte[] data, int width, int height) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
//      only support ImageFormat.NV21 and ImageFormat.YUY2 for now
            YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, width, height, null);
//        YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, width, height, null);
            yuvImage.compressToJpeg(new Rect(0, 0, width, height), 50, out);
            byte[] imageBytes = out.toByteArray();
            Bitmap image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            return image;
        }

        private void dumpYUVFile(long timestamp, byte[] data) {
            File externalStoragePublicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
            File file = new File(externalStoragePublicDirectory, "yuv/" + timestamp + ".yuv");
            File parentFile = file.getParentFile();
            if (!parentFile.exists()) {
                parentFile.mkdirs();
            }
            dumpFile(file.getAbsolutePath(), data);
        }

        private void dumpARGBFile(long timestamp, byte[] data) {
            File externalStoragePublicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
            File file = new File(externalStoragePublicDirectory, "argb/" + timestamp + ".argb");
            File parentFile = file.getParentFile();
            if (!parentFile.exists()) {
                parentFile.mkdirs();
            }
            dumpFile(file.getAbsolutePath(), data);
        }

        /**
         * Get a byte array image data from an Image object.
         * <p>
         * Read data from all planes of an Image into a contiguous unpadded,
         * unpacked 1-D linear byte array, such that it can be write into disk, or
         * accessed by software conveniently. It supports YUV_420_888/NV21/YV12
         * input Image format.
         * </p>
         * <p>
         * For YUV_420_888/NV21/YV12/Y8/Y16, it returns a byte array that contains
         * the Y plane data first, followed by U(Cb), V(Cr) planes if there is any
         * (xstride = width, ystride = height for chroma and luma components).
         * </p>
         */
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        private byte[] getDataFromImage(Image image) {
//        assertNotNull("Invalid image:", image);
            Rect crop = image.getCropRect();
            int format = image.getFormat();
            int width = crop.width();
            int height = crop.height();
            int rowStride, pixelStride;
            byte[] data = null;
            // Read image data
            Image.Plane[] planes = image.getPlanes();
//        assertTrue("Fail to get image planes", planes != null && planes.length > 0);
            // Check image validity
//        checkAndroidImageFormat(image);
            ByteBuffer buffer = null;
            int offset = 0;
            data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
            byte[] rowData = new byte[planes[0].getRowStride()];
            Log.v(TAG, "get data from " + planes.length + " planes");
            for (int i = 0; i < planes.length; i++) {
                int shift = (i == 0) ? 0 : 1;
                buffer = planes[i].getBuffer();
                rowStride = planes[i].getRowStride();
                pixelStride = planes[i].getPixelStride();
//            assertTrue("pixel stride " + pixelStride + " is invalid", pixelStride > 0);
                Log.v(TAG, "pixelStride " + pixelStride);
                Log.v(TAG, "rowStride " + rowStride);
                Log.v(TAG, "width " + width);
                Log.v(TAG, "height " + height);

                // For multi-planar yuv images, assuming yuv420 with 2x2 chroma subsampling.
                int w = crop.width() >> shift;
                int h = crop.height() >> shift;
                buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
//            assertTrue("rowStride " + rowStride + " should be >= width " + w, rowStride >= w);
                for (int row = 0; row < h; row++) {
                    int bytesPerPixel = ImageFormat.getBitsPerPixel(format) / 8;
                    int length;
                    if (pixelStride == bytesPerPixel) {
                        // Special case: optimized read of the entire row
                        length = w * bytesPerPixel;
                        buffer.get(data, offset, length);
                        offset += length;
                    } else {
                        // Generic case: should work for any pixelStride but slower.
                        // Use intermediate buffer to avoid read byte-by-byte from
                        // DirectByteBuffer, which is very bad for performance
                        length = (w - 1) * pixelStride + bytesPerPixel;
                        buffer.get(rowData, 0, length);
                        for (int col = 0; col < w; col++) {
                            data[offset++] = rowData[col * pixelStride];
                        }
                    }
                    // Advance buffer the remainder of the row stride
                    if (row < h - 1) {
                        buffer.position(buffer.position() + rowStride - length);
                    }
                }
                Log.v(TAG, "Finished reading data from plane " + i);
            }
            return data;
        }

        private static void dumpFile(String fileName, byte[] data) {
//        assertNotNull("fileName must not be null", fileName);
//        assertNotNull("data must not be null", data);
            FileOutputStream outStream;
            try {
                Log.v(TAG, "output will be saved as " + fileName);
                outStream = new FileOutputStream(fileName);
            } catch (IOException ioe) {
                throw new RuntimeException("Unable to create debug output file " + fileName, ioe);
            }
            try {
                outStream.write(data);
                outStream.close();
            } catch (IOException ioe) {
                throw new RuntimeException("failed writing data to file " + fileName, ioe);
            }
        }

    }


}
