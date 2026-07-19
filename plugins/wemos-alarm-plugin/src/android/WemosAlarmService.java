package com.uceng48.wemosalarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

public class WemosAlarmService extends Service {

    public static final String ACTION_START = "com.uceng48.wemosalarm.START";
    public static final String ACTION_STOP = "com.uceng48.wemosalarm.STOP";

    private static final String MONITOR_CHANNEL_ID = "wemos_monitor_channel";
    private static final String ALARM_CHANNEL_ID = "wemos_alarm_channel";

    private static final int MONITOR_NOTIFICATION_ID = 1001;
    private static final int ALARM_NOTIFICATION_ID = 1002;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private Thread workerThread;
    private String targetIp = "[192.168.4.1](http://192.168.4.1)";
    private int intervalMs = 1000;

    private PowerManager.WakeLock wakeLock;
    private boolean lastAlarmState = false;
    private int lastSensorEvent = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
        acquireWakeLock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null) {
            targetIp = intent.getStringExtra("ip") != null
                    ? intent.getStringExtra("ip")
                    : "[192.168.4.1](http://192.168.4.1)";

            intervalMs = intent.getIntExtra("intervalMs", 1000);
            if (intervalMs < 1000) intervalMs = 1000;
            if (intervalMs > 10000) intervalMs = 10000;
        }

        startForeground(
                MONITOR_NOTIFICATION_ID,
                buildMonitorNotification("Monitoring Wemos aktif")
        );

        startWorker();

        return START_STICKY;
    }

    private void startWorker() {
        if (running.get()) return;

        running.set(true);

        workerThread = new Thread(() -> {
            while (running.get()) {
                try {
                    pollWemos();
                    Thread.sleep(intervalMs);
                } catch (Exception ignored) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored2) {
                    }
                }
            }
        });

        workerThread.setName("WemosAlarmPollingThread");
        workerThread.start();
    }

    private void pollWemos() {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(targetIp + "/status");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(4000);
            connection.setUseCaches(false);

            int code = connection.getResponseCode();
            if (code != 200) return;

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream())
            );

            StringBuilder builder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            reader.close();

            JSONObject json = new JSONObject(builder.toString());

            boolean d4Alarm = json.optBoolean("4", false);
            boolean alarm = false;

            Object alarmObj = json.opt("alarm");
            if (alarmObj instanceof Boolean) {
                alarm = (Boolean) alarmObj;
            } else if (alarmObj instanceof Number) {
                alarm = ((Number) alarmObj).intValue() == 1;
            } else if (alarmObj instanceof String) {
                alarm = "1".equals(alarmObj) || "true".equalsIgnoreCase((String) alarmObj);
            }

            int sensorEvent = json.optInt("sensorEvent", 0);
            boolean sensorEventChanged = false;

            if (lastSensorEvent < 0) {
                lastSensorEvent = sensorEvent;
            } else if (sensorEvent > lastSensorEvent) {
                lastSensorEvent = sensorEvent;
                sensorEventChanged = true;
            }

            boolean shouldAlarm = d4Alarm || alarm || sensorEventChanged;

            if (shouldAlarm && !lastAlarmState) {
                lastAlarmState = true;
                triggerAlarmNotification();
            }

            if (!shouldAlarm && lastAlarmState) {
                lastAlarmState = false;
            }

        } catch (Exception ignored) {
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void triggerAlarmNotification() {
        acquireWakeLock();

        Notification notification = buildAlarmNotification();

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager != null) {
            manager.notify(ALARM_NOTIFICATION_ID, notification);
        }

        vibrateAlarm();
    }

    private Notification buildMonitorNotification(String text) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                10,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, MONITOR_CHANNEL_ID)
                .setSmallIcon(getApplicationInfo().icon)
                .setContentTitle("Wemos Monitoring")
                .setContentText(text)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .build();
    }

    private Notification buildAlarmNotification() {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());

        PendingIntent fullScreenIntent = PendingIntent.getActivity(
                this,
                20,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmSound == null) {
            alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }

        return new NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
                .setSmallIcon(getApplicationInfo().icon)
                .setContentTitle("🚨 Wemos Alarm!")
                .setContentText("Sensor atau D4 trigger!")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Alarm Wemos aktif. Buka aplikasi untuk reset atau snooze."))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSound(alarmSound)
                .setVibrate(new long[]{0, 400, 200, 400, 200, 800})
                .setAutoCancel(false)
                .setOngoing(true)
                .setFullScreenIntent(fullScreenIntent, true)
                .setContentIntent(fullScreenIntent)
                .build();
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager == null) return;

        NotificationChannel monitorChannel = new NotificationChannel(
                MONITOR_CHANNEL_ID,
                "Wemos Monitoring",
                NotificationManager.IMPORTANCE_LOW
        );
        monitorChannel.setDescription("Foreground service untuk monitoring Wemos");

        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmSound == null) {
            alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        NotificationChannel alarmChannel = new NotificationChannel(
                ALARM_CHANNEL_ID,
                "Wemos Alarm",
                NotificationManager.IMPORTANCE_HIGH
        );

        alarmChannel.setDescription("Alarm prioritas tinggi dari Wemos");
        alarmChannel.enableVibration(true);
        alarmChannel.setVibrationPattern(new long[]{0, 400, 200, 400, 200, 800});
        alarmChannel.setSound(alarmSound, audioAttributes);
        alarmChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        alarmChannel.setBypassDnd(true);

        manager.createNotificationChannel(monitorChannel);
        manager.createNotificationChannel(alarmChannel);
    }

    private void vibrateAlarm() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null) return;

            long[] pattern = new long[]{0, 400, 200, 400, 200, 800};

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                        VibrationEffect.createWaveform(pattern, -1)
                );
            } else {
                vibrator.vibrate(pattern, -1);
            }
        } catch (Exception ignored) {
        }
    }

    private void acquireWakeLock() {
        try {
            PowerManager powerManager =
                    (PowerManager) getSystemService(Context.POWER_SERVICE);

            if (powerManager == null) return;

            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "WemosAlarm::PartialWakeLock"
                );
                wakeLock.setReferenceCounted(false);
            }

            if (!wakeLock.isHeld()) {
                wakeLock.acquire(10 * 60 * 1000L);
            }
        } catch (Exception ignored) {
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        running.set(false);

        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }

        releaseWakeLock();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
