package com.gae.scaffolder.plugin;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import android.util.Log;

import android.support.v4.app.NotificationCompat;
import android.media.RingtoneManager;
import android.app.NotificationManager;
import android.app.Notification;
import android.net.Uri;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
	
import android.preference.PreferenceManager;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import android.os.Bundle;

//import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.CommonStatusCodes;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Iterator;

public class FCMPlugin extends CordovaPlugin {
 
    private static final String TAG = "FCMPlugin";
    private static final String SHARED_PREFS = "trustedcircles";
	
    public static CordovaWebView gWebView;
    public static Context mContext;
    public static String notificationCallBack = "FCMPlugin.onNotificationReceived";
    public static String tokenRefreshCallBack = "FCMPlugin.onTokenRefreshReceived";
    public static Boolean notificationCallBackReady = false;
    public static Map<String, Object> lastPush = null;
    public static ArrayList<Map> lastArray = new ArrayList<Map>();
    public static String TYPE_REJECTED_INVITATION = "-1";
    public static String TYPE_PENDING_INVITATION = "0";
    public static String TYPE_ACCEPTED_INVITATION = "1";
    public static String TYPE_INCOMING_ROUTE = "3";
    public static String TYPE_FINISHED_ROUTE = "4";
    public static String TYPE_USER_UNFRIENDED = "5";
    public static String TYPE_WARNING = "6";
    public static String TYPE_FINISHED_WARNING = "7";
    public static String TYPE_NEW_CAMPAIGN = "106";
    
    public static String KEY_PREF_WARNINGS_TOTAL = "warnings_total";
    public static String KEY_PREF_RECEIVE_WARNINGS = "receive_warnings";
    
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_BODY = "body";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_FUID = "fuid";
    public static final String FIELD_SENT_DATE = "sent_date";
    public static final String FIELD_RKEY = "key";
    public static final String FIELD_TIMESTAMP = "timestamp";
    public static final String FIELD_EMAIL = "email";

    public static final int WARNING_RADIUS = 1500;
    public static final int VALID_LAST_ACCURACY = 2000;

    private static String ACTION_IS_READY = "ready";
    private static String ACTION_REGISTER_NOTIFICATION = "registerNotification";
    private static String ACTION_GET_TOKEN = "getToken";
    private static String ACTION_SET_LOGGED_IN = "setLoggedIn";
    private static String ACTION_SET_PREFERENCE = "setPreference";
    private static String ACTION_GET_PREFERENCE = "getPreference";
    private static String ACTION_SUBSCRIBE_TO_TOPIC = "subscribeToTopic";
    private static String ACTION_UNSUBSCRIBE_FROM_TOPIC = "unsubscribeFromTopic";
    private static String ACTION_LOG_EVENT = "logEvent";
    private static String ACTION_GET_GSERVICES_STATUS = "getGServicesStatus";
    private static String ACTION_SHOW_NOTIFICATION = "showNotification";
    private static String ACTION_DISMISS_NOTIFICATION = "dismissNotification";

    private static boolean mIsLoggedIn = false;
    private static String mUserId = "";

    private static FirebaseAnalytics mFirebaseAnalytics;
    public String mEventSelectContent = "select_content";
    public String mEventShare = "share";
    public String mEventSignUp = "sign_up";
    public String mEventSignOff = "sign_off";
    public String mEventSearch = "search";
    public String mEventTutorialOn = "tutorial_begin";
    public String mEventTutorialOff = "tutorial_complete";
    public String mEventViewItem = "view_item";
    public String mEventViewItemList = "view_item_list";

    public static GoogleApiClient mGoogleApiClient = null;
	 
	public FCMPlugin() {}
	
    @Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		gWebView = webView;
        mContext = gWebView.getContext();
		Log.d(TAG, "==> FCMPlugin initialize. pushes: " + lastArray.size());
		FirebaseMessaging.getInstance().subscribeToTopic("android");
		FirebaseMessaging.getInstance().subscribeToTopic("all");

