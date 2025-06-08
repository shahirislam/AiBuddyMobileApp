# AiBuddy - Your Empathetic AI Companion

AiBuddy is a voice-first, conversational AI companion app for Android. Built with a modern tech stack, it leverages Google's Gemini for natural language understanding and Google Cloud's Text-to-Speech for a lifelike voice. The app features an expressive, animated UI that reacts in real-time to the conversation, creating an engaging and friendly user experience.


## ✨ Core Features

-   **Voice-to-Voice Interaction**: Engage in seamless, natural conversations. The app automatically listens after it finishes speaking, creating a fluid back-and-forth dialogue.
-   **Expressive AI Persona**: Powered by the Gemini 1.5 Flash model with a custom system prompt that makes AiBuddy a warm, empathetic, and curious friend.
-   **High-Quality Text-to-Speech**: Utilizes Google Cloud Text-to-Speech (`en-US-Wavenet-D`) for a clear and natural-sounding voice.
-   **Reactive Animated UI**: Features "RoboEyes" built with Jetpack Compose `Canvas` that animate to reflect the AI's state—widening when speaking, pulsing when listening, and blinking randomly.
-   **Modern Android Architecture**: Built entirely with Kotlin and Jetpack Compose, following MVVM principles for a clean, scalable, and maintainable codebase.
-   **Asynchronous by Design**: Uses Kotlin Coroutines and StateFlow to manage UI state and handle asynchronous operations like API calls and speech processing.

## 🛠️ Tech Stack & Libraries

-   **UI**: Jetpack Compose
-   **Language**: Kotlin
-   **Architecture**: MVVM (ViewModel, Repository)
-   **Asynchronous**: Kotlin Coroutines & StateFlow
-   **Navigation**: Compose Navigation
-   **Generative AI**: Google Gemini API
-   **Text-to-Speech**: Google Cloud Text-to-Speech API
-   **Speech-to-Text**: Android `SpeechRecognizer`

## ⚙️ How It Works

The app's core is a conversational loop orchestrated in the `ConnectedAiScreen`.

1.  **AI Speaks**: The `HomeViewModel` receives a text response from the Gemini API.
2.  **TTS Generation**: The `TextToSpeechManager` sends this text to the Google Cloud TTS API, receives an MP3 audio stream, and plays it using `MediaPlayer`. The RoboEyes animate to show the AI is "speaking".
3.  **Auto-Listen**: A `LaunchedEffect` observes the `isTtsSpeaking` state. The moment the AI finishes speaking, the app automatically activates the Android `SpeechRecognizer`. The RoboEyes animate to show the AI is "listening".
4.  **User Speaks**: The user's speech is captured and converted to text.
5.  **Send to AI**: The transcribed text is sent to the Gemini API via the `AiBuddyRepository`.
6.  The loop repeats.

## 🚀 Setup and Configuration

To build and run this project, you'll need to configure your own API keys for the Google services it relies on.

### Prerequisites

-   Android Studio (latest version recommended)
-   JDK 17 or higher
-   A physical Android device or emulator with microphone access

### 1. Clone the Repository

```bash
git clone https://github.com/shahirislam/AiBuddyMobileApp.git
cd AiBuddy
```

### 2. Configure Gemini API Key

The app uses the Google Gemini API to generate conversational responses.

1.  Get your API key from the [Google AI Studio](https://aistudio.google.com/app/apikey).
2.  In the root of the project, create a file named `local.properties` if it doesn't already exist.
3.  Add your API key to `local.properties`:

    ```properties
    GEMINI_API_KEY="YOUR_GEMINI_API_KEY"
    ```

    The `app/build.gradle.kts` file is configured to read this key and make it available in the `BuildConfig`.

### 3. Configure Google Cloud Text-to-Speech

The app uses Google Cloud TTS for high-quality voice output. This requires a Google Cloud Platform project and a service account.

> **⚠️ CRITICAL SECURITY WARNING**
>
> The project requires a `service_account_key.json` file. **DO NOT** commit this file to any public repository. It contains private credentials that grant access to your Google Cloud account. The `.gitignore` file in this repository is already configured to ignore it, but you should always double-check.

1.  **Enable the API**:
    -   Go to the [Google Cloud Console](https://console.cloud.google.com/).
    -   Create a new project or select an existing one.
    -   Navigate to **APIs & Services > Library** and search for "Cloud Text-to-Speech API".
    -   Click **Enable**.
2.  **Create a Service Account**:
    -   Navigate to **IAM & Admin > Service Accounts**.
    -   Click **Create Service Account**.
    -   Give it a name (e.g., `aibuddy-tts-user`) and a description.
    -   Grant it the role of **Cloud Text-to-Speech Service Agent**.
    -   Click **Done**.
3.  **Generate a JSON Key**:
    -   Find the service account you just created in the list.
    -   Click on it, then go to the **Keys** tab.
    -   Click **Add Key > Create new key**.
    -   Select **JSON** as the key type and click **Create**. A JSON file will be downloaded to your computer.
4.  **Add the Key to the Project**:
    -   Rename the downloaded file to `service_account_key.json`.
    -   Place this file in the `app/src/main/res/raw/` directory of the project.

### 4. Build and Run

Once the keys are configured, you can build and run the app.

1.  Open the project in Android Studio.
2.  Let Gradle sync and download the required dependencies.
3.  Run the app on an emulator or a physical device.
4.  Grant the microphone permission when prompted.

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
