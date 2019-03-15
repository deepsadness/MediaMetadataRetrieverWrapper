package com.example.cry.mediacodecimagereaderchain;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.cry.mediametaretriverwrapper.MediaMetadataRetrieverWrapper;
import com.cry.mediametaretriverwrapper.RetrieverProcessThread;
import com.example.cry.mediacodecimagereaderchain.codec.VideoProcessThread;

import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private VideoProcessThread mVideoProcessThread;
    private ProgressDialog mProgressDialog;
    private ListView mListView;
    private ListView mListView2;
    private ListView mListView3;
    //    private ImageView mIvFrame;
    private ArrayList<String> mVideoPaths = new ArrayList<>();
    private VideoPathAdapter mPathAdapter;
    private BitmapAdapter mBitmapAdapter;
    private BitmapAdapter mBitmapAdapter2;
    private BitmapShow bitmapShow = new BitmapShow();
    private BitmapShow2 bitmapShow2 = new BitmapShow2();
    ArrayList<Bitmap> bitmapArrayList = new ArrayList<>();
    ArrayList<Bitmap> bitmapArrayList2 = new ArrayList<>();


    private String targetPath;
    MediaMetadataRetrieverWrapper metadataRetriever;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mListView = (ListView) findViewById(R.id.list_view);
//        mIvFrame = (ImageView) findViewById(R.id.iv_frame);
        mPathAdapter = new VideoPathAdapter(mVideoPaths);
        mListView.setAdapter(mPathAdapter);


        mListView2 = (ListView) findViewById(R.id.list_view2);
//        mIvFrame = (ImageView) findViewById(R.id.iv_frame);
        mBitmapAdapter = new BitmapAdapter(bitmapArrayList);
        mListView2.setAdapter(mBitmapAdapter);

        mListView3 = (ListView) findViewById(R.id.list_view3);
//        mIvFrame = (ImageView) findViewById(R.id.iv_frame);
        mBitmapAdapter2 = new BitmapAdapter(bitmapArrayList2);
        mListView3.setAdapter(mBitmapAdapter2);


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1000);
        } else {
            getVideoFiles();
        }

        Display defaultDisplay = getWindowManager().getDefaultDisplay();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        defaultDisplay.getMetrics(displayMetrics);
        int widthPixels = displayMetrics.widthPixels;
        int heightPixels = displayMetrics.heightPixels;
        float scale = 1080 * 1f / widthPixels;
    }

    @SuppressLint("NewApi")
    private void getVideoFiles() {
        mVideoProcessThread = new VideoProcessThread(this);
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setMessage("获取视频列表");
        mProgressDialog.show();
        mVideoProcessThread.getFileList(new VideoProcessThread.VideoListCallBack() {
            @Override
            public void onComplete(final ArrayList<String> videos) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mProgressDialog.dismiss();
                        //获取
                        mVideoPaths.addAll(videos);
                        mPathAdapter.notifyDataSetChanged();
                    }
                });
            }
        });

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String path = mVideoPaths.get(position);
                targetPath = path;
//                mVideoProcessThread.process(path, bitmapShow);
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//                    mVideoProcessThread.process2(path, bitmapShow2);
//                }
//                final long start = System.currentTimeMillis();
//                if (metadataRetriever == null) {
                MediaMetadataRetrieverWrapper metadataRetriever = new MediaMetadataRetrieverWrapper();
                metadataRetriever.forceFallBack(true);
                metadataRetriever.setDataSource(path);
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

//                }
                //2S
//                metadataRetriever.getFrameAtTime(2 * 1000 * 1000, 2, new RetrieverProcessThread.BitmapCallBack() {
//                    @Override
//                    public void onComplete(final Bitmap frame) {
//                        long end = System.currentTimeMillis();
//                        Log.d("zzx", "cost ms = " + (end - start));
//                        runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                bitmapArrayList.add(frame);
//                                mBitmapAdapter.notifyDataSetChanged();
//                            }
//                        });
//                    }
//                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            getVideoFiles();
        }
    }

    public void tap(View view) {
        //2S
        final long start = System.currentTimeMillis();

//        metadataRetriever.getFrameAtTime(2 * 1000 * 1000, 2, new RetrieverProcessThread.BitmapCallBack() {
//            @Override
//            public void onComplete(final Bitmap frame) {
//                long end = System.currentTimeMillis();
//                Log.d("zzx", "cost ms = " + (end - start));
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        bitmapArrayList.add(frame);
//                        mBitmapAdapter.notifyDataSetChanged();
//                    }
//                });
//            }
//        });

        MediaMetadataRetrieverWrapper metadataRetriever2 = new MediaMetadataRetrieverWrapper();
        metadataRetriever2.setDataSource(targetPath);
        metadataRetriever2.getFramesInterval(1000, 4, new RetrieverProcessThread.BitmapCallBack() {
            @Override
            public void onComplete(final Bitmap frame2) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bitmapArrayList2.add(frame2);
                        mBitmapAdapter2.notifyDataSetChanged();
                    }
                });
            }
        });
    }

    private class BitmapShow implements VideoProcessThread.BitmapCallBack {

        @Override
        public void onComplete(final Bitmap frame) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
//                    mIvFrame.setImageBitmap(frame);
                    bitmapArrayList.add(frame);
                    mBitmapAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    private class BitmapShow2 implements VideoProcessThread.BitmapCallBack {

        @Override
        public void onComplete(final Bitmap frame) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
//                    mIvFrame.setImageBitmap(frame);
                    bitmapArrayList2.add(frame);
                    mBitmapAdapter2.notifyDataSetChanged();
                }
            });
        }
    }

    private class VideoPathAdapter extends BaseAdapter {

        private ArrayList<String> videoPaths;

        public VideoPathAdapter(ArrayList<String> videoPaths) {
            this.videoPaths = videoPaths;
        }

        @Override
        public int getCount() {
            return videoPaths.size();
        }

        @Override
        public String getItem(int position) {
            return videoPaths.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);
            }

            ((TextView) convertView.findViewById(android.R.id.text1))
                    .setText(getItem(position));
            return convertView;
        }
    }


    private class BitmapAdapter extends BaseAdapter {

        private ArrayList<Bitmap> bitmaps;

        public BitmapAdapter(ArrayList<Bitmap> bitmaps) {
            this.bitmaps = bitmaps;
        }

        @Override
        public int getCount() {
            return bitmaps.size();
        }

        @Override
        public Bitmap getItem(int position) {
            return bitmaps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.simple_image, parent, false);
            }
            ((ImageView) convertView.findViewById(R.id.image))
                    .setImageBitmap(getItem(position));
            return convertView;
        }
    }
}
