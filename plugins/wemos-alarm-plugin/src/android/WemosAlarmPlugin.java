package com.uceng48.wemosalarm;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;

public class WemosAlarmPlugin extends CordovaPlugin {

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        try {
            Context context = cordova.getActivity().getApplicationContext();

            if ("start".equals(action)) {
                String ip = args.optString(0, "[192.168.4.1](http://192.168.4.1)");
                int intervalMs = args.optInt(1, 1000);

                Intent intent = new Intent(context, WemosAlarmService.class);
                intent.setAction(WemosAlarmService.ACTION_START);
                intent.putExtra("ip", ip);
                intent.putExtra("intervalMs", intervalMs);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent);
                } else {
                    context.startService(intent);
                }

                callbackContext.success("Wemos alarm service started");
                return true;
            }

            if ("stop".equals(action)) {
                Intent intent = new Intent(context, WemosAlarmService.class);
                intent.setAction(WemosAlarmService.ACTION_STOP);
                context.startService(intent);

                callbackContext.success("Wemos alarm service stopped");
                return true;
            }

            if ("requestBatteryIgnore".equals(action)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }

                callbackContext.success("Battery optimization request opened");
                return true;
            }

            return false;
        } catch (Exception e) {
            callbackContext.error(e.getMessage());
            return true;
        }
    }
}
