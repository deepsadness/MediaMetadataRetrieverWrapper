#include <jni.h>
#include <string>
#include <iostream>
#include <android/log.h>
//引入头文件
extern "C"
{
#include "libyuv.h"
}
//定义日志宏变量
#define LOGI(FORMAT, ...) __android_log_print(ANDROID_LOG_INFO,"ZZX",FORMAT,##__VA_ARGS__);
#define LOGE(FORMAT, ...) __android_log_print(ANDROID_LOG_ERROR,"ZZX",FORMAT,##__VA_ARGS__);

extern "C"
JNIEXPORT void JNICALL
Java_com_example_yuv_YuvUtils_yuvI420ToNV21(JNIEnv *env, jclass type, jbyteArray i420Src_,
                                            jbyteArray nv21Src_, jint width, jint height) {
    jbyte *src_i420_data = env->GetByteArrayElements(i420Src_, NULL);
    jbyte *src_nv21_data = env->GetByteArrayElements(nv21Src_, NULL);

    jint src_y_size = width * height;
    jint src_u_size = (width >> 1) * (height >> 1);

    jbyte *src_i420_y_data = src_i420_data;
    jbyte *src_i420_u_data = src_i420_data + src_y_size;
    jbyte *src_i420_v_data = src_i420_data + src_y_size + src_u_size;

    jbyte *src_nv21_y_data = src_nv21_data;
    jbyte *src_nv21_vu_data = src_nv21_data + src_y_size;


    libyuv::I420ToNV21(
            (uint8_t *) src_i420_y_data, width,
            (uint8_t *) src_i420_u_data, width >> 1,
            (uint8_t *) src_i420_v_data, width >> 1,
            (uint8_t *) src_nv21_y_data, width,
            (uint8_t *) src_nv21_vu_data, width,
            width, height);

    env->ReleaseByteArrayElements(i420Src_, src_i420_data, 0);
    env->ReleaseByteArrayElements(nv21Src_, src_nv21_data, 0);
}


extern "C"
JNIEXPORT void JNICALL
Java_com_example_yuv_YuvUtils_yuvI420ToNV212(JNIEnv *env, jclass type, jbyteArray nv21_,
                                             jobject y_buffer, jint y_rowStride, jobject u_buffer,
                                             jint u_rowStride, jobject v_buffer, jint v_rowStride,
                                             jint width, jint height) {
    jbyte *nv21 = env->GetByteArrayElements(nv21_, NULL);
    uint8_t *srcYPtr = reinterpret_cast<uint8_t *>(env->GetDirectBufferAddress(y_buffer));
    uint8_t *srcUPtr = reinterpret_cast<uint8_t *>(env->GetDirectBufferAddress(u_buffer));
    uint8_t *srcVPtr = reinterpret_cast<uint8_t *>(env->GetDirectBufferAddress(v_buffer));


    jint src_y_size = width * height;
    jbyte *src_nv21_y_data = nv21;
    jbyte *src_nv21_vu_data = nv21 + src_y_size;
    libyuv::I420ToNV21(
            srcYPtr, y_rowStride,
            srcUPtr, u_rowStride,
            srcVPtr, v_rowStride,
            (uint8_t *) src_nv21_y_data, width,
            (uint8_t *) src_nv21_vu_data, width,
            width, height
    );

    env->ReleaseByteArrayElements(nv21_, nv21, 0);
}


extern "C"
JNIEXPORT void JNICALL
Java_com_example_yuv_YuvUtils_yuvI420ToABGR(JNIEnv *env, jclass type, jbyteArray argb_,
                                            jobject y_buffer, jint y_rowStride,
                                            jobject u_buffer, jint u_rowStride,
                                            jobject v_buffer, jint v_rowStride,
                                            jint width, jint height) {
    jbyte *argb = env->GetByteArrayElements(argb_, NULL);


    uint8_t *srcYPtr = reinterpret_cast<uint8_t *>(env->GetDirectBufferAddress(y_buffer));
    uint8_t *srcUPtr = reinterpret_cast<uint8_t *>(env->GetDirectBufferAddress(u_buffer));
    uint8_t *srcVPtr = reinterpret_cast<uint8_t *>(env->GetDirectBufferAddress(v_buffer));

    jbyte *temp_y = new jbyte[width * height * 3 / 2];
    jbyte *temp_u = temp_y + width * height;
    jbyte *temp_v = temp_y + width * height + width * height / 4;

    libyuv::I420Rotate(
            srcYPtr, y_rowStride,
            srcUPtr, u_rowStride,
            srcVPtr, v_rowStride,

            (uint8_t *) temp_y, height,
            (uint8_t *) temp_u, height >> 1,
            (uint8_t *) temp_v, height >> 1,

            width, height,
            libyuv::kRotate90
    );

    libyuv::I420ToABGR(
            (uint8_t *) temp_y, height,
            (uint8_t *) temp_u, height >> 1,
            (uint8_t *) temp_v, height >> 1,

            (uint8_t *) argb, height * 4,
            height, width
    );


    env->ReleaseByteArrayElements(argb_, argb, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_yuv_YuvUtils_yuvI420ToABGRWithScale(JNIEnv *env, jclass type, jbyteArray argb_,
                                                     jobject y_buffer, jint y_rowStride,
                                                     jobject u_buffer, jint u_rowStride,
                                                     jobject v_buffer, jint v_rowStride,
                                                     jint width, jint height,
                                                     jint scale) {
    jbyte *argb = env->GetByteArrayElements(argb_, NULL);

    uint8_t *srcYPtr = reinterpret_cast<uint8_t *>(env->GetDirectBufferAddress(y_buffer));
    uint8_t *srcUPtr = reinterpret_cast<uint8_t *>(env->GetDirectBufferAddress(u_buffer));
    uint8_t *srcVPtr = reinterpret_cast<uint8_t *>(env->GetDirectBufferAddress(v_buffer));

    int scaleW = width / scale;
    int scaleH = height / scale;
    int scaleSize = scaleW * scaleH;
    jbyte *temp_y_scale = new jbyte[scaleSize * 3 / 2];
    jbyte *temp_u_scale = temp_y_scale + scaleSize;
    jbyte *temp_v_scale = temp_y_scale + scaleSize + scaleSize / 4;

    libyuv::I420Scale(
            srcYPtr, y_rowStride,
            srcUPtr, u_rowStride,
            srcVPtr, v_rowStride,
            width, height,
            (uint8_t *) temp_y_scale, scaleW,
            (uint8_t *) temp_u_scale, scaleW >> 1,
            (uint8_t *) temp_v_scale, scaleW >> 1,
            scaleW, scaleH,
            libyuv::kFilterNone
    );

    width = scaleW;
    height = scaleH;
    jbyte *temp_y = new jbyte[width * height * 3 / 2];
    jbyte *temp_u = temp_y + width * height;
    jbyte *temp_v = temp_y + width * height + width * height / 4;

    libyuv::I420Rotate(
            (uint8_t *) temp_y_scale, scaleW,
            (uint8_t *) temp_u_scale, scaleW >> 1,
            (uint8_t *) temp_v_scale, scaleW >> 1,
//
            (uint8_t *) temp_y, height,
            (uint8_t *) temp_u, height >> 1,
            (uint8_t *) temp_v, height >> 1,

            width, height,
            libyuv::kRotate90
    );

    libyuv::I420ToABGR(
            (uint8_t *) temp_y, height,
            (uint8_t *) temp_u, height >> 1,
            (uint8_t *) temp_v, height >> 1,

            (uint8_t *) argb, height * 4,
            height, width
    );



    env->ReleaseByteArrayElements(argb_, argb, 0);
}