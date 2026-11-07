# Smart Alarm

Aplikasi Android native untuk menjadwalkan sesi fokus dan menerima alarm saat sesi berakhir.

## Batasan keamanan Android

Smart Alarm tidak mengunci Android, menonaktifkan tombol sistem, atau mencegah pengguna keluar dari aplikasi. Jadwal yang tiba di latar belakang mengirim notifikasi; pengguna mengetuk notifikasi itu untuk memulai sesi. Saat berada di layar fokus, tombol **Keluar darurat** selalu tersedia. Mode kiosk Android memerlukan perangkat terkelola dan konfigurasi administrator, sehingga aplikasi ini tidak mencoba mengaktifkannya.

## Fitur

- Pilih durasi 15, 25, 45, atau 60 menit.
- Jadwalkan satu sesi fokus pada waktu pilihan berikutnya. Jika waktu yang dipilih sudah lewat hari ini, jadwal dibuat untuk esok hari.
- Mulai sesi fokus langsung dari aplikasi.
- Terima notifikasi saat waktu jadwal tiba, lalu ketuk untuk memulai fokus.
- Terima notifikasi alarm saat sesi selesai dan pilih jawaban untuk menutup sesi.
- Keluar darurat tersedia selama fokus maupun alarm.

## Menjalankan

1. Buka folder ini di Android Studio.
2. Pastikan Android SDK Platform 35 dan JDK 17 tersedia.
3. Sinkronkan Gradle, lalu jalankan konfigurasi `app` pada emulator atau perangkat Android.
4. Izinkan notifikasi saat diminta. Agar waktu jadwal lebih akurat, izinkan alarm dan pengingat di pengaturan Android jika perangkat memintanya.

Proyek dapat dibuka dan dijalankan melalui Android Studio setelah Android SDK Platform 35 dan JDK 17 tersedia.

## Catatan operasional

- Android dapat menunda alarm inexact karena optimasi baterai. Akses exact-alarm bersifat pilihan dan dikendalikan pengguna di pengaturan sistem.
- Aplikasi tidak membuka layar secara paksa dari latar belakang; Android membatasi peluncuran aktivitas tersebut.
- Jadwal tersimpan di perangkat, tetapi saat ini tidak dipulihkan otomatis setelah perangkat dimulai ulang.
- Alarm memakai suara notifikasi sistem. Pengguna dapat mengubah volume atau kanal notifikasi dari pengaturan Android.

## Struktur

- `app/`: aplikasi Android Kotlin dan Jetpack Compose.
- `app/src/main/java/com/raka/smartalarm/AlarmReceiver.kt`: penjadwalan alarm dan notifikasi.
- `app/src/main/java/com/raka/smartalarm/MainActivity.kt`: layar jadwal, fokus, dan konfirmasi alarm.