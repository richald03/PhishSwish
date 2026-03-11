# PhishSwish 🛡️

**PhishSwish** is a powerful Android application designed to protect users from phishing attacks, scam links, and malicious content. By leveraging advanced AI, real-time scanning, and threat intelligence, PhishSwish provides a comprehensive shield for your digital communications.

## 🚀 Key Features

*   **Multi-Mode Scanning**:
    *   **AR/Camera Scan**: Use your camera to scan physical or on-screen text for malicious URLs. Powered by ML Kit OCR and CameraX.
    *   **Inbox Scan**: Integration with your emails to detect suspicious messages and phishing attempts.
    *   **Paste Scan**: Quickly analyze links or text copied from anywhere on your device.
*   **AI-Powered Analysis**: Utilizes **Gemini AI** to perform deep linguistic and contextual analysis of suspicious messages.
*   **Real-Time Threat Intel**: Integrates with the **VirusTotal API** to check URLs against global databases of known threats.
*   **Brute-Force Protection**: Robust login security with automatic lockout mechanisms to prevent unauthorized access.
*   **Secure Authentication**: Seamless login and registration using Firebase Auth, including Google Sign-In integration.
*   **History & Tracking**: Keep track of your past scans and threats detected for future reference.

## 🛠️ Tech Stack

*   **Language**: Java
*   **Architecture**: Android Jetpack (Activity/Fragment, ViewModels, Navigation)
*   **Database**: Firebase Firestore for cloud data and user tracking.
*   **AI/ML**: 
    *   Gemini AI (Generative Analysis)
    *   ML Kit (Text Recognition/OCR)
*   **Networking**: OkHttp
*   **Security**: Firebase Auth, Google Identity Services, Secure SharedPreferences.

## 🛠️ Setup & Implementation

### Prerequisites
- Android Studio Ladybug (or newer)
- JDK 11+
- Firebase Project setup

### Configuration
1.  **Firebase**: Add your `google-services.json` to the `app/` directory.
2.  **API Keys**: Add the following keys to your `local.properties` file:
    ```properties
    GEMINI_API_KEY_1=your_key_here
    VT_API_KEY=your_key_here
    ```
    *(Refer to `app/build.gradle.kts` for full key rotation setup)*

## 📂 Project Structure

- `ui/`: All Activity and Fragment implementations (Login, Register, Scanners, Results).
- `utils/`: Core logic helpers including `LoginAttemptManager`, `UrlExtractor`, and `LanguageManager`.
- `adapters/`: RecyclerView adapters for history and list displays.

## 🔒 Security Measures

PhishSwish implements a strict anti-brute-force system:
- Maximum 3 failed login attempts.
- 5-minute lockout period with real-time countdown.
- Real-time password matching validation on registration.

---

**Developed with ❤️ to keep you safe from phishing.**
