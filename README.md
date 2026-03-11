# PhishSwish

PhishSwish is an Android application focused on phishing and scam message detection. It combines OCR, AI-assisted text analysis, and URL reputation checks to help users evaluate suspicious content before interacting with links.

## Features

- Camera Scan: Capture on-screen or printed messages and extract text using ML Kit OCR.
- Inbox Scan: Review SMS content for suspicious patterns and risky links.
- Paste Scan: Analyze manually pasted text or URLs.
- AI Analysis: Uses Gemini for contextual phishing assessment.
- URL Reputation Check: Uses VirusTotal API for link safety signals.
- Scan History: Keeps previous scan results for quick review.
- Account Security: Includes login lockout rules to reduce brute-force risk.

## Tech Stack

- Language: Java
- Platform: Android (minSdk 24, targetSdk 36)
- UI: Material Components + AndroidX
- Cloud/Auth: Firebase Authentication and Firestore
- AI/ML: Gemini API, ML Kit Text Recognition
- Networking: OkHttp
- Camera: CameraX

## Getting Started

### Prerequisites

- Android Studio (latest stable recommended)
- JDK 11 or newer
- Android SDK configured
- Firebase project for authentication and Firestore

### Setup

1. Clone the repository.
2. Add your Firebase config file:
   - Place `google-services.json` in `app/`
3. Configure local API keys in `local.properties` (do not commit this file):

```properties
VT_API_KEY=your_virustotal_key
GEMINI_API_KEY_1=your_gemini_key
GEMINI_API_KEY_2=optional_key
GEMINI_API_KEY_3=optional_key
GEMINI_API_KEY_4=optional_key
GEMINI_API_KEY_5=optional_key
GEMINI_API_KEY_6=optional_key
```

4. Build and run:

```bash
./gradlew assembleDebug
```

## Security Notes

- Never commit `local.properties`, `google-services.json`, keystores, or any `.env` file.
- API keys are injected from local configuration into `BuildConfig` at build time.
- Rotate any leaked keys immediately and update your local configuration.

## Project Structure

- `app/src/main/java/com/example/phishswish/ui`: Activities and UI flows.
- `app/src/main/java/com/example/phishswish/ui/adapters`: RecyclerView adapters.
- `app/src/main/java/com/example/phishswish/utils`: Helper and utility classes.
- `app/src/main/res`: Layouts, drawables, and localized strings.

## License

This project is currently unlicensed. Add a license file before public distribution if needed.