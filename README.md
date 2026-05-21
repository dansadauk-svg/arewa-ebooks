# Arewa Scope Ebooks Android App

This is a simple Android WebView app for:

https://www.arewascope.com.ng/ebooks/

## Features

- WebView app for Arewa Scope Ebooks
- 4 seconds splash screen
- Uses your uploaded green book logo
- Internal Arewa Scope links open inside the app
- External links open outside the app
- Supports file upload from website forms
- Supports file downloads
- GitHub Actions workflow included
- Builds Debug APK, Release APK, and Release AAB

## Important

For Google Play Console, upload the generated `.aab` file from GitHub Actions.

## How to use on GitHub

1. Create a new GitHub repository.
2. Upload all files from this project into the repository.
3. Do not upload your private `.jks` file directly to GitHub.
4. Go to your repository settings.
5. Open `Secrets and variables` then `Actions`.
6. Add these repository secrets:

```text
KEYSTORE_BASE64
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

7. Go to the `Actions` tab.
8. Run `Build Arewa Scope Ebooks Android App`.
9. Download the generated artifacts.

## App details

```text
App name: Arewa Scope Ebooks
Package name: com.arewascope.ebooks
Website URL: https://www.arewascope.com.ng/ebooks/
Target SDK: 35
Minimum SDK: 23
Splash screen duration: 4 seconds
```

## Signing

The signing certificate and upload key are provided separately in the signing assets zip. Keep them private.
