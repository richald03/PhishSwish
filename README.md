# PhishSwish

<p align="center">
  <strong>Scan smarter. Click safer.</strong><br/>
  An Android app that helps users detect phishing and scam content using OCR, AI, and URL reputation checks.
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
  <img alt="Language" src="https://img.shields.io/badge/Language-Java-007396?style=for-the-badge&logo=openjdk&logoColor=white" />
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-24-blue?style=for-the-badge" />
  <img alt="Target SDK" src="https://img.shields.io/badge/targetSdk-36-5C6BC0?style=for-the-badge" />
</p>

## What It Does

- Camera Scan: Extracts text from printed or on-screen messages with ML Kit OCR.
- Inbox Scan: Reviews SMS content for suspicious patterns and risky links.
- Paste Scan: Analyzes pasted text or URLs instantly.
- AI Analysis: Uses Gemini for contextual phishing assessment.
- URL Reputation: Checks links with VirusTotal signals.
- Scan History: Stores previous analysis results for quick review.
- Account Protection: Includes login lockout to reduce brute-force attacks.

## Tech Stack

- Java
- AndroidX + Material Components
- Firebase Authentication + Firestore
- ML Kit Text Recognition
- Gemini API
- OkHttp
- CameraX

## Quick Start

### Prerequisites

- Android Studio (latest stable recommended)
- JDK 11+
- Android SDK
- Firebase project (Auth + Firestore)

### Setup

1. Clone this repository.
2. Place `google-services.json` in `app/`.
3. Add local keys in `local.properties` (never commit this file):

```properties
VT_API_KEY=your_virustotal_key
GEMINI_API_KEY_1=your_gemini_key
GEMINI_API_KEY_2=optional_key
GEMINI_API_KEY_3=optional_key
GEMINI_API_KEY_4=optional_key
GEMINI_API_KEY_5=optional_key
GEMINI_API_KEY_6=optional_key
```

4. Build:

```bash
./gradlew assembleDebug
```

## Security First

- Keep `local.properties`, `google-services.json`, keystores, and `.env` files out of git.
- API keys are loaded from local configuration into `BuildConfig` during build.
- Rotate any key immediately if exposure is suspected.

## Project Structure

- `app/src/main/java/com/example/phishswish/ui` - Activities and UI flow.
- `app/src/main/java/com/example/phishswish/ui/adapters` - RecyclerView adapters.
- `app/src/main/java/com/example/phishswish/utils` - Utility helpers.
- `app/src/main/res` - Layouts, drawables, and string resources.

## License

No license is defined yet.
