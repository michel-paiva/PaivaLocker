# PaivaLocker

A secure Android app locker that protects your apps with biometric authentication.

## Features

- 🔒 Lock any app with biometric authentication (fingerprint/face)
- 🚀 Fast app detection and protection
- 🌙 Dark mode support
- 🔍 Search and filter locked apps
- ⚡️ Low battery impact
- 🔄 Auto-start on device boot

## Requirements

- Android 10 or higher
- Biometric hardware (fingerprint sensor or face recognition)
- Usage Stats permission
- Overlay permission

## Setup

1. Install the app
2. Grant required permissions:
   - Usage Stats access
   - Overlay permission
   - Biometric authentication
3. Select apps to protect
4. Enable monitoring
5. To close the loop lock the app itself and System Settings

## Building

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle files
4. Build and run

## Permissions

- `QUERY_ALL_PACKAGES`: To list installed apps
- `FOREGROUND_SERVICE`: For app monitoring
- `USE_BIOMETRIC`: For authentication
- `RECEIVE_BOOT_COMPLETED`: For auto-start
- `PACKAGE_USAGE_STATS`: To detect app launches
- `SYSTEM_ALERT_WINDOW`: For authentication overlay
- `POST_NOTIFICATIONS`: For authentication notifications

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details. 