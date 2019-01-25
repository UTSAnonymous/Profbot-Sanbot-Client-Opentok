package com.utsanonymous.profbotsanbotopentok;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.OpentokError;
import com.opentok.android.Publisher;
import com.opentok.android.PublisherKit;
import com.opentok.android.Session;
import com.opentok.android.Stream;
import com.opentok.android.Subscriber;
import com.opentok.android.SubscriberKit;
import com.opentok.android.VideoUtils;
import com.pubnub.api.PNConfiguration;
import com.pubnub.api.PubNub;
import com.pubnub.api.callbacks.SubscribeCallback;
import com.pubnub.api.enums.PNStatusCategory;
import com.pubnub.api.models.consumer.PNStatus;
import com.pubnub.api.models.consumer.pubsub.PNMessageResult;
import com.pubnub.api.models.consumer.pubsub.PNPresenceEventResult;
import com.sanbot.opensdk.base.TopBaseActivity;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.function.beans.wheelmotion.NoAngleWheelMotion;
import com.sanbot.opensdk.function.unit.HardWareManager;
import com.sanbot.opensdk.function.unit.WheelMotionManager;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import static com.utsanonymous.profbotsanbotopentok.R.id.camera_surface_view;
import static com.utsanonymous.profbotsanbotopentok.R.id.publisher_container;
import static com.utsanonymous.profbotsanbotopentok.R.id.subscriber_container;
import static com.utsanonymous.profbotsanbotopentok.R.layout.activity_call;


