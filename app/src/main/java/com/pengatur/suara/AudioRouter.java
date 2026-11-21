package com.pengatur.suara;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

import java.lang.reflect.Method;

/**
 * Mengatur ke mana suara media (musik, video, game) dikeluarkan.
 * Memakai AudioSystem.setForceUse(FOR_MEDIA, ...) lewat reflection, yaitu cara yang
 * dipakai aplikasi sejenis (mis. "Lesser AudioSwitch"). Tidak butuh root, tapi
 * sebagian ROM pabrikan bisa mengabaikannya.
 */
public final class AudioRouter {

    // Nilai dari android.media.AudioSystem (kode sumber AOSP)
    private static final int FOR_MEDIA = 1;
    private static final int FORCE_NONE = 0;
    private static final int FORCE_SPEAKER = 1;
    private static final int FORCE_HEADPHONES = 2;
    private static final int FORCE_NO_BT_A2DP = 10;

    public static final int MODE_AUTO = 0;
    public static final int MODE_SPEAKER = 1;
    public static final int MODE_HEADPHONE = 2;
    public static final int MODE_NO_BLUETOOTH = 3;

    private static final String PREFS = "pengaturan";
    private static final String KEY_MODE = "mode";
    private static final String KEY_LOCK = "kunci";

    public static String lastError = "";

    private AudioRouter() {}

    public static int forceValueFor(int mode) {
        switch (mode) {
            case MODE_SPEAKER: return FORCE_SPEAKER;
            case MODE_HEADPHONE: return FORCE_HEADPHONES;
            case MODE_NO_BLUETOOTH: return FORCE_NO_BT_A2DP;
            default: return FORCE_NONE;
        }
    }

    public static final String ADB_COMMAND =
            "pkill -f 'com.pengatur.suara.ShellDaem[o]n'; "
            + "CLASSPATH=$(pm path com.pengatur.suara | cut -d: -f2) setsid app_process /system/bin "
            + "com.pengatur.suara.ShellDaemon > /dev/null 2>&1 < /dev/null &";

    /** Menulis pilihan ke file yang dibaca mode ADB (ShellDaemon). */
    public static void writeDaemonMode(Context c, int value) {
        try {
            java.io.File dir = c.getExternalFilesDir(null);
            if (dir == null) return;
            java.io.FileWriter w = new java.io.FileWriter(new java.io.File(dir, "mode.txt"), false);
            w.write(String.valueOf(value));
            w.close();
        } catch (Exception ignored) { }
    }

    /** Menerapkan pilihan: lewat mode ADB (bila berjalan) dan langsung (bila HP mengizinkan). */
    public static boolean applyAll(Context c, int mode) {
        writeDaemonMode(c, forceValueFor(mode));
        return apply(mode);
    }

    public static void stopDaemon(Context c) {
        writeDaemonMode(c, -99);
    }

