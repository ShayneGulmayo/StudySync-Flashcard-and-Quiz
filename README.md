# StudySync — Flashcards & Practice Quizzes

A modern, interactive learning platform that combines **spaced‑repetition flashcards**, **practice quizzes**, **real‑time collaboration**, and **AI-assisted set generation**.

## Key Features

- **AI‑powered study material**: Generate flashcards and quiz questions from raw text (and supported uploads) using the **Google Gemini API**.
- **Dynamic practice quizzes**: Multiple question types with progress tracking.
- **Real‑time chat rooms**: Study together in dedicated rooms—share resources, ask questions, and collaborate live.
- **Smart flashcards**: Organized decks with a clean, intuitive UI for efficient memorization.
- **Live quiz in chat rooms**: Review sets with peers and gamify studying.

## Tech Stack

- **Platform / IDE**: Android (Android Studio)
- **Database / Backend**: Firebase
- **AI Integration**: Google Gemini API

## Getting Started (Local Development)

> If you run into a missing step below (e.g., an absent `google-services.json`), it’s usually because secrets/config files are intentionally not committed.

1. **Clone the repository**
   ```bash
   git clone https://github.com/ShayneGulmayo/StudySync-Flashcard-and-Quiz.git
   ```
2. **Open in Android Studio**
   - `File` → `Open…` → select the project folder.
3. **Set up Firebase**
   - Create or select a Firebase project.
   - Add an Android app in Firebase.
   - Download `google-services.json` and place it in the app module directory (commonly `app/google-services.json`).
   - Ensure the Firebase dependencies and Google Services plugin are enabled in Gradle (project structure may already include this).
4. **Configure Gemini API**
   - Obtain a Gemini API key.
   - Add it using the project’s existing configuration approach (recommended: **do not** hardcode keys in source).
5. **Build & run**
   - Select a device/emulator and press **Run**.


