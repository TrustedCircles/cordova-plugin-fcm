package com.gae.scaffolder.plugin;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.content.Intent;

import java.util.Map;
import java.util.HashMap;

public class FCMPluginActivity extends Activity {
    private static String TAG = "FCMPlugin";

    /*
     * this activity will be started if the user touches a notification that we own. 
     * We send it's data off to the push plugin for processing.
     * If needed, we boot up the main activity to kickstart the application. 
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "==> FCMPluginActivity onCreate");

        if (!FCMPlugin.isLoggedIn(getApplicationContext())){
            Log.d(TAG, "==> FCMPluginActivity onCreate. NOT LOGGED IN");
            return;
        }
        else{
            Log.d(TAG, "==> FCMPluginActivity onCreate. LOGGED IN");
        }

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("wasTapped", true);
        if (getIntent().getExtras() != null) {
            Log.d(TAG, "==> USER TAPPED NOTFICATION");
            for (String key : getIntent().getExtras().keySet()) {
                String value = getIntent().getExtras().getString(key);
                // FIXME: add the key out of the loop, instead of check this here.
                if ("wasTapped".equals(key)){
                    continue;
                }
                Log.d(TAG, "\tKey: " + key + " Value: " + value);
                data.put(key, value);
            }
        }
        else{
            Log.d(TAG, "==> USER TAPPED NOTFICATION, but has no extras");
        }

        // The notification should already be saved at this point.
        FCMPlugin.sendPushPayload(this, data);
        forceMainActivityReload((getIntent() != null) ? getIntent() : null);
        finish();
    }

    private void forceMainActivityReload(Intent srcInt) {
        PackageManager pm = getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(getApplicationContext().getPackageName());
        
        if (srcInt != null){
            launchIntent.putExtras(srcInt);
        }
        startActivity(launchIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "==> FCMPluginActivity onResume");
        final NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }
	
	@Override
	public void onStart() {
		super.onStart();
		Log.d(TAG, "==> FCMPluginActivity onStart");
	}
	
	@Override
	public void onStop() {
		super.onStop();
		Log.d(TAG, "==> FCMPluginActivity onStop");
	}

}