    /** Detik sejak mode ADB terakhir aktif, atau -1 bila tidak terbaca. */
    public static long daemonAgeSeconds(Context c) {
        try {
            java.io.File dir = c.getExternalFilesDir(null);
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.FileReader(new java.io.File(dir, "daemon.txt")));
            String s = br.readLine();
            br.close();
            long t = Long.parseLong(s.trim().split(" ")[0]);
            if (t == 0) return -1;
            return (System.currentTimeMillis() - t) / 1000;
        } catch (Exception e) {
            return -1;
        }
    }

    public static boolean isDaemonAlive(Context c) {
        long age = daemonAgeSeconds(c);
        return age >= 0 && age < 5;
    }

    /** Menerapkan mode. Mengembalikan true bila sistem menerima perintah. */
    public static boolean apply(int mode) {
        try {
            Class<?> c = Class.forName("android.media.AudioSystem");
            Method m = c.getMethod("setForceUse", int.class, int.class);
            Object r = m.invoke(null, FOR_MEDIA, forceValueFor(mode));
            boolean ok = (r instanceof Integer) && ((Integer) r) == 0;
            lastError = ok ? "" : "Sistem menolak (kode " + r + ")";
            return ok;
        } catch (Throwable t) {
            lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
            return false;
        }
    }

    /** Nilai paksa yang sedang aktif di sistem, atau -1 bila tidak terbaca. */
    public static int currentForce() {
        try {
            Class<?> c = Class.forName("android.media.AudioSystem");
            Method m = c.getMethod("getForceUse", int.class);
            return (Integer) m.invoke(null, FOR_MEDIA);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static boolean isApplied(int mode) {
        return currentForce() == forceValueFor(mode);
    }

    // ---- Penyimpanan pilihan ----
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    public static int getMode(Context c) { return prefs(c).getInt(KEY_MODE, MODE_AUTO); }
    public static void setMode(Context c, int m) { prefs(c).edit().putInt(KEY_MODE, m).apply(); }
    public static boolean isLocked(Context c) { return prefs(c).getBoolean(KEY_LOCK, false); }
    public static void setLocked(Context c, boolean v) { prefs(c).edit().putBoolean(KEY_LOCK, v).apply(); }

    public static String modeName(int mode) {
        switch (mode) {
            case MODE_SPEAKER: return "Paksa Speaker HP";
            case MODE_HEADPHONE: return "Paksa Headphone/Earphone";
            case MODE_NO_BLUETOOTH: return "Jangan pakai Bluetooth";
            default: return "Otomatis (bawaan sistem)";
        }
    }

    // ---- Pilihan perangkat di daftar output ----
    private static final String KEY_CHOICE = "pilihan";
    public static String getChoice(Context c) { return prefs(c).getString(KEY_CHOICE, "auto"); }
    public static void setChoice(Context c, String v) { prefs(c).edit().putString(KEY_CHOICE, v).apply(); }

    /** Apakah tipe perangkat ini bisa dipakai untuk suara media. */
    public static boolean isMediaOutput(int t) {
        switch (t) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
            case AudioDeviceInfo.TYPE_USB_ACCESSORY:
            case AudioDeviceInfo.TYPE_HDMI:
            case AudioDeviceInfo.TYPE_LINE_ANALOG:
            case AudioDeviceInfo.TYPE_LINE_DIGITAL:
            case AudioDeviceInfo.TYPE_DOCK:
            case AudioDeviceInfo.TYPE_AUX_LINE:
            case 23: case 26: case 27:
                return true;
            default:
                return false;
        }
    }

    public static boolean isBluetooth(int t) {
        return t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == 23 || t == 26 || t == 27;
    }

    public static boolean isWired(int t) {
        return t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || t == AudioDeviceInfo.TYPE_WIRED_HEADSET;
    }

    /** Mode routing yang membuat suara keluar ke tipe perangkat ini. */
    public static int modeForType(int t) {
        if (t == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) return MODE_SPEAKER;
        if (isWired(t)) return MODE_HEADPHONE;
        if (isBluetooth(t)) return MODE_AUTO;      // Bluetooth sudah jadi prioritas utama bawaan
        return MODE_NO_BLUETOOTH;                  // USB/HDMI/line: lewati Bluetooth
    }

    /** Daftar output media yang terdeteksi, tanpa duplikat. Speaker HP selalu ada. */
    public static java.util.List<AudioDeviceInfo> mediaOutputs(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        java.util.List<AudioDeviceInfo> list = new java.util.ArrayList<AudioDeviceInfo>();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (!isMediaOutput(d.getType())) continue;
            String key = d.getType() + ":" + d.getProductName();
            if (seen.add(key)) list.add(d);
        }
        return list;
    }

    /** Perkiraan tipe perangkat yang sedang mengeluarkan suara media. */
    public static int guessActiveType(java.util.List<AudioDeviceInfo> devs) {
        int force = currentForce();
        int bt = -1, usb = -1, wired = -1, other = -1;
        for (AudioDeviceInfo d : devs) {
            int t = d.getType();
            if (isBluetooth(t)) { if (bt < 0) bt = t; }
            else if (t == AudioDeviceInfo.TYPE_USB_HEADSET || t == AudioDeviceInfo.TYPE_USB_DEVICE) usb = t;
            else if (isWired(t)) wired = t;
            else if (t != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) other = t;
        }
        if (force == FORCE_SPEAKER) return AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
        if (force == FORCE_HEADPHONES && wired >= 0) return wired;
        if (force != FORCE_NO_BT_A2DP && bt >= 0) return bt;
        if (usb >= 0) return usb;
        if (wired >= 0) return wired;
        if (other >= 0) return other;
        return AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
    }

    public static String deviceLabel(AudioDeviceInfo d) {
        String name = d.getProductName() == null ? "" : d.getProductName().toString().trim();
        String label = typeName(d.getType());
        if (name.length() > 0 && !name.equals(android.os.Build.MODEL) && isBluetooth(d.getType())
                || (name.length() > 0 && d.getType() == AudioDeviceInfo.TYPE_USB_HEADSET)) {
            label += " — " + name;
        }
        return label;
    }

    // ---- Daftar perangkat output yang dikira tersambung oleh HP ----
    public static String describeOutputs(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo[] devs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        StringBuilder sb = new StringBuilder();
        boolean fakeHeadset = false;
        for (AudioDeviceInfo d : devs) {
            int t = d.getType();
            if (t == AudioDeviceInfo.TYPE_TELEPHONY || t == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    || t == 20 /* TYPE_IP */ || t == 25 /* TYPE_REMOTE_SUBMIX */) continue;
            if (t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || t == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                fakeHeadset = true;
            }
            String name = d.getProductName() == null ? "" : d.getProductName().toString();
            sb.append("• ").append(typeName(t));
            if (name.length() > 0 && !name.equals(android.os.Build.MODEL)) sb.append(" (").append(name).append(")");
            sb.append('\n');
        }
        if (sb.length() == 0) sb.append("(tidak ada)\n");
        if (fakeHeadset) {
            sb.append("\n⚠ HP mengira ada headphone/earphone kabel tersambung. Kalau tidak ada yang "
                    + "dicolok, kemungkinan lubang jack kotor/basah. Pilih \"Paksa Speaker HP\" dan "
                    + "aktifkan kunci output.\n");
        }
        return sb.toString();
    }

    public static String typeName(int t) {
        switch (t) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "Speaker HP";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: return "Headphone kabel";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "Headset kabel (dengan mic)";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "Bluetooth (musik)";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "Bluetooth (telepon)";
            case AudioDeviceInfo.TYPE_USB_DEVICE: return "Perangkat USB";
            case AudioDeviceInfo.TYPE_USB_HEADSET: return "Headset USB";
            case AudioDeviceInfo.TYPE_USB_ACCESSORY: return "Aksesori USB";
            case AudioDeviceInfo.TYPE_HDMI: return "HDMI";
            case AudioDeviceInfo.TYPE_LINE_ANALOG: return "Line out analog";
            case AudioDeviceInfo.TYPE_LINE_DIGITAL: return "Line out digital";
            case AudioDeviceInfo.TYPE_DOCK: return "Dock";
            case AudioDeviceInfo.TYPE_AUX_LINE: return "AUX";
            case 23: return "Alat bantu dengar";
            case 24: return "Speaker HP (mode aman)";
            case 26: return "Headset Bluetooth LE";
            case 27: return "Speaker Bluetooth LE";
            case 30: return "Siaran Bluetooth LE";
            default: return "Perangkat lain (tipe " + t + ")";
        }
    }
}
