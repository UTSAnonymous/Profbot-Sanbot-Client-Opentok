package com.utsanonymous.profbotsanbotopentok.util;

public class Constants {

    public static final String PUBNUB_PUB_KEY = "pub-c-2ce3b50e-b61f-481e-88c4-d48e795b9336";
    public static final String PUBNUB_SUB_KEY = "sub-c-ba5a852a-f6b9-11e8-adf7-5a5b31762c0f";

    public static final String CHAT_PREFS = "com.utsanonymous.SHARED_PREFS";
    public static final String CHAT_USERNAME = "com.utsanonymous.SHARED_PREFS.USERNAME";

    public static final String LOBBY_CHANNEL = "lobby";
    public static final String ROBOT_AVAILABLE = "Available";
    public static final String ROBOT_NAME_KEY = "Robot Name";
    public static final String ROBOT_LOCATION_KEY = "Robot Location";
    public static final String ROBOT_ROOM = "Robot Room";
    public static final String PRIVATE_CHANNEL_KEY = "Private Channel";

    //for JSONObject data
    public static final String JSON_USER = "user";
    public static final String JSON_MESSAGE = "message";
    public static final String JSON_FORWARD = "forward";
    public static final String JSON_BACKWARD = "backward";
    public static final String JSON_RIGHT = "right";
    public static final String JSON_LEFT = "left";
    public static final String JSON_STOP = "stop";

    public static final String EXTRA_ROOMID = "org.appspot.apprtc.ROOMID";
    public static final int CAPTURE_PERMISSION_REQUEST_CODE = 1;

    // List of mandatory application permissions.
    public static final String[] MANDATORY_PERMISSIONS = {"android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.RECORD_AUDIO", "android.permission.INTERNET"};

    // Peer connection statistics callback period in ms.
    public static final int STAT_CALLBACK_PERIOD = 1000;
    // Local preview screen position before call is connected.
    public static final int LOCAL_X_CONNECTING = 0;
    public static final int LOCAL_Y_CONNECTING = 0;
    public static final int LOCAL_WIDTH_CONNECTING = 0;
    public static final int LOCAL_HEIGHT_CONNECTING = 0;
    // Local preview screen position after call is connected.
    public static final int LOCAL_X_CONNECTED = 0;
    public static final int LOCAL_Y_CONNECTED = 0;
    public static final int LOCAL_WIDTH_CONNECTED = 25;
    public static final int LOCAL_HEIGHT_CONNECTED = 25;
    // Remote video screen position
    public static final int REMOTE_X = 72;
    public static final int REMOTE_Y = 72;
    public static final int REMOTE_WIDTH = 25;
    public static final int REMOTE_HEIGHT = 25;
}
