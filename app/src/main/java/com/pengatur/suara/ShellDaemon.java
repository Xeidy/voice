package com.pengatur.suara;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Method;

/**
 * Dijalankan lewat ADB shell (iADB / Shizuku / komputer) dengan app_process, sehingga
 * berjalan sebagai user "shell" yang punya izin MODIFY_AUDIO_ROUTING.
 *
 * Membaca pilihan dari file yang ditulis aplikasi, lalu menerapkannya setiap detik
 * (sekaligus berfungsi sebagai "kunci output").
 *
 * Perintah (ditampilkan di aplikasi, tinggal salin):
 *   CLASSPATH=$(pm path com.pengatur.suara | cut -d: -f2) setsid app_process /system/bin \
 *       com.pengatur.suara.ShellDaemon > /dev/null 2>&1 < /dev/null &
 */
public final class ShellDaemon {

    static final String DIR = "/sdcard/Android/data/com.pengatur.suara/files";
    static final int FOR_MEDIA = 1;
    static final int STOP = -99;

    public static void main(String[] args) throws Exception {
        File modeFile = new File(DIR, "mode.txt");
        File beat = new File(DIR, "daemon.txt");
        Class<?> as = Class.forName("android.media.AudioSystem");
        Method set = as.getMethod("setForceUse", int.class, int.class);
        Method get = as.getMethod("getForceUse", int.class);

        System.out.println("Pengatur Suara: mode ADB berjalan.");
        int lastResult = 0;
        while (true) {
            int want = readInt(modeFile, 0);
            if (want == STOP) {
                set.invoke(null, FOR_MEDIA, 0);
                writeText(beat, "0 berhenti");
                System.out.println("Pengatur Suara: mode ADB dihentikan.");
                return;
            }
            int cur = (Integer) get.invoke(null, FOR_MEDIA);
            if (cur != want) {
                Object r = set.invoke(null, FOR_MEDIA, want);
                lastResult = (r instanceof Integer) ? (Integer) r : -1;
            }
            writeText(beat, System.currentTimeMillis() + " " + lastResult);
            Thread.sleep(1000);
        }
    }

    private static int readInt(File f, int def) {
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String s = br.readLine();
            return s == null ? def : Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static void writeText(File f, String s) {
        try (FileWriter w = new FileWriter(f, false)) { w.write(s); } catch (Exception ignored) { }
    }
}
