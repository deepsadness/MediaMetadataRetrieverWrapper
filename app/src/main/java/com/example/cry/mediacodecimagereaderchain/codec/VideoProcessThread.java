package com.example.cry.mediacodecimagereaderchain.codec;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;

import java.lang.ref.WeakReference;
import java.util.ArrayList;


@RequiresApi(api = Build.VERSION_CODES.KITKAT)
public class VideoProcessThread extends HandlerThread {
    public static final String TAG = "VideoProcessThread";
    private final android.os.Handler handler;
    private final WeakReference<Context> contextWeakReference;

    public interface VideoListCallBack {
        void onComplete(ArrayList<String> videos);
    }

    public interface BitmapCallBack {
        void onComplete(Bitmap frame);
    }

    public VideoProcessThread(Context context) {
        super("VideoProcessThread");
        start();
        Looper looper = getLooper();
        handler = new android.os.Handler(looper);
        contextWeakReference = new WeakReference<>(context);
    }

    public void getFileList(final VideoListCallBack callBack) {
        //得到本地所有的Media
        handler.post(new Runnable() {
            @Override
            public void run() {
                ArrayList<String> list = new ArrayList<>();
                String[] projection = new String[]{MediaStore.Video.Media.DATA, MediaStore.Video.Media
                        .DURATION};
                Context context = contextWeakReference.get();
                if (context == null) {
                    return;
                }
                Cursor cursor = context.getContentResolver().query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null,
                        null, null);
                while (cursor.moveToNext()) {
                    String path = cursor
                            .getString(cursor
                                    .getColumnIndexOrThrow(MediaStore.Video.Media.DATA));
//                    long duration = cursor
//                            .getInt(cursor
//                                    .getColumnIndexOrThrow(MediaStore.Video.Media.DURATION));
                    // EntityVideo video = new EntityVideo(path, duration, getVideoThumbnail(path));
                    list.add(path);
                }
                cursor.close();

                if (callBack != null) {
                    callBack.onComplete(list);
                }
            }
        });
    }

    /*
      private boolean requestStop;
    private int interval = 1000;//间隔1S
    private long lastPresentationTimeUs;


        //设置采样的间隔
    public void setInterval(int interval) {
        this.interval = interval;
    }

    public void process(final String fileName, final BitmapCallBack callBack) {
        handler.post(new Runnable() {
            @Override
            public void run() {

                MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
                metadataRetriever.setDataSource(fileName);

                String duration = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                Log.d(TAG, "duration = " + duration);
                int durationMs = Integer.parseInt(duration);
                if (interval == 0) {
                    interval = 3;
                }
                //每秒取一次
                for (int i = 0; i < durationMs; i += interval) {
                    long start = System.nanoTime();
                    Log.d(TAG, "getFrameAtTime time = " + i);
                    //这里传入的是ms
                    Bitmap frameAtIndex = metadataRetriever.getFrameAtTime(i * 1000);
                    Bitmap frame = Bitmap.createScaledBitmap(frameAtIndex, frameAtIndex.getWidth() / 4, frameAtIndex.getHeight() / 4, false);
                    frameAtIndex.recycle();
                    long end = System.nanoTime();
                    long cost = end - start;
                    Log.d(TAG, "cost time in millis = " + (cost * 1f / 1000000));

                    if (callBack != null) {
                        callBack.onComplete(frame);
                    }
                }
                metadataRetriever.release();
            }
        });

    }

    public void process2(final String fileName, final BitmapCallBack callBack) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                MediaExtractor extractor = null;
                MediaCodec codec = null;
                try {
                    extractor = new MediaExtractor();
                    extractor.setDataSource(fileName);
                    int trackCount = extractor.getTrackCount();
                    MediaFormat videoFormat = null;
                    for (int i = 0; i < trackCount; i++) {
                        MediaFormat trackFormat = extractor.getTrackFormat(i);
                        if (trackFormat.getString(MediaFormat.KEY_MIME).contains("video")) {
                            videoFormat = trackFormat;
                            extractor.selectTrack(i);
                            break;
                        }
                    }
                    if (videoFormat == null) {
                        Log.d(TAG, "Can not get video format");
                        return;
                    }

                    int imageFormat = ImageFormat.YUV_420_888;
                    int colorFormat = COLOR_FormatYUV420Flexible;
                    videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
                    videoFormat.setInteger(MediaFormat.KEY_WIDTH, videoFormat.getInteger(MediaFormat.KEY_WIDTH));
                    videoFormat.setInteger(MediaFormat.KEY_HEIGHT, videoFormat.getInteger(MediaFormat.KEY_HEIGHT));

                    long duration = videoFormat.getLong(MediaFormat.KEY_DURATION);

                    codec = MediaCodec.createDecoderByType(videoFormat.getString(MediaFormat.KEY_MIME));
                    ImageReader imageReader = ImageReader
                            .newInstance(
                                    videoFormat.getInteger(MediaFormat.KEY_WIDTH),
                                    videoFormat.getInteger(MediaFormat.KEY_HEIGHT),
                                    imageFormat,
                                    3);
                    final ImageReaderHandlerThread imageReaderHandlerThread = new ImageReaderHandlerThread();

                    imageReader.setOnImageAvailableListener(new MyOnImageAvailableListener(callBack), imageReaderHandlerThread.getHandler());
                    codec.configure(videoFormat, imageReader.getSurface(), null, 0);
                    codec.start();
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    long timeOut = 5 * 1000;//10ms
                    boolean inputDone = false;
                    boolean outputDone = false;
                    ByteBuffer[] inputBuffers = null;
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
                        inputBuffers = codec.getInputBuffers();
                    }
                    //开始进行解码。
                    int count = 1;
                    while (!outputDone) {
                        if (requestStop) {
                            return;
                        }
                        if (!inputDone) {
                            //feed data
                            int inputBufferIndex = codec.dequeueInputBuffer(timeOut);
                            if (inputBufferIndex >= 0) {
                                ByteBuffer inputBuffer;
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                    inputBuffer = codec.getInputBuffer(inputBufferIndex);
                                } else {
                                    inputBuffer = inputBuffers[inputBufferIndex];
                                }
                                int sampleData = extractor.readSampleData(inputBuffer, 0);
                                if (sampleData > 0) {
                                    long sampleTime = extractor.getSampleTime();
                                    codec.queueInputBuffer(inputBufferIndex, 0, sampleData, sampleTime, 0);
                                    //继续
                                    if (interval == 0) {
                                        extractor.advance();
                                    } else {
                                        extractor.seekTo(count * interval * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                                        count++;
//                                        extractor.advance();
                                    }
                                } else {
                                    //小于0，说明读完了
                                    codec.queueInputBuffer(inputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                    inputDone = true;
                                    Log.d(TAG, "end of stream");
                                }
                            }
                        }
                        if (!outputDone) {
                            //get data
                            int status = codec.dequeueOutputBuffer(bufferInfo, timeOut);
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
                                    if (interval != 0) {
                                        if (diff < interval * 1000) {
                                            doRender = false;
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
                                codec.releaseOutputBuffer(status, doRender);
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (codec != null) {
                        codec.stop();
                        codec.release();
                    }
                    if (extractor != null) {
                        extractor.release();
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


    private static class MyOnImageAvailableListener implements ImageReader.OnImageAvailableListener {
        private final BitmapCallBack callBack;

        private MyOnImageAvailableListener(BitmapCallBack callBack) {
            this.callBack = callBack;
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
                    Bitmap bitmap = getBitmapScale(img, 4);
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
        private Bitmap getBitmapScale(Image img, int scale) {
            int width = img.getWidth() / scale;
            int height = img.getHeight() / scale;
            final byte[] bytesImage = getDataFromYUV420Scale(img, scale);
            Bitmap bitmap = null;
            bitmap = Bitmap.createBitmap(height, width, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytesImage));
            return bitmap;
        }

        private byte[] getDataFromYUV420Scale(Image image, int scale) {
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
                    scale
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

 */

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
/*    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
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

}*/


}