public class CallActivity extends TopBaseActivity
        implements
        EasyPermissions.PermissionCallbacks,
        Session.SessionListener,
        PublisherKit.PublisherListener,
        SubscriberKit.SubscriberListener,
        CameraDialog.CameraDialogParent {

    private static final String LOG_TAG = "CallActivity";
    private static final int RC_VIDEO_APP_PERM = 124;

    //Opentok API
    private Session mSession;
    private PublisherKit mPublisher;
    private Subscriber mSubscriber;

    private FrameLayout mPublisherViewContainer;
    private FrameLayout mSubscriberViewContainer;

    //for SanbotOpenSDK
    private WheelMotionManager wheelMotionManager;
    private HardWareManager hardWareManager;

    //for pubnub
    private PubNub mPubNub;
    private String robotname;
    private String roomId;

    //======External Camera Support=======//
    //UVCCamera API
    private com.utsanonymous.profbotandroidopentok.CustomWebcamCapturer mCapturer;
    private USBMonitor mUSBMonitor;
    private UVCCamera mUVCCamera;
    private SurfaceView mUVCCameraView;
    // for open&start / stop&close camera preview
    private Surface mPreviewSurface;
    private boolean isActive, isPreview;

    //For thread pool
    private static final int CORE_POOL_SIZE = 1;		// initial/minimum threads
    private static final int MAX_POOL_SIZE = 4;			// maximum threads
    private static final int KEEP_ALIVE_TIME = 10;		// time periods while keep the idle thread
    protected static final ThreadPoolExecutor EXECUTER
            = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME,
            TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    private final Object mSync = new Object();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        register(CallActivity.class);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        super.onCreate(savedInstanceState);
        setContentView(activity_call);

        //Initialize view
        mPublisherViewContainer = (FrameLayout)findViewById(publisher_container);
        mSubscriberViewContainer = (FrameLayout)findViewById(subscriber_container);
        mUVCCameraView = (SurfaceView) findViewById(camera_surface_view);

        // Initialize SanbotOpenSDK classes
        wheelMotionManager = (WheelMotionManager) getUnitManager(FuncConstant.WHEELMOTION_MANAGER);
        hardWareManager = (HardWareManager)getUnitManager(FuncConstant.HARDWARE_MANAGER);

        //Initialize view for external camera output(not shown on screen)
        SurfaceHolder mUVCCameraViewHolder = mUVCCameraView.getHolder();
        mUVCCameraViewHolder.setFormat(PixelFormat.TRANSPARENT);
        mUVCCameraViewHolder.addCallback(mSurfaceViewCallback);

        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);

        // Get Intent parameters.
        final Intent intent = getIntent();
        String room = intent.getStringExtra(com.utsanonymous.profbotrobotclient.util.Constants.ROBOT_ROOM);
        this.robotname = intent.getStringExtra(com.utsanonymous.profbotrobotclient.util.Constants.ROBOT_NAME_KEY);

        String chn = this.robotname + room;
        this.roomId = chn.replaceAll("[\\s\\.]","");
        Log.d(LOG_TAG, "Room ID: " + roomId);

        if (roomId == null || roomId.length() == 0) {
            logAndToast(getString(R.string.missing_url));
            Log.e(LOG_TAG, "Incorrect room ID in intent!");
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        pubnubInit(roomId);

        // turn off blackline filter(Sanbot SDK)
        hardWareManager.switchBlackLineFilter(true);

        requestPermission();
    }

    /**
     * Activity methods
     */
    //=========================================================================================//
    //=========================================================================================//

    @Override
    public void onResume() {
        super.onResume();
        mUSBMonitor.register();
        mSession.onResume();
        mPubNub.reconnect();
    }
    @Override
    protected void onPause() {
        super.onPause();
        mUSBMonitor.unregister();
        mSession.onPause();
        mPubNub.disconnect();
    }

    /**
     * EasyPermission listener methods and functions
     */
    //=========================================================================================//
    //=========================================================================================//

    @AfterPermissionGranted(RC_VIDEO_APP_PERM)
    public void requestPermission(){

        String[] perms = { Manifest.permission.INTERNET, Manifest.permission.CAMERA};
        if(EasyPermissions.hasPermissions(this,perms)){
            initializeSession();
            initializePublisher();
        }
        else{
            EasyPermissions.requestPermissions(this,getString(R.string.rationale_video_app), RC_VIDEO_APP_PERM, perms);
        }
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> perms) {
    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> perms) {
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    /**
     * Session listener methods and functions
     */
    //=========================================================================================//
    //=========================================================================================//

    public void initializeSession(){
        mSession = new Session.Builder(this, com.utsanonymous.profbotandroidopentok.OpenTokConfig.API_KEY, com.utsanonymous.profbotandroidopentok.OpenTokConfig.SESSION_ID).build();
        mSession.setSessionListener(this);
        mSession.connect(com.utsanonymous.profbotandroidopentok.OpenTokConfig.TOKEN);
    }

    public void initializePublisher(){
        mPublisher = new Publisher.Builder(this).build();
        mPublisher.setCapturer(new com.utsanonymous.profbotandroidopentok.CustomWebcamCapturer(this));
        mPublisher.setPublisherListener(this);
        mPublisher.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FIT);
        mPublisherViewContainer.addView(mPublisher.getView());
    }

    @Override
    public void onConnected(Session session) {
        logAndToast("Session connected :" + session.getSessionId());


        if (mUVCCamera == null) {
            // XXX calling CameraDialog.showDialog is necessary at only first time(only when app has no permission).
            CameraDialog.showDialog(this);
        } else {
            synchronized (mSync) {
                mUVCCamera.destroy();
                mUVCCamera = null;
                isActive = isPreview = false;
            }
        }

        if (mPublisher != null) {
            mSession.publish(mPublisher);
        }
    }

    @Override
    public void onDisconnected(Session session) {
        logAndToast("Session disconnected");

        synchronized (mSync) {
            if (mUVCCamera != null) {
                mUVCCamera.destroy();
                mUVCCamera = null;
            }
            isActive = isPreview = false;
        }
        if (mUSBMonitor != null) {
            mUSBMonitor.destroy();
            mUSBMonitor = null;
        }
        mUVCCameraView = null;
    }

    @Override
    public void onStreamReceived(Session session, Stream stream) {

        if(mSubscriber == null){
            mSubscriber = new Subscriber.Builder(this, stream).build();
            mSubscriber.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FIT);
            mSubscriber.setPreferredFrameRate((float) 20);
            mSubscriber.setPreferredResolution(new VideoUtils.Size(680,480));
            mSession.subscribe(mSubscriber);
            mSubscriberViewContainer.addView(mSubscriber.getView());
        }
    }

    @Override
    public void onStreamDropped(Session session, Stream stream) {
        log("Stream Dropped");

        if (mSubscriber != null) {
            mSubscriber = null;
            mSubscriberViewContainer.removeAllViews();
        }

    }

    @Override
    public void onError(Session session, OpentokError opentokError) {
        logAndToast("Opentok Session connection error : " + opentokError.getMessage());
    }

    /**
     * Publisher listener methods
     */
    //=========================================================================================//
    //=========================================================================================//

    @Override
    public void onStreamCreated(PublisherKit publisherKit, Stream stream) {
        log("onStreamConnected :" + publisherKit.getSession());

    }

    @Override
    public void onStreamDestroyed(PublisherKit publisherKit, Stream stream) {
        log("onStreamDestroyed :" + publisherKit.getSession());
    }

    @Override
    public void onError(PublisherKit publisherKit, OpentokError opentokError) {
        logAndToast("Publishing error :" + opentokError.getMessage());
    }

    /**
     * Subscriber listener methods
     */
    //=========================================================================================//
    //=========================================================================================//

    @Override
    public void onConnected(SubscriberKit subscriberKit) {

    }

    @Override
    public void onDisconnected(SubscriberKit subscriberKit) {

    }

    @Override
    public void onError(SubscriberKit subscriberKit, OpentokError opentokError) {

    }

    /**
     * Util
     */
    //=========================================================================================//
    //=========================================================================================//

    public void log(String msg){
        Log.i(LOG_TAG,msg);
    }

    public void logAndToast(final String msg){
        Log.i(LOG_TAG,msg);
        final String data = msg;
        runOnUiThread(new Runnable(){
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), data, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * LibUSB camera methods and functions
     */
    //=========================================================================================//
    //=========================================================================================//

    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            Toast.makeText(getApplicationContext(), "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            synchronized (mSync) {
                if (mUVCCamera != null)
                    mUVCCamera.destroy();
                isActive = isPreview = false;
            }
            EXECUTER.execute(new Runnable() {
                @Override
                public void run() {
                    synchronized (mSync) {
                        mUVCCamera = new UVCCamera();
                        mUVCCamera.open(ctrlBlock);

                        try {
                            mUVCCamera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG);
                        } catch (final IllegalArgumentException e) {
                            try {
                                // fallback to YUV mode
                                mUVCCamera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.DEFAULT_PREVIEW_MODE);
                            } catch (final IllegalArgumentException e1) {
                                mUVCCamera.destroy();
                                mUVCCamera = null;
                            }
                        }
                        if ((mUVCCamera != null) && (mPreviewSurface != null)) {
                            isActive = true;
                            mUVCCamera.setPreviewDisplay(mPreviewSurface);
                            mUVCCamera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_YUV420SP);
                            mUVCCamera.startPreview();
                            isPreview = true;
                        }
                    }
                }
            });
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
            // XXX you should check whether the comming device equal to camera device that currently using

            synchronized (mSync) {
                if (mUVCCamera != null) {
                    mUVCCamera.close();
                    if (mPreviewSurface != null) {
                        mPreviewSurface.release();
                        mPreviewSurface = null;
                    }
                    isActive = isPreview = false;
                }
            }
        }

        @Override
        public void onCancel(UsbDevice usbDevice) {

        }

        @Override
        public void onDettach(final UsbDevice device) {

            Toast.makeText(getApplicationContext(), "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
        }

    };

    public USBMonitor getUSBMonitor() {

        log("getUSBMonitor");
        mCapturer = (com.utsanonymous.profbotandroidopentok.CustomWebcamCapturer) mPublisher.getCapturer();
        mCapturer.addFrame(null);

        new Thread(new MyThread(mCapturer)).start();

        return mUSBMonitor;
    }

    @Override
    public void onDialogResult(boolean b) {

    }

    class MyThread implements Runnable {
        private com.utsanonymous.profbotandroidopentok.CustomWebcamCapturer mCapturer;
        private Long lastCameraTime = 0L;
        public MyThread(com.utsanonymous.profbotandroidopentok.CustomWebcamCapturer cap) {
            mCapturer = cap;
        }

        @Override
        public void run() {
            while(true){

                byte[] capArray = null;
                imageArrayLock.lock();

                if(lastCameraTime != imageTime){
                    lastCameraTime = System.currentTimeMillis();
                    capArray = imageArray;
                }
                imageArrayLock.unlock();
                mCapturer.addFrame(capArray);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private final SurfaceHolder.Callback mSurfaceViewCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(final SurfaceHolder holder) {
        }

        @Override
        public void surfaceChanged(final SurfaceHolder holder, final int format, final int width, final int height) {
            if ((width == 0) || (height == 0)) return;

            mPreviewSurface = holder.getSurface();
            synchronized (mSync) {
                if (isActive && !isPreview) {
                    mUVCCamera.setPreviewDisplay(mPreviewSurface);
                    mUVCCamera.startPreview();
                    isPreview = true;
                }
            }
        }

        @Override
        public void surfaceDestroyed(final SurfaceHolder holder) {

            synchronized (mSync) {
                if (mUVCCamera != null) {
                    mUVCCamera.stopPreview();
                }
                isPreview = false;
            }
            mPreviewSurface = null;
        }
    };

    private byte[] imageArray = null;
    private Long imageTime = 0L;
    private ReentrantLock imageArrayLock = new ReentrantLock();
    private final IFrameCallback mIFrameCallback = new IFrameCallback() {
        @Override
        public void onFrame(final ByteBuffer frame) {
            imageArrayLock.lock();

            imageArray = new byte[frame.remaining()];
            frame.get(imageArray);

            if (imageArray == null) {
                log("onFrame Lock NULL");
            } else {
//                Log.d(TAG, "onFrame Lock ");
            }

            imageTime = System.currentTimeMillis();
            imageArrayLock.unlock();
        }
    };

    /**
     * Pubnub Callbacks and functions
     */
    //=========================================================================================//
    //=========================================================================================//

    public void pubnubInit (String room){

        PNConfiguration pnConfiguration = new PNConfiguration();
        pnConfiguration.setSubscribeKey("sub-c-ba5a852a-f6b9-11e8-adf7-5a5b31762c0f");
        pnConfiguration.setPublishKey("pub-c-2ce3b50e-b61f-481e-88c4-d48e795b9336");
        pnConfiguration.setSecure(false);
        pnConfiguration.setUuid(this.robotname);

        this.mPubNub = new PubNub(pnConfiguration);
        subscribeWithPresence(room);
    }

    public void subscribeWithPresence(String chn){
        this.mPubNub.addListener(new SubscribeCallback() {
            @SuppressLint("LongLogTag")
            @Override
            public void status(PubNub pubnub, PNStatus status) {
                if (status.getCategory() == PNStatusCategory.PNConnectedCategory){
                    Toast.makeText(getApplicationContext(),"yay",Toast.LENGTH_SHORT).show();
                }

                Log.i("subscribing callbacks",status.getOperation().toString());
                Log.i("subscribing callbacks category",status.getCategory().toString());
            }

            @Override
            public void message(PubNub pubnub, PNMessageResult message) {

                JsonElement jsonTree = message.getMessage();
                JsonElement msg = null;

                if(jsonTree.isJsonObject()){
                    JsonObject jsonObject = jsonTree.getAsJsonObject();

                    JsonElement data = jsonObject.get("nameValuePairs");

                    if(data.isJsonObject()){
                        JsonObject jsonObject1 = data.getAsJsonObject();

                        msg = jsonObject1.get(com.utsanonymous.profbotrobotclient.util.Constants.JSON_MESSAGE);
                    }
                }

                String cmd = msg.getAsString();

                Log.i("received message", cmd);
                wheelCommands(cmd);
            }

            @Override
            public void presence(PubNub pubnub, PNPresenceEventResult presence) {
                if(presence.getEvent().equals("join")) {
                    //activity.runOnUiThread(() -> Toast.makeText(context, "someone joined the channel", Toast.LENGTH_SHORT).show());
                }
            }
        });

        this.mPubNub.subscribe()
                .channels(Arrays.asList(chn))
                .withPresence()
                .execute();
    }

    /**
     * Sanbot SDK methods and functions
     */
    //=========================================================================================//
    //=========================================================================================//

    @Override
    protected void onMainServiceConnected() {

    }

    private void wheelCommands(String movement){

        switch(movement) {
            case "forward":
                NoAngleWheelMotion noAngleWheelMotion1 = new NoAngleWheelMotion(
                        NoAngleWheelMotion.ACTION_FORWARD_RUN, 8,40
                );
                wheelMotionManager.doNoAngleMotion(noAngleWheelMotion1);
                break;
            case "right":
                NoAngleWheelMotion noAngleWheelMotion2 = new NoAngleWheelMotion(
                        NoAngleWheelMotion.ACTION_RIGHT_CIRCLE, 8,40
                );
                wheelMotionManager.doNoAngleMotion(noAngleWheelMotion2);
                break;
            case "left":
                NoAngleWheelMotion noAngleWheelMotion3 = new NoAngleWheelMotion(
                        NoAngleWheelMotion.ACTION_LEFT_CIRCLE, 8,20
                );
                wheelMotionManager.doNoAngleMotion(noAngleWheelMotion3);
                break;
            case "backwards":
                NoAngleWheelMotion noAngleWheelMotion4 = new NoAngleWheelMotion(
                        NoAngleWheelMotion.ACTION_BACK_RUN, 8,40
                );
                wheelMotionManager.doNoAngleMotion(noAngleWheelMotion4);
                break;
            case "stop":
                NoAngleWheelMotion noAngleWheelMotion5 = new NoAngleWheelMotion(
                        NoAngleWheelMotion.ACTION_STOP_RUN, 8,20
                );
                wheelMotionManager.doNoAngleMotion(noAngleWheelMotion5);
                break;
        }

    }

}