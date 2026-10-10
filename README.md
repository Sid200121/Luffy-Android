# Luffy for Android (floating bubble + home-screen widget)

What you get:
- A floating Luffy bubble that sits above every app. Drag it anywhere. Tap it to open the chat panel.
- A speaker badge on the bubble to mute or unmute with one tap.
- A home-screen widget (Luffy's face, his last message, a mute button). Tap it to open the chat.
- Luffy starts muted, and stays silent on his own while a call or meeting is using the phone's audio.

## Get the app onto your phone (no Android Studio needed)

1. Create a free GitHub account if you do not have one, then create a new empty repository (Add file > Upload files works fine).
2. Upload everything from this folder to the repository, including the hidden `.github` folder. (Easiest way: install GitHub Desktop, or unzip and drag all files in, making sure `.github/workflows/build.yml` is included.)
3. Open the repository's **Actions** tab, choose **Build Luffy APK**, and press **Run workflow**. Wait about 5 minutes.
4. Open the finished run, download the **luffy-apk** file, unzip it, and you get `app-debug.apk`.
5. Send the APK to your phone, open it, and allow "Install unknown apps" when asked.

## Or build it with Android Studio

Open this folder in Android Studio, let it sync, then Build > Build APK(s). Android Studio may offer to update the Gradle plugin. Accept it.

## First run on the phone

1. Open **Luffy**. Work through steps 1 to 4 on the screen:
   - Allow floating over other apps (needed for the bubble)
   - Allow the microphone (needed for voice input)
   - Allow notifications (Android 13+; shows the small "Luffy is on your screen" card)
   - Keep Luffy running (battery), so Android does not stop him
2. Tap **Start Luffy**. The bubble appears. Tap it, open Settings (gear), paste your free Gemini key, and press Test connection.
3. Add the widget: touch and hold an empty spot on the home screen, tap Widgets, find Luffy, drag him out.

## Using him

- Tap the bubble to chat. Tap outside the panel, press Back, or tap the down arrow to tuck him away.
- When he has something to say while closed, a small preview appears next to the bubble. Tap it to open.
- To stop him completely, use "Stop Luffy" in the app or in the notification.
