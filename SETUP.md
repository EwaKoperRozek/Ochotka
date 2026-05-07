# Local setup

This repository does not include private keys or production configuration files.

To run the project locally:

## 1. Create `local.properties`

Copy `local.properties.example` to `local.properties` and provide:

- `sdk.dir`
- `MAPS_API_KEY`

## 2. Add Firebase config

Create:

`app/google-services.json`

You can:

- download it from Firebase Console for the `com.ochotka.app` Android app
- or use `app/google-services.json.example` as a reference for expected structure

## 3. Google Maps restrictions

If the Maps key is restricted, make sure the following are allowed:

- package name: `com.ochotka.app`
- SHA-1 of the local debug/release signing certificate

## 4. Sync the project

After adding local configuration:

1. Sync Gradle
2. Rebuild the project
3. Run the app
