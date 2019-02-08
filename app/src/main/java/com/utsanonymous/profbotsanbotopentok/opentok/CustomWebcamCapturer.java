package com.utsanonymous.profbotsanbotopentok.opentok;

import android.content.Context;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.opentok.android.BaseVideoCapturer;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by quanhua on 14/10/2015.
 */
public class CustomWebcamCapturer extends BaseVideoCapturer {
    private final static String LOGTAG = "custom-video-capturer";
    private Context context;

    private ReentrantLock mPreviewBufferLock = new ReentrantLock(); // sync
    // start/stop
    // capture
    // and
    // surface
    // changes

    private final static int PIXEL_FORMAT = NV21;

    private Long lastCaptureFrame = 0L;
    private Long currentCaptureFrame = 0L;

    private boolean isCaptureStarted = false;
    private boolean isCaptureRunning = false;

    private int mCaptureWidth = 320;
    private int mCaptureHeight = 240;
    private int mCaptureFPS = 20;

    private Display mCurrentDisplay;

    public CustomWebcamCapturer(Context context) {
        // Initialize front camera by default

        Log.d(LOGTAG, "CustomWebcamCapturer constructor");

        this.context = context;

        // Get current display to query UI orientation
        WindowManager windowManager = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        mCurrentDisplay = windowManager.getDefaultDisplay();
    }


    public void addFrame(final byte[] data){
        Log.d(LOGTAG, "addFrame");
        if(data == null){
//            Log.d(LOGTAG, "hasFrame \ NULL");
            return;
        }
        if(data != null){
            if (isCaptureRunning) {
                provideByteArrayFrame(data, PIXEL_FORMAT, mCaptureWidth,
                        mCaptureHeight, 0 , false);
            }
            currentCaptureFrame = System.currentTimeMillis();
        }
    }


    @Override
    public int startCapture() {
        if (isCaptureStarted) {
            return -1;
        }

        Log.d(LOGTAG, "startCapture");

        mPreviewBufferLock.lock();
        isCaptureRunning = true;
        mPreviewBufferLock.unlock();

        isCaptureStarted = true;

        return 0;
    }

    @Override
    public int stopCapture() {
        isCaptureStarted = false;
        return 0;
    }

    @Override
    public void destroy() {
        stopCapture();
    }

    @Override
    public boolean isCaptureStarted() {
        return isCaptureStarted;
    }

    @Override
    public CaptureSettings getCaptureSettings() {

        // Set the preferred capturing size
        CaptureSettings settings = new CaptureSettings();
        settings.fps = mCaptureFPS;
        settings.width = mCaptureWidth;
        settings.height = mCaptureHeight;
        settings.format = PIXEL_FORMAT;
        settings.expectedDelay = 0;
        return settings;
    }

    @Override
    public void onPause() {


    }

    @Override
    public void onResume() {

    }


    private void configureCaptureSize(int preferredWidth, int preferredHeight) {

        mCaptureFPS = 20;

        mCaptureWidth = preferredWidth;
        mCaptureHeight = preferredHeight;
    }

    @Override
    public void init() {

    }

}