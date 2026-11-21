package com.pengatur.suara;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Menyalakan lagi kunci output setelah HP dinyalakan ulang. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        if (AudioRouter.isLocked(c)) {
            AudioRouter.apply(AudioRouter.getMode(c));
            try { LockService.start(c); } catch (Exception ignored) { }
        }
    }
}
