# MediaMetadataRetrieverWrapper
MediaMetadataRetriever wrapper。
API Request : >=19 ,Android 4.4

### 速度对比

> 左边的图片是通过方式1(使用MediaMetadataRetriever)
> 右边的图片是通过方式2(使用MediaCodec+ImageReader)

![speed.gif](https://upload-images.jianshu.io/upload_images/1877190-043e610b38a54051.gif?imageMogr2/auto-orient/strip)

####
在缩小2倍的Bitmap输出情况下
- 使用MediaMetadataRetriever
抽帧的速度，稳定在 300ms左右。

- 使用MediaCodec+ImageReader
第一次抽帧。大概是200ms ,后续每帧则是50ms左右。

> 注意：如果不缩小图片的话，建议还是使用MediaMetadataRetriever。
> 使用当前库的话，调用metadataRetriever.forceFallBack(true);

### 添加依赖

- Add it in your root build.gradle at the end of repositories:

```
    allprojects {
    	repositories {
    		...
    		maven { url 'https://jitpack.io' }
    	}
    }
```

- Add the dependency

```
    dependencies {
        implementation 'com.github.deepsadness:MediaMetadataRetrieverWrapper:0.1'
    }

```

### 使用方式

1. 创建
```

 MediaMetadataRetrieverWrapper metadataRetriever = new MediaMetadataRetrieverWrapper();
 //如果想要完全按照原来的metaRetirever的方式，就设置为true。默认为false
 //metadataRetriever.forceFallBack(true);

```

2. 设置DataSource

当前只支持了本地文件。没有重写其他的方法
```
   metadataRetriever.setDataSource(path);

```

3. 对对应的时间抽帧

```
    //2s处。尺寸缩小2倍
    metadataRetriever.getFrameAtTime(2 * 1000 * 1000, 2, new RetrieverProcessThread.BitmapCallBack() {
        @Override
        public void onComplete(final Bitmap frame) {
            long end = System.currentTimeMillis();
            Log.d("zzx", "cost ms = " + (end - start));
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    bitmapArrayList.add(frame);
                    mBitmapAdapter.notifyDataSetChanged();
                }
            });
        }
    });
```

4. 按照间隔

这种方式，可以按照时间间隔，一口气取出所有的帧。
如果没有强制使用MediaMetaRetriever的话(forceBack 为 false，默认情况)，结果会多一帧。因为强制输出最后一帧。
```
      metadataRetriever.getFramesInterval(1000, 4, new RetrieverProcessThread.BitmapCallBack() {
          @Override
          public void onComplete(final Bitmap frame) {
              runOnUiThread(new Runnable() {
                  @Override
                  public void run() {
                      bitmapArrayList.add(frame);
                      mBitmapAdapter.notifyDataSetChanged();
                  }
              });
          }
      });

```

5. 释放
```
    metadataRetriever.release();
```

### 简要说明

将文件通过MediaCodec解码。
输出到ImageReader当中。来获取截图。

使用MediaMetadataRetriever的方式，因为无法配置输出的图片的大小。

但当我们只需要生成小图预览的时候，
如果我们实现做了缩放的处理。就能得到很快的速度。

博文位置:https://www.jianshu.com/p/dfddb85302bd

### 后续
需要对原来MediaMetadataRetriever的原理探究

- 在API28以上，MediaMetadataRetriever提供了[getScaledFrameAtTime](https://developer.android.com/reference/android/media/MediaMetadataRetriever#getScaledFrameAtTime(long,%20int,%20int,%20int))方法
