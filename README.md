# Smart Alarm

Aplikasi Android native untuk menjadwalkan rentang fokus harian dan menerima alarm saat rentang berakhir.

## Batasan keamanan Android

Smart Alarm tidak mengunci Android, menonaktifkan tombol sistem, atau mencegah pengguna keluar dari aplikasi. Jadwal yang tiba di latar belakang mengirim notifikasi; pengguna mengetuk notifikasi itu untuk memulai sesi. Saat berada di layar fokus, tombol **Keluar darurat** selalu tersedia. Mode kiosk Android memerlukan perangkat terkelola dan konfigurasi administrator, sehingga aplikasi ini tidak mencoba mengaktifkannya.

## Fitur

- Atur waktu mulai dan selesai harian, misalnya 17:00 sampai 05:00. Rentang melewati tengah malam didukung.
- Pilih format jam 24 jam atau 12 jam (AM/PM), serta bahasa Indonesia atau English.
- Jadwal berulang setiap hari sampai dimatikan. Bila jadwal diaktifkan saat sedang berada di dalam rentang, sesi dimulai setelah notifikasi diketuk.
- Mulai sesi fokus langsung dengan durasi cepat 15, 25, 45, atau 60 menit.
- Terima notifikasi saat waktu mulai tiba, lalu ketuk untuk memulai fokus sampai jam akhir yang ditentukan.
- Terima notifikasi alarm saat sesi selesai. Buka notifikasi, lalu ketik tiga kalimat acak yang ditampilkan untuk menghentikan suara berulang.
- Keluar darurat tersedia selama fokus maupun alarm.

## Menjalankan dan memasang

1. Pasang Android Studio di komputer, lalu buka folder proyek ini.
2. Pada SDK Manager, pasang Android SDK Platform 35 dan Build Tools yang ditawarkan Android Studio. Pastikan JDK 17 tersedia.
3. Tunggu Gradle Sync selesai, lalu pilih **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
4. Setelah build berhasil, pilih **locate** pada notifikasi Android Studio. APK debug berada di `app/build/outputs/apk/debug/app-debug.apk`.
5. Pindahkan APK ke ponsel/tablet melalui USB atau metode transfer file pilihan Anda.
6. Buka APK pada perangkat. Jika diminta, izinkan instalasi dari sumber tersebut di **Settings > Install unknown apps**, lalu konfirmasi **Install**.
7. Buka Smart Alarm dan izinkan notifikasi. Untuk jadwal yang lebih tepat, Android dapat meminta izin **Alarms & reminders** pada halaman info aplikasi.

Untuk memasang langsung melalui USB setelah mengaktifkan USB debugging, hubungkan perangkat dan jalankan `adb install -r app/build/outputs/apk/debug/app-debug.apk` dari komputer yang memiliki Android SDK Platform Tools.

## Catatan operasional

- Android dapat menunda alarm inexact karena optimasi baterai. Akses exact-alarm bersifat pilihan dan dikendalikan pengguna di pengaturan sistem.
- Aplikasi tidak membuka layar secara paksa dari latar belakang; Android membatasi peluncuran aktivitas tersebut.
- Jadwal tersimpan di perangkat, tetapi saat ini tidak dipulihkan otomatis setelah perangkat dimulai ulang.
- Alarm memakai suara notifikasi sistem. Pengguna dapat mengubah volume atau kanal notifikasi dari pengaturan Android.
- Suara berulang selama layar alarm aplikasi terbuka; suara berhenti saat layar aplikasi ditinggalkan. Aplikasi tidak memaksa volume sistem ke maksimum, dan tombol volume perangkat tetap berfungsi.

## Struktur

- `app/`: aplikasi Android Kotlin dan Jetpack Compose.
- `app/src/main/java/com/raka/smartalarm/AlarmReceiver.kt`: penjadwalan alarm dan notifikasi.
- `app/src/main/java/com/raka/smartalarm/MainActivity.kt`: layar jadwal, fokus, dan konfirmasi alarm.