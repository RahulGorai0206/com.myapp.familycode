# FamilyCode

**FamilyCode** is a private, offline-first Android application designed to securely synchronize and manage SMS OTPs (One-Time Passwords) across family members' devices using Google Sheets as a free, lightweight backend. 

With true End-to-End Encryption (E2EE) and persistent local caching, your private data is fully secured and instantly accessible, even when you're offline.

## ✨ Features

* **Real-time OTP Syncing**: Receive an OTP on one device and instantly access it on all other linked family devices.
* **End-to-End Encryption (E2EE)**: Powered by AES/GCM. Data is encrypted on the device before it is sent to Google Sheets. The backend only acts as a dumb database, storing unreadable Base64 ciphertext. Only your family's devices hold the key to decrypt it.
* **Offline-First Architecture**: Built with a robust Room Database layer. Your OTPs and connected devices are cached locally so the app loads instantly, even in Airplane Mode.
* **Swipe-to-Delete**: Quickly manage clutter. Swiping an OTP away deletes it locally and permanently purges it from the Google Sheet backend. (Features automatic offline rollback if the network request fails).
* **Auto-Expiring OTPs**: A built-in cleanup script automatically purges OTPs older than 5 minutes to maintain hygiene and security.
* **Device Management**: Track linked family devices, see their relative "Last Seen" statuses, and know exactly who is online. Devices are uniquely tracked via hardware identifiers (`ANDROID_ID`) so they remain stable across app reinstalls.
* **Modern UI & UX**: Built natively with Jetpack Compose featuring live countdown timers, a pulsing expiration UI, pull-to-refresh, dynamic empty states, and dynamic Theme toggling (Light/Dark/System).

## 🛠 Tech Stack

* **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3
* **Architecture**: MVVM (Model-View-ViewModel)
* **Dependency Injection**: [Koin](https://insert-koin.io/)
* **Networking**: [Retrofit 2](https://square.github.io/retrofit/) & OkHttp
* **Local Storage**: [Room Database](https://developer.android.com/training/data-storage/room) & SharedPreferences
* **Security**: `javax.crypto` AES/GCM with SHA-256 Key Derivation
* **Backend**: Google Apps Script (REST API) & Google Sheets (Database)

## 🚀 Setup & Deployment

### 1. Google Sheets Backend Setup
1. Create a new Google Sheet.
2. Go to **Extensions > Apps Script**.
3. Copy the contents of the local `Code.gs` file and paste it into the editor.
4. Go to **Project Settings** (gear icon) -> **Script Properties** and add a new property:
   * **Property**: `API_KEY`
   * **Value**: *(Generate a strong, random password. You will need this for the app.)*
5. Click **Deploy > New Deployment**.
   * Select type: **Web app**
   * Execute as: **Me**
   * Who has access: **Anyone**
6. Click Deploy and copy the provided **Web app URL**.

### 2. Android App Setup
1. Open the project in Android Studio.
2. Build and install the APK on your family's Android devices.
3. On first launch, the app will present a setup wizard. Enter the following details:
   * **Device Name**: E.g., "Dad's Phone" or "Living Room Tablet"
   * **Google Sheet URL**: (Optional) The URL of the Google Sheet for reference.
   * **API Key**: The exact API key you configured in Script Properties.
   * **Web App URL**: The deployed Google Apps Script URL.
4. The app will verify the connection and instantly sync!

## 🔐 Security Disclaimer

Because this application handles sensitive One-Time Passwords (OTPs):
* **Never** share your `API_KEY` or Google Apps Script URL with anyone outside your trusted family network.
* Google Sheets permissions must remain restricted. The Apps Script is configured to securely bypass the Google Login requirement using the custom `API_KEY` payload, making E2EE the primary line of defense.
* The `API_KEY` acts as the master decryption key. If you lose it, the data stored in the Google Sheet cannot be recovered.

## 📄 License
This project is for personal and educational use.
