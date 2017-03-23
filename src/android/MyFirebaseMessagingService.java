package com.gae.scaffolder.plugin;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

import android.os.Bundle;
import android.os.SystemClock;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONObject;

import java.lang.Runnable;
import android.os.Handler;


import android.location.Location;
//import android.location.LocationListener;
import android.location.LocationManager;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;

import android.app.Notification;

import  android.graphics.Color;

import com.gae.scaffolder.plugin.MyFirebaseMessagingService;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Created by Felipe Echanique on 08/06/2016.
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService 
    implements LocationListener, ConnectionCallbacks {

    private static GoogleApiClient mGoogleApiClient = null;
    private static final String TAG = "FCMPlugin";
    private static Integer numNotifications = 0;
    private static Integer numMsgs = 0;
    private static Integer numId = 0;
    private static int mWarningTimeout = (60 * 30);
    private static ArrayList mNotificationsList = new ArrayList<Map>();
    private static HashMap<String, String> mWarningsList = new HashMap<String, String>();
    private static boolean removeWarning = false;
    Handler handler = new Handler();
    private static Context mContext = null;
    
    public void onLocationChanged(Location location) {
    }

    @Override
    public void onConnected(Bundle connectionHint) {
    }

    @Override
    public void onConnectionSuspended(int cause) {
    }

    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    // [START receive_message]
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // https://firebase.google.com/docs/reference/android/com/google/firebase/messaging/RemoteMessage
        // TODO(developer): Handle FCM messages here.
        // If the application is in the foreground handle both data and notification messages here.
        // Also if you intend on generating your own notifications as a result of a received FCM
        // message, here is where that should be initiated. See sendNotification method below.
        mContext = this;
        mGoogleApiClient = FCMPlugin.get_gapi_client();
        if (mGoogleApiClient == null || !mGoogleApiClient.isConnected()){
            FCMPlugin.connect_gservices(mContext);
        }

        Log.d(TAG, "==> MyFirebaseMessagingService onMessageReceived");
	
        boolean isLoggedIn = FCMPlugin.isLoggedIn(this);
		
        if( remoteMessage.getNotification() != null){
            Log.d(TAG, "\tNotification Title: " + remoteMessage.getNotification().getTitle());
            Log.d(TAG, "\tNotification Message: " + remoteMessage.getNotification().getBody());
        }
        else{
			Log.d(TAG, "\tNotification null");
        }
		
        final Map<String, Object> data = new HashMap<String, Object>();
        data.put("wasTapped", false);
        for (String key : remoteMessage.getData().keySet()) {
            Object value = remoteMessage.getData().get(key);
            Log.d(TAG, "\tKey: " + key + " Value: " + value);
            data.put(key, value);
        }

        final Object type = data.get(FCMPlugin.FIELD_TYPE);
        final Object rkey = data.get(FCMPlugin.FIELD_RKEY);
        final Object fuid = data.get(FCMPlugin.FIELD_FUID);
        final Object timestamp = data.get(FCMPlugin.FIELD_TIMESTAMP);

        if (type != null && (type.toString().equals( FCMPlugin.TYPE_WARNING ) ||
                    type.toString().equals( FCMPlugin.TYPE_FINISHED_WARNING ))){
            String rwarns = FCMPlugin.getPreference(this, "receive_warnings");
            if (rwarns != null && rwarns.equals("false")){
                Log.d(TAG, "\tWARN: User doesnt want to receive warnings: " + rwarns);
                return;
            }
            Log.d(TAG, "\tUser wants to receive warnings: " + rwarns);
        }


        if (data.containsKey("silent")){
			Log.d(TAG, "\tSilent notification");
            if (type != null && type.toString().equals( FCMPlugin.TYPE_WARNING )){
                getGpsPosition(this, data);
            }
        }
        else{
            Log.d(TAG, "\tNotification Data: " + data.toString());
            if (rkey != null && mWarningsList.containsKey(rkey.toString())){
                Log.d(TAG, "We got a WARNING in the list: " + rkey.toString());
                removeWarning = true;
            }
            if (type != null && type.toString().equals( FCMPlugin.TYPE_WARNING )){
                if (timestamp == null || fuid == null || rkey == null){
                    Log.d(TAG, "==> malformed Warning <==");
                    return;
                }

                java.util.Date now = new java.util.Date();
                java.util.Date sent_timestamp = new java.util.Date( Long.valueOf(timestamp.toString()) );
                final Long elapsed_time = ((now.getTime() - sent_timestamp.getTime()) / 1000);
                Log.d(TAG, "a WARNING has arrived. sent " + elapsed_time + "s ago");
                // TODO: save warning to sharedprefs

                if (elapsed_time < mWarningTimeout){
                    Log.d(TAG, "WARNING time valid");
                    // add data to list if it's not already added
                    if (!removeWarning){
                        mWarningsList.put(rkey.toString(), fuid.toString());
                    }
                    handler.postDelayed(
                            new Runnable(){
                                @Override
                                public void run() {
                                    mWarningsList.remove(rkey.toString());
                                    if (removeWarning){
                                        removeWarning = false;
                                        Log.d(TAG, "WARNING removed due to another incoming event with the same rkey: " + rkey.toString());
                                        return;
                                    }
                                    // if queue empty, send it
                                    Log.d(TAG, "WARNING posting");
                                    data.put(FCMPlugin.FIELD_SENT_DATE, "sent " + ((elapsed_time > 60) ? (elapsed_time / 60) + "m" : elapsed_time + "s") + " ago");
                                    getGpsPosition(mContext, data);                   
                                    removeWarning = false;
		                    // TODO: remove notification from sharedprefs
                                }
                            }, 10000);
                }
                else{
                    Log.d(TAG, "WARNING expired... sent " + elapsed_time + "s ago");
                }
            }
            else{
		// TODO: save notifications to sharedprefs, to survive to reboots
                numNotifications++;
                Log.d(TAG, "\tiIncoming notifications: " + numNotifications);
                /*Log.d(TAG, "New notification added: " + type.toString());
                mNotificationsList.add(data);
                handler.postDelayed(
                        new Runnable(){
                            @Override
                            public void run() {
                                Log.d(TAG, "\tDELAYED sending pending notifications: " + mNotificationsList.size() + " - " + numNotifications);
                                Map<String, Object> _data;
                                for (int i = 0; i < mNotificationsList.size(); i++){
                                    _data = (Map)mNotificationsList.get(i);
                                    mNotificationsList.remove(i);
                                    numNotifications--;
                                }
                                Log.d(TAG, "\tDELAYED notifications sent: " + numNotifications);
                            }
                        }, 3000);
                */
                // if the user is not logged in, dont send the notification, but save the data.
                // for now, we don't care too much if the user has clicked on the notification or not.
                if (isLoggedIn){
                    sendNotification( mContext, data );
                }
                FCMPlugin.sendPushPayload( mContext, data );
                numNotifications--;
		// TODO: remove notification from sharedprefs
            }
        }
		
        //sendNotification(remoteMessage.getNotification().getTitle(), remoteMessage.getNotification().getBody(), remoteMessage.getData());
    }
    // [END receive_message]

    /**
     * Create and show a simple notification containing the received FCM message.
     *
     * @param messageBody FCM message body received.
     */
    public static void sendNotification(Context _ctx, Map<String, Object> data) {
        Intent intent = new Intent(_ctx, FCMPluginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        for (String key : data.keySet()) {
            intent.putExtra(key, data.get(key).toString());
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(_ctx, 0 /* (int) System.nanoTime() */ /* Request code */, intent,
                PendingIntent.FLAG_ONE_SHOT);

        String title = (data.get(FCMPlugin.FIELD_TITLE) != null) ? data.get(FCMPlugin.FIELD_TITLE).toString() : "";
        String body = (data.get(FCMPlugin.FIELD_BODY) != null) ? data.get(FCMPlugin.FIELD_BODY).toString() : "";
        String type = (data.get(FCMPlugin.FIELD_TYPE) != null) ? data.get(FCMPlugin.FIELD_TYPE).toString() : "-1";
        String rkey = (data.get(FCMPlugin.FIELD_RKEY) != null) ? data.get(FCMPlugin.FIELD_RKEY).toString() : "";
        String fuid = (data.get(FCMPlugin.FIELD_FUID) != null) ? data.get(FCMPlugin.FIELD_FUID).toString() : "";
        String email = (data.get(FCMPlugin.FIELD_EMAIL) != null) ? data.get(FCMPlugin.FIELD_EMAIL).toString() : "";

        Uri defaultSoundUri= RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(_ctx)
                .setSmallIcon(_ctx.getApplicationInfo().icon)
                .setContentTitle(title)
                //.setSubText("sub text")
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                //.setStyle(new NotificationCompat.InboxStyle()
                //        .addLine(data.get("title").toString())
                //        .setSummaryText(data.get(FCMPlugin.FIELD_EMAIL).toString()))
                //.setGroupSummary(true)
                .setContentIntent(pendingIntent)
                .setLights(Color.WHITE, 1500, 1000);

        if (data.get(FCMPlugin.FIELD_FUID) != null){
            Log.d(TAG, "\tsendNotification() group string: " + fuid);
            notificationBuilder.setGroup(fuid);
        }
        //notificationBuilder.setNumber(++numMsgs);
        
        int not_id = 0;
        if (type.equals( FCMPlugin.TYPE_WARNING )){
            notificationBuilder.setPriority(NotificationCompat.PRIORITY_MAX);
            notificationBuilder.setCategory(NotificationCompat.CATEGORY_ALARM);
            notificationBuilder.setColor(Color.RED);
            notificationBuilder.setContentText(body);
            notificationBuilder.setLights(Color.WHITE, 1000, 1000);

            if (data.get(FCMPlugin.FIELD_SENT_DATE) != null){
                notificationBuilder.setSubText(data.get(FCMPlugin.FIELD_SENT_DATE).toString());
            }
            if (android.os.Build.VERSION.SDK_INT >= 21){
                notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            }
            //.setSummaryText("Enviado hace " + data.get(FCMPlugin.FIELD_SENT_DATE) + " segundos.")
            not_id = 6;
        }
        else if(type.equals( FCMPlugin.TYPE_ACCEPTED_INVITATION ) || type.equals( FCMPlugin.TYPE_REJECTED_INVITATION ) || 
                type.equals( FCMPlugin.TYPE_PENDING_INVITATION ) || type.equals( FCMPlugin.TYPE_USER_UNFRIENDED )){
            String summary=email.toString();
            Object timestamp = data.get(FCMPlugin.FIELD_TIMESTAMP);
            if (timestamp != null){
                java.util.Date now = new java.util.Date();
                java.util.Date sent_timestamp = new java.util.Date( Long.valueOf(timestamp.toString()) );
                Long secs_ago = 0L;
                if (now.getTime() > sent_timestamp.getTime()){
                    secs_ago = ((now.getTime() - sent_timestamp.getTime()) / 1000);
                }
                else{
                    secs_ago = ((sent_timestamp.getTime() - now.getTime()) / 1000);
                }
                if (secs_ago > 0){
                    summary += (secs_ago < 60) ? " - " + secs_ago + "s ago" : " - " + (secs_ago / 60) + " mins ago";
                }
                rkey = timestamp.toString();
            }
            notificationBuilder.setContentText(body);
            notificationBuilder.setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(body)
                    .setSummaryText(summary)
                    );
            if (type.equals( FCMPlugin.TYPE_REJECTED_INVITATION ) || type.equals( FCMPlugin.TYPE_USER_UNFRIENDED )){
                notificationBuilder.setColor(Color.RED);
            }
            else{
                notificationBuilder.setColor(Color.GREEN);
            }
            // collapse/replace duplicated nots: fuid + type
            // for example: multiple invitations to be friends of the same person, will be grouped on the friend's mobile 
            not_id = Integer.valueOf(type);
        }
        else{
            notificationBuilder.setContentText(body);
            notificationBuilder.setCategory(NotificationCompat.CATEGORY_MESSAGE);
        }

        NotificationManager notificationManager =
                (NotificationManager) _ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        Notification n = notificationBuilder.build();
        if (type.equals( FCMPlugin.TYPE_WARNING )){
            // TODO: if warning sent >= 1h do not set this
            //n.priority = Notification.IMPORTANCE_MAX;
            n.flags = Notification.FLAG_INSISTENT; // | Notification.FLAG_ONGOING_EVENT;
        }

        if (numNotifications > 5){
            Log.d(TAG, "supressing sounds due to num notifications: " + numNotifications);
            //notificationManager.setInterruptionFilter(notificationManager.INTERRUPTION_FILTER_ALARMS);
        }
        else{
            Log.d(TAG, "activating sounds. num notifications: " + numNotifications);
            //notificationManager.setInterruptionFilter(notificationManager.INTERRUPTION_FILTER_ALL);
        }

        Log.d(TAG, "posting notification with tag+id: " + rkey + ":" + not_id);
        // the pair (tag, id) must be unique within your application
        // https://developer.android.com/reference/android/app/NotificationManager.html#notify%28java.lang.String,%20int,%20android.app.Notification%29
        // If a notification with the same tag and id has already been posted by your 
        // application and has not yet been canceled, it will be replaced by the updated information.
        notificationManager.notify(rkey,
                not_id,//Integer.valueOf(type.toString()) /* ID of notification */, 
                n);
    }

    public static void getGpsPosition(final Context _ctx, final Map<String, Object> data){
        LocationManager locationManager = (LocationManager) _ctx.getSystemService(_ctx.LOCATION_SERVICE);
        Log.d(TAG, "\tgetGpsPosition()");
 
        final LocationRequest mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(5000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setExpirationDuration((60 * 15) * 1000); // 15 minutes

        mGoogleApiClient = FCMPlugin.get_gapi_client();
        start_listening(_ctx, mGoogleApiClient, mLocationRequest, data);
    
    }

    public static void start_listening(final Context _ctx, final GoogleApiClient gApiClient, 
            LocationRequest locRequest, 
            final Map<String, Object> data){

        try{
            if (Looper.myLooper()==null){
                Looper.prepare();
            }

            if (gApiClient != null && gApiClient.isConnected()){
                Log.d(TAG, "start_listening() connected");
                final Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                        gApiClient);

                final LocationListener mLocListener =
                            new LocationListener() {
                                @Override
                                public void onLocationChanged(Location location) {
                                    Log.d(TAG, "newLocation. lat: " + location.getLatitude());
                                    Log.d(TAG, "newLocation. accuracy: " + location.getAccuracy());

                                    if (parseAndSendPosition(_ctx, gApiClient, location, this, data)){
                                        Log.d(TAG, "newLocation ok. removing updates ");
                                        if (gApiClient != null && gApiClient.isConnected()){
                                            Log.d(TAG, "gApiClient connected");
                                        }
                                        else{
                                            Log.e(TAG, "gApiClient null");
                                        }
                                    }
                                    else{
                                        Log.d(TAG, "newLocation no ok. bad position");
                                        if (gApiClient != null && gApiClient.isConnected()){
                                            Log.d(TAG, "gApiClient connected");
                                        }
                                    }
                                }
                //                @Override
                                public void onProviderDisabled(String provider) {
                                    Log.d(TAG, "newLocation. provider disabled: " + provider);
                                }
                            };

                if (/*1==0 &&*/ mLastLocation != null && mLastLocation.getAccuracy() < FCMPlugin.VALID_LAST_ACCURACY) {
                    Log.d(TAG, "lastKnowLocation. lat: " + mLastLocation.getLatitude());
                    Log.d(TAG, "lastKnowLocation. accuracy: " + mLastLocation.getAccuracy());

                    if (parseAndSendPosition(_ctx, gApiClient, mLastLocation, mLocListener, data)){
                        Log.d(TAG, "lastKnowLocation. accuracy: " + mLastLocation.getAccuracy());
                        if (gApiClient != null && gApiClient.isConnected()){
                            Log.d(TAG, "gApiClient connected");
                        }
                    }
                    else{
                        Log.d(TAG, "lastKnowLocation. too far away. accuracy: " + mLastLocation.getAccuracy());
                        LocationServices.FusedLocationApi.requestLocationUpdates(gApiClient, locRequest, mLocListener);
                    }
                }
                else{
                    Log.d(TAG, "no lastKnowLocation. requesting new locations");
                    // TODO: if the GPS is disabled, or we're not allowed to use it, post a notification remainding the user to enable it
                    LocationServices.FusedLocationApi.requestLocationUpdates(gApiClient, locRequest, mLocListener);

                    Log.d(TAG, "no lastKnowLocation 1");
                }

            }
            else{
                Log.d(TAG, "start_listening() gApiClient not connected");
                // TODO: reconnect in the user has not started TC or android has killed it, but we've received a new push
                //FCMPlugin.connect_gservices(_ctx);
                //start_listening(_ctx, gApiClient, locRequest, data);
            }
        }
        catch(Exception e){
            Log.d(TAG, "==> FCMPlugin exception: " + e.getMessage());
            e.printStackTrace();
        }

    }

    public static boolean parseAndSendPosition(
            Context _ctx, 
            GoogleApiClient gApiClient, 
            Location location, 
            LocationListener locListener, 
            Map<String, Object> data){
        try{
            JSONObject jo = new JSONObject(data.get("position").toString());
            Log.d(TAG, "parseAndSendPosition. position.latitude: " + jo.getDouble("latitude"));

            Location rLoc = new Location("xxx");
            rLoc.setLatitude(jo.getDouble("latitude"));
            rLoc.setLongitude(jo.getDouble("longitude"));
            int accuracy = 100;
            if (jo.has("accuracy")){
                accuracy = jo.getInt("accuracy");
            }
            rLoc.setAccuracy(accuracy);

            float dTo = rLoc.distanceTo(location);
            if (dTo < FCMPlugin.WARNING_RADIUS){
                Log.d(TAG, "parseAndSendPosition. distanceTo: " + rLoc.distanceTo(location));

                data.put("our_position", 
                        "{ \"latitude\": " + location.getLatitude() 
                        + ", \"longitude\": " + location.getLongitude()
                        + ", \"accuracy\": " + location.getAccuracy() + "}"
                        );
                sendNotification(_ctx, data);
                FCMPlugin.sendPushPayload( _ctx, data );
                LocationServices.FusedLocationApi.removeLocationUpdates(gApiClient, locListener);
                return true;
            }
            else{
                Log.d(TAG, "parseAndSendPosition. too far distanceTo: " + rLoc.distanceTo(location));
            }
        
        }
        catch(Exception e){
            Log.d(TAG, "parseAndSendPosition. exception: " + e.getLocalizedMessage());
            e.printStackTrace();
        }

        try{
            LocationServices.FusedLocationApi.removeLocationUpdates(gApiClient, locListener);
        }
        catch(Exception e){
            e.printStackTrace();
        }

        return false;
    }
}