        try{
            mFirebaseAnalytics = FirebaseAnalytics.getInstance( this.cordova.getActivity().getApplicationContext());//mContext);
            mFirebaseAnalytics.setAnalyticsCollectionEnabled(true);
            mFirebaseAnalytics.setMinimumSessionDuration(1000);
            mFirebaseAnalytics.logEvent("plugin_init", null);
        }
        catch(Exception e){
            Log.e(TAG, "FCMPlugin analytics exception");
            e.printStackTrace();
        }

	    connect_gservices(mContext);
	}
	 
	public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {

		Log.d(TAG,"==> FCMPlugin execute: "+ action);
		
		try{
			// READY //
			if (action.equals( ACTION_IS_READY )) {
				//
				callbackContext.success();
			}
			else if (action.equals( ACTION_SET_LOGGED_IN )) {
				cordova.getActivity().runOnUiThread(
					new Runnable() {
					public void run() {
					try{
                        Log.d(TAG, "setLoggedIn() uid: " + args.getString(0));
                        Log.d(TAG, "setLoggedIn() new status: " + args.getString(1));
                        setLoggedIn(args.getString(0), args.getString(1).equals("true"));
                        callbackContext.success();
					}
					catch(Exception e){
						Log.e(TAG, "setLoggedIn() error: " + e.getMessage());
						callbackContext.error(e.getMessage());
					}
				}
				});
			}
			else if (action.equals( ACTION_SET_PREFERENCE )) {
				cordova.getActivity().runOnUiThread(
					new Runnable() {
					public void run() {
					try{
                        Log.d(TAG, "setPreference: " + args.getString(0) + " - " + args.getString(1));
                        setPreference(args.getString(0), args.getString(1));
                        callbackContext.success();
					}
					catch(Exception e){
						Log.e(TAG, "setPreference() error: " + e.getMessage());
						callbackContext.error(e.getMessage());
					}
				}
				});
            }
			else if (action.equals( ACTION_GET_PREFERENCE )) {
				cordova.getActivity().runOnUiThread(
					new Runnable() {
					public void run() {
					try{
                        String value = getPreference(mContext, args.getString(0));
                        Log.d(TAG, "getPreference: " + args.getString(0) + " - " + value);
                        callbackContext.success( value );
					}
					catch(Exception e){
						Log.e(TAG, "getPreference() error: " + e.getMessage());
						callbackContext.error(e.getMessage());
					}
				}
				});
            }
			// GET TOKEN //
			else if (action.equals( ACTION_GET_TOKEN )) {
				cordova.getActivity().runOnUiThread(new Runnable() {
					public void run() {
						try{
							String token = FirebaseInstanceId.getInstance().getToken();
							callbackContext.success( token );
							Log.d(TAG,"\tToken: "+ token);
						}catch(Exception e){
							Log.d(TAG,"\tError retrieving token: " + e.getMessage());
                            callbackContext.error(e.getMessage());
						}
					}
				});
			}
			// GET GSERVICES STATUS //
			else if (action.equals( ACTION_GET_GSERVICES_STATUS )) {
				cordova.getActivity().runOnUiThread(new Runnable() {
					public void run() {
						try{
							callbackContext.success( get_gservices_status() );
						}catch(Exception e){
							Log.d(TAG,"\tError retrieving token");
                            e.printStackTrace();
						}
					}
				});
			}
			// NOTIFICATION CALLBACK REGISTER //
			else if (action.equals( ACTION_REGISTER_NOTIFICATION )) {
				notificationCallBackReady = true;
				cordova.getActivity().runOnUiThread(new Runnable() {
					public void run() {
					//	if(lastPush != null) FCMPlugin.sendPushPayload(mContext, lastPush );
						lastPush = null;
                        Log.d(TAG, "XXX push list BEFORE: " + lastArray.size());
                        // XXX: possible race condition, if we're sending the notifications to the webview, and we receive a new one
                        for (int i=0; i < lastArray.size();i++){
                            Log.d(TAG, "XXX push list item: " + i);// + " - " + lastArray.get(i));
                            FCMPlugin.sendPushPayload( mContext, lastArray.get(i) );
                        }
                        lastArray.clear();
                        Log.d(TAG, "XXX push list AFTER: " + lastArray.size());
                        NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
                        notificationManager.cancelAll();
						callbackContext.success();
					}
				});
			}
			// UN/SUBSCRIBE TOPICS //
			else if (action.equals( ACTION_SUBSCRIBE_TO_TOPIC )) {
				cordova.getThreadPool().execute(new Runnable() {
					public void run() {
						try{
                            if (args.getString(0) == null || args.getString(0).equals("null")){
                                Log.d(TAG, "subscribeToTopic: null");
                                return;
                            }
							FirebaseMessaging.getInstance().subscribeToTopic( args.getString(0) );
							callbackContext.success();
						}catch(Exception e){
							callbackContext.error(e.getMessage());
						}
					}
				});
			}
			else if (action.equals( ACTION_UNSUBSCRIBE_FROM_TOPIC )) {
				cordova.getThreadPool().execute(new Runnable() {
					public void run() {
						try{
							FirebaseMessaging.getInstance().unsubscribeFromTopic( args.getString(0) );
							callbackContext.success();
						}catch(Exception e){
							callbackContext.error(e.getMessage());
						}
					}
				});
			}
			else if (action.equals( ACTION_LOG_EVENT )) {
				cordova.getThreadPool().execute(new Runnable() {
					public void run() {
						try{
                            Log.d(TAG, "FCMPlugin js logEvent() key: " + args.getString(0) + " value: " + args.getString(1));
                            logEvent(args, callbackContext);
						}catch(Exception e){
							callbackContext.error(e.getMessage());
						}
					}
				});
            }
			else if (action.equals( ACTION_SHOW_NOTIFICATION )) {
				cordova.getThreadPool().execute(new Runnable() {
					public void run() {
						try{
                            Log.d(TAG, "FCMPlugin showNotificaton: " + args.getString(0)); // title 
                            /*Map<String, Object> data = new HashMap<String, Object>();
                            data.put(FCMPlugin.FIELD_TYPE, args.getString(0));
                            data.put("title", args.getString(1));
                            data.put("body", args.getString(2));
                            data.put(FCMPlugin.FIELD_FUID, args.getString(3));
                            MyFirebaseMessagingService.sendNotification(mContext, data);
                            */
                            FCMPlugin.postNotification(null, args.getString(0), args.getString(1));
                            callbackContext.success();
						}catch(Exception e){
							callbackContext.error(e.getMessage());
						}
                    }
                });
            }
			else if (action.equals( ACTION_DISMISS_NOTIFICATION )) {
				cordova.getThreadPool().execute(new Runnable() {
					public void run() {
						try{
                            Log.d(TAG, "FCMPlugin dismissNotificaton: " + args.getString(0)); //tag
                            NotificationManager nManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
                            nManager.cancel(args.getString(0), args.getInt(1));
                            // if (args.getInt(1) == 0){ //dismiss route notification
                            int id = getPushByTimestamp( args.getString(0) );
                            Log.d(TAG, "FCMPlugin dismissNotificaton() to remove: " + id); //tag
                            if (id != -1){
                                lastArray.remove(id);
                            }
                            callbackContext.success();
						}catch(Exception e){
							callbackContext.error(e.getMessage());
						}
                    }
                });
            }
			else{
				callbackContext.error("Method not found");
				return false;
			}
		}catch(Exception e){
			Log.d(TAG, "ERROR: onPluginAction: " + e.getMessage());
			callbackContext.error(e.getMessage());
			return false;
		}
		
		return true;
	}

    public static Context getContext(){
        return mContext;
    }

    public static void sendTokenRefresh(String token){
        Log.d(TAG, "==> FCMPlugin sendRefreshToken");
        try{
            String callBack = "javascript:" + tokenRefreshCallBack + "('" + token + "')";
            if (gWebView != null){
                gWebView.sendJavascript(callBack);
            }
            else{
                Log.d(TAG, "\tERROR sendRefreshToken: gwebview null");
            }
        }catch (Exception e) {
            Log.d(TAG, "\tERROR sendRefreshToken: " + e.getMessage());
        }
    }
    
	public static void sendPushPayload(Context _ctx, Map<String, Object> payload){
		Log.d(TAG, "==> FCMPlugin sendPushPayload. 0 pushes in the queue BEFORE: " + lastArray.size());
		Log.d(TAG, "\tnotificationCallBackReady: " + notificationCallBackReady);
		Log.d(TAG, "\tgWebView: " + gWebView);
        Context _ltx = (mContext != null) ? mContext : _ctx;
        boolean is_a_warning = false;
        int push_id = -1;

	    try {
		    JSONObject jo = new JSONObject();
			for (String key : payload.keySet()) {
			    jo.put(key, payload.get(key));
				Log.d(TAG, "\tpayload: " + key + " => " + payload.get(key));
            }

            String callBack = "javascript:" + notificationCallBack + "(" + jo.toString() + ")";
            if(notificationCallBackReady && gWebView != null){
                Log.d(TAG, "\tSent PUSH to view: " + callBack);
                gWebView.sendJavascript(callBack);
                Log.d(TAG, "==> FCMPlugin sendPushPayload 1. pushes in the queue AFTER: " + lastArray.size());
            }
            else {
                Log.d(TAG, "\tView not ready. SAVED NOTIFICATION: " + callBack);
                lastPush = payload;
                if ((push_id = FCMPlugin.isPushInTheList(payload)) != -1){
                    lastArray.remove(push_id);
                }
                lastArray.add(payload);
                Log.d(TAG, "==> FCMPlugin sendPushPayload 2. pushes in the queue AFTER: " + lastArray.size());
            }
        } catch (Exception e) {
            Log.d(TAG, "\tERROR sendPushToView. SAVED NOTIFICATION: " + e.getMessage());
            lastPush = payload;
            if ((push_id = FCMPlugin.isPushInTheList(payload)) != -1){
                lastArray.remove(push_id);
            }
            lastArray.add(payload);
            Log.d(TAG, "==> FCMPlugin sendPushPayload 3. pushes in the queue AFTER: " + lastArray.size());
        }
	}

    /**
     * Removes from the list of pushes, the push of an incoming route, if the route has been finished.
     * This avoids to send multiple notifications to the user.
     *
     * There're other measures similar to this one:
     * - notifications with collapse_key in the payload, to discard notifications of same type 
     *   on google servers, when google can not deliver the message inmmediately.
     * - notification key of the notification itself.
     */
    public static void cleanRoutesPushes(){
        String type;
        String fuid;
        String rkey;
        int x=-1;
        Map<String, Object> push = null;
        try{
            for (int i = 0; i < lastArray.size(); i++){
                push = lastArray.get(i);

                if (push != null && push.containsKey(FCMPlugin.FIELD_TYPE) && 
                        push.containsKey(FCMPlugin.FIELD_FUID) && push.containsKey(FCMPlugin.FIELD_RKEY)){
                    type = push.get(FCMPlugin.FIELD_TYPE).toString();
                    fuid = push.get(FCMPlugin.FIELD_FUID).toString();
                    rkey = push.get(FCMPlugin.FIELD_RKEY).toString();
                    
                    if (type != null && type == FCMPlugin.TYPE_FINISHED_ROUTE){
                        if ((x = FCMPlugin.getPushIndex(fuid, rkey, FCMPlugin.TYPE_INCOMING_ROUTE)) != -1){
                            lastArray.remove( x );
                            Log.d(TAG, "==> FCMPlugin cleanRoutesPushes() removing INCOMING route, and leaving only FINISHED route: " + rkey);
                            return;
                        }
                    }
                }
            }
        }
        catch(Exception e){
            Log.d(TAG, "==> FCMPlugin exception: " + e.getMessage());
            e.printStackTrace();
        }

    }
    
    public static int getPushByTimestamp(String _timestamp){
        String timestamp;
        Map<String, Object> push = null;
        try{
            if (_timestamp == null){
                return -1;
            }

            Log.d(TAG, "==> FCMPlugin getPushByTimestamp() timestamp: " + _timestamp + " - pushes: " + lastArray.size());

            for (int i = 0; i < lastArray.size(); i++){
                push = lastArray.get(i);
                if (push != null && push.containsKey(FCMPlugin.FIELD_TIMESTAMP)){
                    timestamp = push.get(FCMPlugin.FIELD_TIMESTAMP).toString();
                    if (timestamp != null && timestamp.equals( _timestamp )){
                        Log.d(TAG, "==> FCMPlugin getPushByTimestamp() found. index: " + i + " - " + timestamp);
                        return i;
                    }
                    Log.d(TAG, "==> FCMPlugin getPushByTimestamp() NOPE. index: " + timestamp);
                }
                else{
                    Log.d(TAG, "==> FCMPlugin getPushByTimestamp() no field timestamp");
                }
            }
        }
        catch(Exception e){
            Log.d(TAG, "==> FCMPlugin exception: " + e.getMessage());
            e.printStackTrace();
        }
        Log.d(TAG, "==> FCMPlugin getPushByTimestamp() NOT found: " + _timestamp);

        return -1;
    }

    public static int getPushIndex(String _fuid, String _rkey, String _type){
        String type;
        String fuid;
        String rkey;
        Map<String, Object> push = null;
        try{
            for (int i = 0; i < lastArray.size(); i++){
                push = lastArray.get(i);
                if (push != null && push.containsKey(FCMPlugin.FIELD_TYPE) && push.containsKey(FCMPlugin.FIELD_FUID) && push.containsKey("key")){
                    type = push.get(FCMPlugin.FIELD_TYPE).toString();
                    fuid = push.get(FCMPlugin.FIELD_FUID).toString();
                    rkey = push.get(FCMPlugin.FIELD_RKEY).toString();
                    if (type != null && type.equals ( _type ) && fuid.equals( _fuid ) && rkey.equals( _rkey )){
                        Log.d(TAG, "==> FCMPlugin getRouteIndex() found. rkey: " + _rkey + " index: " + i);
                        return i;
                    }
                }
            }
        }
        catch(Exception e){
            Log.d(TAG, "==> FCMPlugin exception: " + e.getMessage());
            e.printStackTrace();
        }

        return -1;
    }

    /**
     * Checks out if a given push is in the list of pushes, checking type + timestamp.
     * A push can be added twice, if the message arrives in background (1 push), 
     * and later the user clicks on the notification.
     */
    public static int isPushInTheList(Map<String, Object> payload){
        if (payload == null || payload.get(FCMPlugin.FIELD_TIMESTAMP) == null || payload.get(FCMPlugin.FIELD_TYPE) == null){
            return -1;
        }

        String newpush_timestamp = payload.get(FCMPlugin.FIELD_TIMESTAMP).toString();
        String type = payload.get(FCMPlugin.FIELD_TYPE).toString();
        Map<String, Object> push = null;
        try{
            for (int idx = 0; idx < lastArray.size(); idx++){
                push = lastArray.get(idx);
                Log.d(TAG, "==> FCMPlugin isPushInTheList. timestamp: " + newpush_timestamp + " - list_timestamp: " + push.get(FCMPlugin.FIELD_TIMESTAMP).toString());
                if (push != null && push.containsKey(FCMPlugin.FIELD_TYPE) && push.containsKey(FCMPlugin.FIELD_TIMESTAMP)){
                    if (push.get(FCMPlugin.FIELD_TYPE).toString().equals( type ) && push.get(FCMPlugin.FIELD_TIMESTAMP).toString().equals( newpush_timestamp )){
                        Log.d(TAG, "==> FCMPlugin isPushInTheList: true");
                        return idx;
                    }
                }
            }
        }
        catch(Exception e){
            Log.d(TAG, "==> FCMPlugin exception: " + e.getMessage());
            e.printStackTrace();
        }

        Log.d(TAG, "==> FCMPlugin isPushInTheList: false");
        return -1;
    }

    public void logEvent (final JSONArray args, final CallbackContext callbackContext){
        try{
            Log.d(TAG, "logEvent() key: " + args.getString(0) + " - value: " + args.getString(1) + " length: " + args.length());

	        // borrowed from cordova-plugin-firebase :]
            final Bundle parms = (!args.getString(1).equals("null")) ? new Bundle() : null;
            if (parms != null){
                JSONObject obj = args.getJSONObject(1);
                Iterator iter = obj.keys();
                while(iter.hasNext()){
                    String key = (String)iter.next();
                    Object value = obj.get(key);
                    Log.d(TAG, " key: " + key + " value: " + value);
                    if (value instanceof Integer || value instanceof Double) {
                        parms.putFloat(key, ((Number)value).floatValue());
                    }
                    else{
                        parms.putString(key, value.toString());
                    }
                }
            }

            //parms.putString(args.getString(0), args.getString(1));
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try{
                        mFirebaseAnalytics.logEvent(args.getString(0), parms);
                        callbackContext.success();
                        Log.d(TAG, "logEvent ok");
                    }
                    catch (Exception e){
                        callbackContext.error(e.getMessage());
                        Log.e(TAG, "logEvent exception: " + e.getMessage());
                    }
                }
            });

		} catch (Exception e) {
            Log.e(TAG, "logEvent() exception: " + e.getMessage());
            callbackContext.error(e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void postEvent(String event, Bundle parms){
        try{
            if (FCMPlugin.mFirebaseAnalytics != null){
                mFirebaseAnalytics.logEvent(event, parms);
                Log.d(TAG, "postEvent ok");
            }
        }
        catch (Exception e){
	    Log.e(TAG, "postEvent exception: " + e.getMessage());
        }
    }

    public static void setPreference(String key, String value){
        try{
            if (mContext != null){
                SharedPreferences sPref = PreferenceManager.getDefaultSharedPreferences(mContext);
                SharedPreferences.Editor ed = sPref.edit();
                ed.putString(key, value);
                ed.commit();
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    public static String getPreference(Context _ctx, String key){
        if (_ctx != null){
            try{
                SharedPreferences sPref = PreferenceManager.getDefaultSharedPreferences(_ctx);
                Log.d(TAG, "getPreference(): " + sPref.getString(key, null));
                return sPref.getString(key, null);
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
        return null;
    }


    public static void setLoggedIn(String uid, boolean status){
        mIsLoggedIn = status;
        if (mContext != null){
            SharedPreferences sPref = PreferenceManager.getDefaultSharedPreferences(mContext);
            SharedPreferences.Editor ed = sPref.edit();
            ed.putBoolean("is_logged_in", status);
            ed.putString("uid", uid);
            Log.d(TAG, "setLoggedIn() status: " + status + " uid: " + uid);
            ed.commit();
        }
        if (mIsLoggedIn == false){
            mUserId = null;
            new Thread(new Runnable(){
                @Override
                public void run(){
                    try{
                        FirebaseInstanceId.getInstance().deleteInstanceId();
                    }
                    catch(Exception e){
                        e.printStackTrace();
                    }
                }
            });
        }
        else{
            mUserId = uid;
        }
    }

    public static String getUserId(){
        return mUserId;
    }
    
    public static boolean isLoggedIn(Context _ctx){
        if (_ctx != null){
            SharedPreferences sPref = PreferenceManager.getDefaultSharedPreferences(_ctx);
	        Log.d(TAG, "setLoggedIn() isLoggedIn(): " + sPref.getBoolean("is_logged_in", false));
	        return sPref.getBoolean("is_logged_in", false);
        }
        return false;
    }

/*    public void crashReport(){
        FirebaseCrash.report(new Exception("My first Android non-fatal error"));
    }
*/

    public static void connect_gservices(Context _ctx){
        try{
            mGoogleApiClient = new GoogleApiClient.Builder(_ctx)
                 .addApi(LocationServices.API)
                 .addConnectionCallbacks(
                         new GoogleApiClient.ConnectionCallbacks() {
                             @Override
                             public void onConnected( Bundle bundle ){
                                Log.d(TAG, "googleServices onConnected()");
                             }

                             @Override
                             public void onConnectionSuspended( int i ){
                                Log.d(TAG, "googleServices onConnection() suspended");
                             }
                         })
                 .addOnConnectionFailedListener(
                         new GoogleApiClient.OnConnectionFailedListener() {
                             @Override
                             public void onConnectionFailed( ConnectionResult cResult ){
                                // TODO: show a notification
                                try{
                                    Log.d(TAG, "googleServices onConnection() failed: " + cResult.getErrorCode() + " - " + cResult.getErrorMessage());
                                }
                                catch(Exception e){
                                    e.printStackTrace();
                                }
                                /*try{
                                    Bundle params = new Bundle();
                                    params.putString("item_id", cResult.getErrorCode() + "_" + cResult.getErrorMessage());
                                    params.putString("content_type", "google_services_error");
                                    FCMPlugin.postEvent("select_content", params);

                                    if (cResult.getErrorCode() == 2){
                                        //Intent intent = new Intent(_ctx, FCMPluginActivity.class);
                                        Intent intent = new Intent(Intent.ACTION_VIEW);
                                        intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.gms"));
                                        //intent.putExtra(FCMPlugin.FIELD_TYPE, "8");
                                        //intent.putExtra("title", "GooglePlayServices desactualizado");
                                        //intent.putExtra("body", "Si no lo actualizas, Trusted Circles no podrá obtener posición GPS ni recibir avisos de ayuda.");
                                        //intent.putExtra("event_num", 0);
                                        FCMPlugin.postNotification(intent,
                                            "GooglePlayServices desactualizado", 
                                            "Si no lo actualizas, Trusted Circles no funcionará correctamente."
                                        );
                                    }
                                }
                                catch (Exception e){
                                }*/
                             }
                         })
                 .build();
            mGoogleApiClient.connect();
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    public JSONObject get_gservices_status(){
        try{
            int statusCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(mContext);
            //Log.d(TAG, "is error recoverable? " + GoogleApiAvailability.getInstance().isUserResolvableError(resultCode) + " code: " + statusCode);
            JSONObject jo = new JSONObject();
            jo.put("code", statusCode);
            jo.put("message", CommonStatusCodes.getStatusCodeString(statusCode));
            return jo;
        }
        catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public static GoogleApiClient get_gapi_client(){
        return mGoogleApiClient;
    }

    public static void postNotification(Intent _intent, String title, String body){
        Context _ctx = FCMPlugin.mContext;
        if (_ctx == null){
            Log.e(TAG, "postNotification() mContext null");
            return;
        }
        Intent intent = null;
        if (_intent == null){
            intent = new Intent(_ctx, FCMPluginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        }
        else{
            intent = _intent;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(
		_ctx, 0 /* (int) System.nanoTime() */ /* Request code */, intent,
                PendingIntent.FLAG_ONE_SHOT);
        Uri defaultSoundUri= RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(_ctx)
                .setSmallIcon(_ctx.getApplicationInfo().icon)
                .setContentTitle(title)
                .setContentText(body)
                //.setSubText("sub text")
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                //.setStyle(new NotificationCompat.InboxStyle()
                //        .addLine(data.get("title").toString())
                //        .setSummaryText(data.get(FCMPlugin.FIELD_EMAIL).toString()))
                .setContentIntent(pendingIntent);
        NotificationManager notificationManager =
                (NotificationManager) _ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        Notification n = notificationBuilder.build();
        notificationManager.notify(null,
                999,//Integer.valueOf(type.toString()) /* ID of notification */, 
                n);
    }

    // @see: https://github.com/fechanique/cordova-plugin-fcm/pull/186/ and 175
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "==> FCMPlugin Destroy");
        gWebView = null;
        notificationCallBackReady = false;
    }
} 
