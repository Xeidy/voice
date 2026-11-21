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

public class MainActivity extends Activity {

    private TextView statusText;
    private TextView devicesText;
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
    }

    @Override protected void onResume() {
        super.onResume();
        am.registerAudioDeviceCallback(deviceCallback, handler);
        refresh();
    }

    @Override protected void onPause() {
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

        root.addView(header("Keluarkan suara ke:"));
        modeGroup = new RadioGroup(this);
        int[] modes = {AudioRouter.MODE_AUTO, AudioRouter.MODE_SPEAKER,
                AudioRouter.MODE_HEADPHONE, AudioRouter.MODE_NO_BLUETOOTH};
        for (int m : modes) {
            RadioButton rb = new RadioButton(this);
            rb.setId(100 + m);
            rb.setText(AudioRouter.modeName(m));
            rb.setTextSize(16);
            rb.setPadding(dp(4), dp(8), 0, dp(8));
            modeGroup.addView(rb);
        }
        modeGroup.check(100 + AudioRouter.getMode(this));
        modeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int id) {
                int mode = id - 100;
                AudioRouter.setMode(MainActivity.this, mode);
                boolean ok = AudioRouter.apply(mode);
                if (!ok) {
                    Toast.makeText(MainActivity.this,
                            "Gagal: " + AudioRouter.lastError, Toast.LENGTH_LONG).show();
                }
                if (AudioRouter.isLocked(MainActivity.this)) LockService.start(MainActivity.this);
                refresh();
            }
        });
        root.addView(modeGroup);

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

        root.addView(header("Perangkat output yang terdeteksi HP"));
        devicesText = new TextView(this);
        devicesText.setTextSize(15);
        root.addView(devicesText);

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
        devicesText.setText(AudioRouter.describeOutputs(this));
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
