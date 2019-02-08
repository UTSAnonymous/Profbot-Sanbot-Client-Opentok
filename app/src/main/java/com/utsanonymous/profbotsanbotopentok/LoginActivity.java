package com.utsanonymous.profbotsanbotopentok;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import com.jiangdg.usbcamera.UVCCameraHelper;
import com.sanbot.opensdk.base.TopBaseActivity;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.function.unit.SystemManager;
import com.utsanonymous.profbotsanbotopentok.util.Constants;

import pub.devrel.easypermissions.EasyPermissions;

public class LoginActivity extends TopBaseActivity {

    String LOG_TAG = "LoginActivity";
    public EditText mUsername;
    public EditText mRoom;
    private static final int RC_CALL = 111;

    SystemManager systemManager;
    private UVCCameraHelper mCameraHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        register(LoginActivity.class);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mUsername = (EditText) findViewById(R.id.login_username);
        mRoom = (EditText) findViewById(R.id.room);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String lastUsername = extras.getString("oldUsername", "");
            mUsername.setText(lastUsername);
        }

        systemManager = (SystemManager) getUnitManager(FuncConstant.SYSTEM_MANAGER);

        mCameraHelper = UVCCameraHelper.getInstance();
        mCameraHelper.closeCamera();
        mCameraHelper.unregisterUSB();
    }

        public void joinChannel (View view){
            String[] perms = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};

            if (EasyPermissions.hasPermissions(this, perms)) {
                String username = mUsername.getText().toString();
                String room = mRoom.getText().toString();
                if (!validUsername(username)) return;

                Intent intent = new Intent(this, CallActivity.class);
                intent.putExtra(Constants.ROBOT_NAME_KEY, username);
                intent.putExtra(Constants.ROBOT_ROOM, room);
                startActivity(intent);
            } else {
                EasyPermissions.requestPermissions(this, "Need some permissions", RC_CALL, perms);
            }
        }

        @Override
        public void onRequestPermissionsResult ( int requestCode, @NonNull String[] permissions,
        @NonNull int[] grantResults){
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
        }

        private boolean validUsername (String username){
            if (username.length() == 0) {
                mUsername.setError("Username cannot be empty.");
                return false;
            }
            if (username.length() > 16) {
                mUsername.setError("Username too long.");
                return false;
            }
            if (username.equals("Name")) {
                mUsername.setError("Please enter your name");
                return false;
            }
            return true;
        }


        @Override
        protected void onMainServiceConnected () {
            //systemManager.switchFloatBar(false, LoginActivity.class.getName());
        }

    }
