package com.pengatur.suara;

import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

/**
 * Jembatan ke iADB / Shizuku: menjalankan Mode ADB (ShellDaemon) tanpa perlu
 * menyalin perintah ke terminal.
 */
public final class ShizukuHelper {

    public static final int REQUEST_CODE = 42;
    private static final String PATTERN = "'com.pengatur.suara.ShellDaem[o]n'";

    private ShizukuHelper() {}

    /** Layanan iADB/Shizuku sedang menyala. */
    public static boolean available() {
        try { return Shizuku.pingBinder(); } catch (Throwable t) { return false; }
    }

    /** Aplikasi ini sudah diizinkan di iADB/Shizuku. */
    public static boolean granted() {
        try {
            return available() && !Shizuku.isPreV11()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void requestPermission() {
        try { Shizuku.requestPermission(REQUEST_CODE); } catch (Throwable ignored) { }
    }

    /** Menjalankan perintah shell dengan hak ADB, mengembalikan keluarannya. */
    public static String run(String cmd) throws Exception {
        Method m = Shizuku.class.getDeclaredMethod("newProcess",
                String[].class, String[].class, String.class);
        m.setAccessible(true);
        Process p = (Process) m.invoke(null, new String[]{"sh", "-c", cmd}, null, null);
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        BufferedReader er = new BufferedReader(new InputStreamReader(p.getErrorStream()));
        while ((line = er.readLine()) != null) sb.append(line).append('\n');
        p.waitFor();
        return sb.toString();
    }

    public static boolean isDaemonRunning() {
        try { return run("pgrep -f " + PATTERN).trim().length() > 0; } catch (Throwable t) { return false; }
    }

    /** Menyalakan (ulang) Mode ADB. Jalankan di thread latar. */
    public static String startDaemon() {
        try {
            run(AudioRouter.ADB_COMMAND);
            Thread.sleep(1200);
            return isDaemonRunning() ? null : "Mode ADB tidak mau berjalan";
        } catch (Throwable t) {
            return t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    public static void stopDaemon() {
        try { run("pkill -f " + PATTERN); } catch (Throwable ignored) { }
    }
}
