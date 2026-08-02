# SIM Refresh A71

Experimental Android diagnostic app for Samsung Galaxy A71 (SM-A715F) using an eSIM.me removable eSIM card.

## Purpose

The app tests whether Android permits a normal sideloaded application to:

- request basic phone-state access;
- call the public `TelephonyManager.rebootModem()` API on Android 13+;
- open Samsung/Android phone-information and SIM settings screens;
- display active subscription and modem status.

## Important limitation

Android normally restricts modem restart operations behind the privileged `MODIFY_PHONE_STATE` permission. A regular APK cannot obtain that permission merely by requesting it in the manifest. On many Samsung devices, the modem restart button will therefore report a `SecurityException`. This app records that result clearly rather than hiding it.

## Safety

The app does not:

- delete eSIM profiles;
- factory-reset the phone;
- reset network settings;
- modify APNs;
- change preferred network mode automatically.

## Build

Open the repository in Android Studio and build the debug APK, or use the included GitHub Actions workflow once Actions are enabled for the repository.

Debug APK path after a local build:

`app/build/outputs/apk/debug/app-debug.apk`
