package com.pengatur.suara;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Layanan latar yang "mengunci" output: setiap kali HP mendeteksi perangkat audio
 * baru/terlepas (penyebab output suka berganti sendiri), pilihan pengguna diterapkan ulang.
 * Juga dicek ulang berkala setiap 3 detik.
 */
public class LockService extends Service {

    private static final String CHANNEL = "kunci_output";
    private static final int NOTIF_ID = 7;
    private static final long INTERVAL_MS = 3000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager am;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            reapply();
            handler.postDelayed(this, INTERVAL_MS);
        }
    };

    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { reapplySoon(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { reapplySoon(); }
    };

    public static void start(Context c) {
        Intent i = new Intent(c, LockService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, LockService.class));
    }

    @Override public void onCreate() {
        super.onCreate();
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        am.registerAudioDeviceCallback(deviceCallback, handler);
        // Setelah HP restart: begitu iADB dinyalakan lagi, Mode ADB ikut menyala otomatis.
        try { rikka.shizuku.Shizuku.addBinderReceivedListenerSticky(binderListener); } catch (Throwable ignored) { }
    }

    private final rikka.shizuku.Shizuku.OnBinderReceivedListener binderListener =
            new rikka.shizuku.Shizuku.OnBinderReceivedListener() {
        @Override public void onBinderReceived() {
            new Thread(new Runnable() { @Override public void run() {
                if (ShizukuHelper.granted() && !ShizukuHelper.isDaemonRunning()) {
                    AudioRouter.writeDaemonMode(LockService.this,
                            AudioRouter.forceValueFor(AudioRouter.getMode(LockService.this)));
                    ShizukuHelper.startDaemon();
                }
            }}).start();
        }
    };

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
        handler.removeCallbacks(tick);
        handler.post(tick);
        return START_STICKY;
    }

    private void reapplySoon() {
        // Beri jeda sebentar: sistem sering menyetel ulang routing tepat setelah perangkat berubah.
        handler.postDelayed(new Runnable() { @Override public void run() { reapply(); } }, 400);
        handler.postDelayed(new Runnable() { @Override public void run() { reapply(); } }, 1500);
    }

    private void reapply() {
        int mode = AudioRouter.getMode(this);
        if (!AudioRouter.isApplied(mode)) AudioRouter.applyAll(this, mode);
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Kunci output suara",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Output suara dikunci")
                .setContentText(AudioRouter.modeName(AudioRouter.getMode(this)))
                .setContentIntent(open)
                .setOngoing(true)
                .build();
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        am.unregisterAudioDeviceCallback(deviceCallback);
        try { rikka.shizuku.Shizuku.removeBinderReceivedListener(binderListener); } catch (Throwable ignored) { }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
