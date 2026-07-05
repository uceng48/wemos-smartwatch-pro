# Build Output Structure

Pada saat build aplikasi, struktur folder akan terlihat seperti berikut:

```
wemos-smartwatch-pro/
├── build/                    # Temporary build artifacts
│   ├── android/             # Android build intermediates
│   ├── ios/                 # iOS build intermediates
│   └── temp/                # Temporary files
├── platforms/               # Platform-specific source code
│   ├── android/             # Android project files
│   │   ├── res/
│   │   ├── src/
│   │   └── build.gradle
│   └── ios/                 # iOS project files
│       ├── Wemos-Smartwatch-Pro/
│       └── Wemos-Smartwatch-Pro.xcodeproj/
├── plugins/                 # Cordova plugins
│   ├── cordova-plugin-device/
│   ├── cordova-plugin-file/
│   └── cordova-plugin-network-information/
├── dist/                    # Distribution/Release builds
│   ├── app-release.apk     # Android release APK
│   ├── app-debug.apk       # Android debug APK
│   ├── app.ipa             # iOS app
│   └── index.html          # Web version
├── www/                     # Web assets (source)
│   ├── index.html
│   ├── css/
│   ├── js/
│   ├── assets/
│   └── cordova.js
└── config.xml               # Cordova configuration
```

## Folder Descriptions

### build/
Menyimpan file-file intermediate saat proses kompilasi:
- Temporary build files
- Object files (.o, .class)
- Generated build scripts

### platforms/
Berisi kode sumber spesifik untuk setiap platform:
- **android/**: Project Gradle untuk Android
- **ios/**: Xcode project untuk iOS

### plugins/
Directori Cordova plugins yang diinstal:
- Native plugins untuk fitur hardware
- Setiap plugin memiliki struktur sendiri

### dist/
Hasil akhir build yang siap didistribusikan:
- APK untuk Android
- IPA untuk iOS
- Web bundles

### www/
Asset web yang digunakan oleh semua platform:
- HTML, CSS, JavaScript
- Images dan resources
- Cordova bridge files

## Build Process

```bash
# Install dependencies
cordova platform add android
cordova plugin add cordova-plugin-device

# Build untuk development
cordova build android

# Build untuk production/release
cordova build android --release
cordova build ios --release
```
