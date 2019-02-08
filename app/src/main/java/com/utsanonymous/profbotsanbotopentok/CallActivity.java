package com.utsanonymous.profbotsanbotopentok;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jiangdg.usbcamera.UVCCameraHelper;
import com.jiangdg.usbcamera.utils.FileUtils;
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
import com.pubnub.api.callbacks.PNCallback;
import com.pubnub.api.callbacks.SubscribeCallback;
import com.pubnub.api.enums.PNStatusCategory;
import com.pubnub.api.models.consumer.PNPublishResult;
import com.pubnub.api.models.consumer.PNStatus;
import com.pubnub.api.models.consumer.pubsub.PNMessageResult;
import com.pubnub.api.models.consumer.pubsub.PNPresenceEventResult;
import com.sanbot.opensdk.base.TopBaseActivity;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.beans.OperationResult;
import com.sanbot.opensdk.function.beans.FaceRecognizeBean;
import com.sanbot.opensdk.function.beans.LED;
import com.sanbot.opensdk.function.beans.StreamOption;
import com.sanbot.opensdk.function.beans.wheelmotion.NoAngleWheelMotion;
import com.sanbot.opensdk.function.unit.HDCameraManager;
import com.sanbot.opensdk.function.unit.HardWareManager;
import com.sanbot.opensdk.function.unit.SystemManager;
import com.sanbot.opensdk.function.unit.WheelMotionManager;
import com.sanbot.opensdk.function.unit.interfaces.hardware.ObstacleListener;
import com.sanbot.opensdk.function.unit.interfaces.media.FaceRecognizeListener;
import com.sanbot.opensdk.function.unit.interfaces.media.MediaStreamListener;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;
import com.serenegiant.usb.widget.CameraViewInterface;
import com.utsanonymous.profbotsanbotopentok.opentok.CustomWebcamCapturer;
import com.utsanonymous.profbotsanbotopentok.opentok.OpenTokConfig;
import com.utsanonymous.profbotsanbotopentok.util.Constants;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import static com.utsanonymous.profbotsanbotopentok.R.id.subscriber_container;
import static com.utsanonymous.profbotsanbotopentok.R.layout.activity_call;


