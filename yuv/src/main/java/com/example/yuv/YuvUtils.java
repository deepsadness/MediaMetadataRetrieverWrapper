package com.example.yuv;

import java.nio.ByteBuffer;

public class YuvUtils {
    static {
        System.loadLibrary("yuvconvert-lib");
    }

    /**
     * 将I420转化为NV21
     *
     * @param i420Src 原始I420数据
     * @param nv21Src 转化后的NV21数据
     * @param width   输出的宽
     * @param width   输出的高
     **/
    public static native void yuvI420ToNV21(byte[] i420Src, byte[] nv21Src, int width, int height);
    public static native void yuvI420ToNV212(byte[] nv21, ByteBuffer y_buffer, int rowStride, ByteBuffer buffer1, int rowStride1, ByteBuffer buffer2, int rowStride2, int width, int height);
    public static native void yuvI420ToABGR(byte[] argb, ByteBuffer y_buffer, int rowStride, ByteBuffer buffer1, int rowStride1, ByteBuffer buffer2, int rowStride2, int width, int height) ;
    public static native void yuvI420ToABGRWithScale(byte[] argb, ByteBuffer y_buffer, int rowStride, ByteBuffer buffer1, int rowStride1, ByteBuffer buffer2, int rowStride2, int width, int height,int scale) ;
}
