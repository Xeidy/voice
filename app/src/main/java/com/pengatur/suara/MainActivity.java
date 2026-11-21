package com.pengatur.suara;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.content.ClipData;
import android.content.ClipboardManager;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private TextView statusText;
    private TextView warningText;
    private TextView adbStatusText;
    private RadioGroup modeGroup;
    private Switch lockSwitch;
    private AudioManager am;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] a) { refresh(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] r) { refresh(); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        setContentView(buildUi());

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        if (AudioRouter.isLocked(this)) LockService.start(this);
        AudioRouter.writeDaemonMode(this, AudioRouter.forceValueFor(AudioRouter.getMode(this)));
    }

    @Override protected void onResume() {
        super.onResume();
        am.registerAudioDeviceCallback(deviceCallback, handler);
        refresh();
        handler.postDelayed(statusTicker, 2000);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(statusTicker);
        am.unregisterAudioDeviceCallback(deviceCallback);
        super.onPause();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView header(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(18), 0, dp(6));
        return t;
    }

    private Button button(String s, View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(s);
        btn.setAllCaps(false);
        btn.setOnClickListener(l);
        return btn;
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));

        TextView title = new TextView(this);
        title.setText("Pengatur Output Suara");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        statusText = new TextView(this);
        statusText.setTextSize(15);
        statusText.setPadding(0, dp(8), 0, 0);
        root.addView(statusText);

        root.addView(header("Daftar output suara"));
        TextView listHint = new TextView(this);
        listHint.setText("Ketuk salah satu untuk mengeluarkan suara ke sana.");
        listHint.setTextSize(13);
        listHint.setTextColor(Color.GRAY);
        root.addView(listHint);

        modeGroup = new RadioGroup(this);
        root.addView(modeGroup);

        warningText = new TextView(this);
        warningText.setTextSize(14);
        warningText.setTextColor(Color.rgb(230, 120, 0));
        warningText.setPadding(0, dp(6), 0, 0);
        root.addView(warningText);

        lockSwitch = new Switch(this);
        lockSwitch.setText("Kunci output (cegah berganti sendiri)");
        lockSwitch.setTextSize(16);
        lockSwitch.setPadding(0, dp(14), 0, dp(4));
        lockSwitch.setChecked(AudioRouter.isLocked(this));
        lockSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton c, boolean on) {
                AudioRouter.setLocked(MainActivity.this, on);
                if (on) LockService.start(MainActivity.this); else LockService.stop(MainActivity.this);
                refresh();
            }
        });
        root.addView(lockSwitch);

        TextView lockHint = new TextView(this);
        lockHint.setText("Saat aktif, aplikasi berjalan di latar dan menerapkan ulang pilihan Anda "
                + "setiap kali HP mendeteksi perangkat audio baru. Tetap aktif setelah HP restart.");
        lockHint.setTextSize(13);
        lockHint.setTextColor(Color.GRAY);
        root.addView(lockHint);

        root.addView(header("Mode ADB (iADB / Shizuku)"));
        adbStatusText = new TextView(this);
        adbStatusText.setTextSize(15);
        root.addView(adbStatusText);

        TextView adbHelp = new TextView(this);
        adbHelp.setText("HP Anda butuh izin ADB untuk memindahkan suara. Caranya:\n"
                + "1. Ketuk \"Salin perintah\".\n"
                + "2. Buka iADB, sambungkan, lalu tempel perintah di terminal dan jalankan.\n"
                + "3. Kembali ke sini: status akan berubah jadi aktif.\n"
                + "Ulangi setiap kali HP restart.");
        adbHelp.setTextSize(13);
        adbHelp.setTextColor(Color.GRAY);
        adbHelp.setPadding(0, dp(4), 0, dp(4));
        root.addView(adbHelp);

        TextView cmd = new TextView(this);
        cmd.setText(AudioRouter.ADB_COMMAND);
        cmd.setTextIsSelectable(true);
        cmd.setTypeface(Typeface.MONOSPACE);
        cmd.setTextSize(12);
        cmd.setBackgroundColor(Color.argb(40, 128, 128, 128));
        cmd.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(cmd);

        root.addView(button("📋  Salin perintah", new View.OnClickListener() {
            @Override public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("perintah", AudioRouter.ADB_COMMAND));
                Toast.makeText(MainActivity.this, "Perintah disalin", Toast.LENGTH_SHORT).show();
            }
        }));
        root.addView(button("⏹  Hentikan mode ADB", new View.OnClickListener() {
            @Override public void onClick(View v) {
                AudioRouter.stopDaemon(MainActivity.this);
                handler.postDelayed(new Runnable() { @Override public void run() { refresh(); } }, 1500);
            }
        }));

        root.addView(header("Alat bantu"));
        root.addView(button("🔊  Tes suara", new View.OnClickListener() {
            @Override public void onClick(View v) { playTestTone(); }
        }));
        root.addView(button("Buka pilihan output bawaan Android", new View.OnClickListener() {
            @Override public void onClick(View v) { openSystemOutputPicker(); }
        }));
        root.addView(button("Buka pengaturan Bluetooth", new View.OnClickListener() {
            @Override public void onClick(View v) { safeStart(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
        }));

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        return sv;
    }

    private void refresh() {
        int mode = AudioRouter.getMode(this);
        int force = AudioRouter.currentForce();
        String state;
        if (force < 0) {
            state = "⚠ HP ini tidak mengizinkan pengaturan paksa. Gunakan tombol \"Buka pilihan output bawaan Android\".";
        } else if (AudioRouter.isApplied(mode)) {
            state = "✅ Aktif: " + AudioRouter.modeName(mode);
        } else {
            state = "⏳ Belum diterapkan: " + AudioRouter.modeName(mode);
        }
        if (AudioRouter.isLocked(this)) state += "\n🔒 Kunci output menyala";
        statusText.setText(state);
        updateAdbStatus();
        buildOutputList();
    }

    private void updateAdbStatus() {
        if (AudioRouter.isDaemonAlive(this)) {
            adbStatusText.setText("🟢 Mode ADB berjalan");
        } else if (AudioRouter.daemonAgeSeconds(this) >= 0) {
            adbStatusText.setText("🔴 Mode ADB berhenti (HP restart atau dihentikan). Jalankan lagi perintahnya.");
        } else {
            adbStatusText.setText("⚪ Mode ADB belum dijalankan");
        }
    }

    private final Runnable statusTicker = new Runnable() {
        @Override public void run() {
            updateAdbStatus();
            int mode = AudioRouter.getMode(MainActivity.this);
            if (AudioRouter.currentForce() >= 0) {
                String s = (AudioRouter.isApplied(mode) ? "✅ Aktif: " : "⏳ Belum diterapkan: ")
                        + AudioRouter.modeName(mode);
                if (AudioRouter.isLocked(MainActivity.this)) s += "\n🔒 Kunci output menyala";
                statusText.setText(s);
            }
            handler.postDelayed(this, 2000);
        }
    };

    /** Membangun ulang daftar output dari perangkat yang sedang terdeteksi. */
    private void buildOutputList() {
        List<AudioDeviceInfo> devs = AudioRouter.mediaOutputs(this);
        int active = AudioRouter.guessActiveType(devs);
        String choice = AudioRouter.getChoice(this);

        modeGroup.setOnCheckedChangeListener(null);
        modeGroup.removeAllViews();
        final List<String> keys = new ArrayList<String>();
        final List<Integer> modes = new ArrayList<Integer>();

        addRow("🔄  Otomatis (bawaan sistem)", null, "auto", AudioRouter.MODE_AUTO, keys, modes);

        boolean fakeHeadset = false;
        boolean activeMarked = false;
        for (AudioDeviceInfo d : devs) {
            int t = d.getType();
            if (AudioRouter.isWired(t)) fakeHeadset = true;
            String sub = null;
            if (t == active && !activeMarked) { sub = "▶ sedang dipakai"; activeMarked = true; }
            addRow(icon(t) + "  " + AudioRouter.deviceLabel(d), sub, "t" + t,
                    AudioRouter.modeForType(t), keys, modes);
        }

        int checked = keys.indexOf(choice);
        if (checked < 0) checked = 0;   // perangkat pilihan sudah terlepas
        modeGroup.check(1000 + checked);

        modeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int id) {
                int i = id - 1000;
                if (i < 0 || i >= keys.size()) return;
                choose(keys.get(i), modes.get(i));
            }
        });

        warningText.setText(fakeHeadset
                ? "⚠ HP mengira ada headphone/earphone kabel tersambung. Kalau tidak ada yang dicolok, "
                  + "kemungkinan lubang jack kotor/lembap. Pilih \"Speaker HP\" dan nyalakan kunci output."
                : "");
    }

    private void addRow(String title, String sub, String key, int mode,
                        List<String> keys, List<Integer> modes) {
        RadioButton rb = new RadioButton(this);
        rb.setId(1000 + keys.size());
        rb.setText(sub == null ? title : title + "\n" + sub);
        rb.setTextSize(16);
        rb.setPadding(dp(6), dp(10), 0, dp(10));
        modeGroup.addView(rb);
        keys.add(key);
        modes.add(mode);
    }

    private void choose(String key, int mode) {
        AudioRouter.setChoice(this, key);
        AudioRouter.setMode(this, mode);
        final int m = mode;
        AudioRouter.applyAll(this, mode);
        if (AudioRouter.isLocked(this)) LockService.start(this);
        refresh();
        // Mode ADB membaca pilihan tiap 1 detik; cek hasilnya setelah itu.
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                refresh();
                if (!AudioRouter.isApplied(m)) {
                    Toast.makeText(MainActivity.this,
                            "HP menolak. Jalankan Mode ADB lewat iADB (lihat bagian bawah).",
                            Toast.LENGTH_LONG).show();
                }
            }
        }, 1800);
    }

    private static String icon(int t) {
        if (t == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) return "📱";
        if (AudioRouter.isWired(t)) return "🎧";
        if (AudioRouter.isBluetooth(t)) return "🔵";
        if (t == AudioDeviceInfo.TYPE_USB_HEADSET || t == AudioDeviceInfo.TYPE_USB_DEVICE
                || t == AudioDeviceInfo.TYPE_USB_ACCESSORY) return "🔌";
        if (t == AudioDeviceInfo.TYPE_HDMI) return "📺";
        return "🔈";
    }

    private void playTestTone() {
        try {
            final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 90);
            tg.startTone(ToneGenerator.TONE_CDMA_PIP, 1200);
            handler.postDelayed(new Runnable() { @Override public void run() { tg.release(); } }, 1500);
        } catch (Exception e) {
            Toast.makeText(this, "Tidak bisa memutar suara tes", Toast.LENGTH_SHORT).show();
        }
    }

    private void openSystemOutputPicker() {
        // Panel "Putar media ke" (Android 11+). Berbeda tiap merek, jadi dicoba satu per satu.
        Intent panel = new Intent("com.android.settings.panel.action.MEDIA_OUTPUT");
        panel.putExtra("com.android.settings.panel.extra.PACKAGE_NAME", getPackageName());
        if (tryStart(panel)) return;
        if (tryStart(new Intent("android.settings.MEDIA_OUTPUT"))) return;
        if (tryStart(new Intent("com.android.settings.panel.action.VOLUME"))) return;
        safeStart(new Intent(Settings.ACTION_SOUND_SETTINGS));
    }

    private boolean tryStart(Intent i) {
        try { startActivity(i); return true; } catch (ActivityNotFoundException | SecurityException e) { return false; }
    }

    private void safeStart(Intent i) {
        if (!tryStart(i)) Toast.makeText(this, "Tidak tersedia di HP ini", Toast.LENGTH_SHORT).show();
    }
}
