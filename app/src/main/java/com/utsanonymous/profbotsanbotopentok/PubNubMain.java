package com.utsanonymous.profbotrobotclient;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pubnub.api.PNConfiguration;
import com.pubnub.api.PubNub;
import com.pubnub.api.callbacks.PNCallback;
import com.pubnub.api.callbacks.SubscribeCallback;
import com.pubnub.api.enums.PNStatusCategory;
import com.pubnub.api.models.consumer.PNPublishResult;
import com.pubnub.api.models.consumer.PNStatus;
import com.pubnub.api.models.consumer.pubsub.PNMessageResult;
import com.pubnub.api.models.consumer.pubsub.PNPresenceEventResult;
import com.utsanonymous.profbotrobotclient.util.Constants;

import org.json.JSONObject;

import java.io.Serializable;
import java.util.Arrays;

public class PubNubMain implements Serializable {

    private PubNub mPubNub;
    private String username;
    private Context context;
    private Activity activity;
    private String channel;

    public PubNubMain(Context context , String username, Activity activity, String channel){
        this.username = username;
        this.context = context;
        this.activity = activity;
        this.channel = channel;
    }

    /**
     * initPubNub
     * @note initialise pubnub with all the necessary details
     */
    public void initPubNub(){

        PNConfiguration pnConfiguration = new PNConfiguration();
        pnConfiguration.setSubscribeKey("sub-c-ba5a852a-f6b9-11e8-adf7-5a5b31762c0f");
        pnConfiguration.setPublishKey("pub-c-2ce3b50e-b61f-481e-88c4-d48e795b9336");
        pnConfiguration.setSecure(false);
        pnConfiguration.setUuid(this.username);

        this.mPubNub = new PubNub(pnConfiguration);
        subscribeWithPresence(this.channel);
    }

    public void subscribeWithPresence(String chn){
        this.mPubNub.addListener(new SubscribeCallback() {
            @SuppressLint("LongLogTag")
            @Override
            public void status(PubNub pubnub, PNStatus status) {
                if (status.getCategory() == PNStatusCategory.PNConnectedCategory){
                    //Toast.makeText(context,"yay",Toast.LENGTH_SHORT).show();
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

                Log.i("received message", String.valueOf(msg));
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

    public void unsubscribeAll(){
        mPubNub.unsubscribeAll();
    }

    public void publish(JSONObject data){
        mPubNub.publish().channel(this.channel).message(data).async(new PNCallback<PNPublishResult>() {
            @Override
            public void onResponse(PNPublishResult result, PNStatus status) {
            }
        });
    }

    /*
     public void publish(JSONObject data){
        JSONObject data = new JSONObject() ;
        try {
            data.put("user",username);
            data.put("msg","HELLO!");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mPubNub.publish().channel(Constant.LOBBY_CHANNEL).message(data).async(new PNCallback<PNPublishResult>() {
            @Override
            public void onResponse(PNPublishResult result, PNStatus status) {
            }
        });
    }
     */

}
