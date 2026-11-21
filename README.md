# Pengatur Output Suara (Android)

Aplikasi untuk memaksa suara media (musik, video, game) keluar ke **Speaker HP**,
**Headphone**, atau **bukan Bluetooth**, dan **mengunci** pilihan itu supaya tidak
berganti sendiri.

## Fitur
- Pilih output: Otomatis / Paksa Speaker HP / Paksa Headphone / Jangan pakai Bluetooth
- **Kunci output**: layanan latar yang menerapkan ulang pilihan setiap kali HP
  mendeteksi perangkat audio baru (termasuk setelah HP restart)
- Daftar perangkat yang *dikira* tersambung oleh HP + peringatan bila HP mengira
  ada earphone padahal tidak ada (tanda jack kotor/basah)
- Tombol tes suara, pembuka panel output bawaan Android, dan pengaturan Bluetooth

## Cara membuat APK tanpa komputer (GitHub, gratis)
1. Buat akun di github.com, lalu buat repository baru (boleh *private*).
2. Upload semua isi folder ini (Add file → Upload files). Pastikan folder
   `.github/workflows` ikut terupload.
3. Buka tab **Actions** → tunggu "Build APK" selesai (±3–5 menit, tanda centang hijau).
4. Klik hasil build → bagian **Artifacts** → unduh `PengaturSuara-apk` (zip berisi APK).
5. Ekstrak, pasang APK di HP (izinkan "Instal dari sumber tidak dikenal").

## Cara lain: Android Studio
Buka folder ini di Android Studio → Build → Build APK(s).

## Catatan
- Pemaksaan output memakai API tersembunyi Android (`AudioSystem.setForceUse`),
  cara yang sama dengan aplikasi sejenis. Tidak butuh root, tetapi sebagian ROM
  (mis. beberapa Xiaomi/Samsung versi baru) bisa mengabaikannya. Status di bagian
  atas aplikasi akan memberi tahu apakah berhasil.
- Mode ini mengatur suara **media**. Suara telepon diatur terpisah oleh Android.
- Untuk "Kunci output", matikan optimasi baterai untuk aplikasi ini agar tidak
  dimatikan sistem.