public class CallActivity extends TopBaseActivity
        implements
        EasyPermissions.PermissionCallbacks,
        Session.SessionListener,
        PublisherKit.PublisherListener,
        SubscriberKit.SubscriberListener,
        CameraDialog.CameraDialogParent,
        CameraViewInterface.Callback{

    private static final String LOG_TAG = "CallActivity";
    private static final int RC_VIDEO_APP_PERM = 124;

    //Opentok API
    private Session mSession;
    private PublisherKit mPublisher;
    private Subscriber mSubscriber;

    private FrameLayout mSubscriberViewContainer;

    //for SanbotOpenSDK
    private WheelMotionManager wheelMotionManager;
    private HardWareManager hardWareManager;
    private SystemManager systemManager;

    //for pubnub
    private PubNub mPubNub;
    private String robotname;
    private String roomId;

    //FOR Jiang test
    private UVCCameraHelper mCameraHelper;
    private CameraViewInterface mUVCCameraView;
    private TextureView mTextureView;

    private boolean isRequest;
    private boolean isPreview;
    private boolean isSubscribing;

    //Custom webcam capturer
    private CustomWebcamCapturer mCapturer;

    //Listener for Jiang Camera Helper class
    // This listener handles all of the USB connection for external Webcam
    private UVCCameraHelper.OnMyDevConnectListener listener = new UVCCameraHelper.OnMyDevConnectListener() {

        @Override
        public void onAttachDev(UsbDevice device) {
            if (mCameraHelper == null || mCameraHelper.getUsbDeviceCount() == 0) {
                return;
            }
            // request open permission
            if (!isRequest) {
                isRequest = true;
                if (mCameraHelper != null) {
                    mCameraHelper.requestPermission(0);
                }
            }
        }

        @Override
        public void onDettachDev(UsbDevice device) {
            // close camera
            if (isRequest) {
                isRequest = false;
                mCameraHelper.closeCamera();
            }
        }

        @Override
        public void onConnectDev(UsbDevice device, boolean isConnected) {
            if (!isConnected) {
                isPreview = false;
            } else {
                isPreview = true;
                logAndToast("Connected to :" + device.getProductName());
            }
        }

        @Override
        public void onDisConnectDev(UsbDevice device){
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        register(CallActivity.class);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        super.onCreate(savedInstanceState);
        setContentView(activity_call);

        //Initialize view
        mSubscriberViewContainer = (FrameLayout)findViewById(subscriber_container);
        mTextureView = (TextureView)findViewById(R.id.camera_view);

        // Initialize SanbotOpenSDK classes
        wheelMotionManager = (WheelMotionManager) getUnitManager(FuncConstant.WHEELMOTION_MANAGER);
        hardWareManager = (HardWareManager)getUnitManager(FuncConstant.HARDWARE_MANAGER);
        systemManager = (SystemManager) getUnitManager(FuncConstant.SYSTEM_MANAGER);

        // step.1 initialize UVCCameraHelper
        mUVCCameraView = (CameraViewInterface) mTextureView;
        mUVCCameraView.setCallback(this);
        mCameraHelper = UVCCameraHelper.getInstance();
        mCameraHelper.setDefaultPreviewSize(320,240); //424,240
        mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_YUYV);
        mCameraHelper.initUSBMonitor(this, mUVCCameraView, listener);
        isSubscribing = false;

        mCameraHelper.setOnPreviewFrameListener(new AbstractUVCCameraHandler.OnPreViewResultListener() {
            @Override
            public void onPreviewResult(byte[] nv21Yuv) {
                if(mPublisher != null){
                    if(isSubscribing){
                        mCapturer.addFrame(nv21Yuv);
                    }
                }
            }
        });

        // Get Intent parameters.
        final Intent intent = getIntent();
        String room = intent.getStringExtra(Constants.ROBOT_ROOM);
        this.robotname = intent.getStringExtra(Constants.ROBOT_NAME_KEY);

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
    protected void onDestroy() {
        super.onDestroy();
        // step.4 release uvc camera resources
        if (mCameraHelper != null) {
            mCameraHelper.release();
        }
    }    @Override
    protected void onStart() {
        super.onStart();
        // step.2 register USB event broadcast
        if (mCameraHelper != null) {
            mCameraHelper.registerUSB();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // step.3 unregister USB event broadcast
        if (mCameraHelper != null) {
            mCameraHelper.unregisterUSB();
        }
    }

    public void disconnectButton(View view){
        if(mPubNub != null){
            mPubNub.disconnect();
        }
        if (mCameraHelper != null) {
            mCameraHelper.release();
            mCameraHelper.closeCamera();
            mCameraHelper.unregisterUSB();
        }
        if(mSession != null){
            mSession.disconnect();
            finish();
        }
    }

    /**
     * EasyPermission listener methods and functions
     */
    //=========================================================================================//
    //=========================================================================================//

    @AfterPermissionGranted(RC_VIDEO_APP_PERM)
    public void requestPermission(){

        String[] perms = { Manifest.permission.INTERNET, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
        if(EasyPermissions.hasPermissions(this,perms)){
            initializeSession();
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
        mSession = new Session.Builder(this, OpenTokConfig.API_KEY, OpenTokConfig.SESSION_ID).build();
        mSession.setSessionListener(this);
        mSession.connect(OpenTokConfig.TOKEN);
    }

    public void initializePublisher(){
        mPublisher = new Publisher.Builder(this)
                .audioTrack(true)
                .capturer(new CustomWebcamCapturer(this))
                .build();
        mPublisher.setPublishAudio(true);
        mPublisher.setPublisherListener(this);
    }

    @Override
    public void onConnected(Session session) {
        initializePublisher();
        if (mPublisher != null) {
            mSession.publish(mPublisher);
        }
        mCapturer = (CustomWebcamCapturer) mPublisher.getCapturer();
        mCapturer.addFrame(null);
    }

    @Override
    public void onDisconnected(Session session) {

    }

    @Override
    public void onStreamReceived(Session session, Stream stream) {
        isSubscribing = true;
        if(mSubscriber == null){
            mSubscriber = new Subscriber.Builder(this, stream).build();
            mSubscriber.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
            mSubscriber.setPreferredResolution(new VideoUtils.Size(680,480));
            mSession.subscribe(mSubscriber);
            mSubscriberViewContainer.addView(mSubscriber.getView());
        }
    }

    @Override
    public void onStreamDropped(Session session, Stream stream) {
        log("Stream Dropped");
        isSubscribing = false;
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
        log("Publishing error :" + opentokError.getMessage());
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

    @Override
    public USBMonitor getUSBMonitor() {
        return mCameraHelper.getUSBMonitor();
    }

    @Override
    public void onDialogResult(boolean canceled) {
        if (canceled) {
        }
    }

    public boolean isCameraOpened() {
        return mCameraHelper.isCameraOpened();
    }

    @Override
    public void onSurfaceCreated(CameraViewInterface view, Surface surface) {
        if (!isPreview && mCameraHelper.isCameraOpened()) {
            mCameraHelper.startPreview(mUVCCameraView);
            isPreview = true;
        }
    }

    @Override
    public void onSurfaceChanged(CameraViewInterface view, Surface surface, int width, int height) {

    }

    @Override
    public void onSurfaceDestroy(CameraViewInterface view, Surface surface) {
        if (isPreview && mCameraHelper.isCameraOpened()) {
            mCameraHelper.stopPreview();
            isPreview = false;
        }
    }

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

                        msg = jsonObject1.get(Constants.JSON_MESSAGE);
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

    public void publishPubNub(JSONObject data){
        mPubNub.publish().channel(this.roomId).message(data).async(new PNCallback<PNPublishResult>() {
            @Override
            public void onResponse(PNPublishResult result, PNStatus status) {
                log("Acknowledge PubNub publish is successful");
            }
        });
    }

    /**
     * Sanbot SDK methods and functions
     */
    //=========================================================================================//
    //=========================================================================================//

    @Override
    protected void onMainServiceConnected() {
        systemManager.switchFloatBar(false, CallActivity.class.getName());
    }

    private void wheelCommands(String movement){

        switch(movement) {
            case "forward":
                NoAngleWheelMotion noAngleWheelMotion1 = new NoAngleWheelMotion(
                        NoAngleWheelMotion.ACTION_FORWARD_RUN, 3,30
                );
                wheelMotionManager.doNoAngleMotion(noAngleWheelMotion1);
                break;
            case "right":
                NoAngleWheelMotion noAngleWheelMotion2 = new NoAngleWheelMotion(
                        NoAngleWheelMotion.ACTION_RIGHT_CIRCLE, 3,30
                );
                wheelMotionManager.doNoAngleMotion(noAngleWheelMotion2);
                break;
            case "left":
                NoAngleWheelMotion noAngleWheelMotion3 = new NoAngleWheelMotion(
                        NoAngleWheelMotion.ACTION_LEFT_CIRCLE, 3,30
                );
                wheelMotionManager.doNoAngleMotion(noAngleWheelMotion3);
                break;
            case "backwards":
                NoAngleWheelMotion noAngleWheelMotion4 = new NoAngleWheelMotion(
                        NoAngleWheelMotion.ACTION_BACK_RUN, 3,30
                );
                wheelMotionManager.doNoAngleMotion(noAngleWheelMotion4);
                break;
            case "stop":
                NoAngleWheelMotion noAngleWheelMotion5 = new NoAngleWheelMotion(
                        NoAngleWheelMotion.ACTION_STOP_RUN, 8,30
                );
                wheelMotionManager.doNoAngleMotion(noAngleWheelMotion5);
                break;
        }

    }

}