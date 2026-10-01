# Bible Flutter

This folder is the single source of truth for the Android and web apps. It is
self-contained: application code, complete local CUV/WEB scripture data, fonts,
icons, Bible AI, tests, and web deployment configuration all live here.

## Run

```sh
flutter pub get
flutter run
```

For web development:

```sh
flutter run -d chrome
```

## Verify

```sh
flutter analyze
flutter test
flutter build web --release
flutter build apk --release
```

Android builds require JDK 17 or later. On this Mac, Homebrew's JDK 17 can be
selected for one command without storing a machine-specific path in the project:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
  flutter build apk --release
```

The web bundle is written to `build/web`. Deploy that directory, not the old
Vite `dist` directory from the parent folder.

## Bible data

The complete public-domain Chinese Union Version and World English Bible data
is stored in `assets/bible/cuv` and `assets/bible/web`.

Runtime loading remains lazy:

- Opening a chapter loads only the current book's Chinese and English JSON.
- Changing chapter reuses the already-loaded book.
- Full-Bible search loads books progressively and caches them for that session.
- The app never downloads scripture from an external API at runtime.

To refresh the bundled data intentionally:

```sh
node tool/download_bible.mjs
```

Verify the checked-in data without using the network:

```sh
node tool/verify_bible.mjs
```

The downloader validates 66 books and 1,189 chapters before completing. It is a
maintenance tool only; users do not need a network connection to read scripture.

## Bible AI

Bible AI uses OpenRouter OAuth. On first use, sign in from the chat composer or
`AI settings`. The default model route is `openrouter/free` and can be changed in
settings. OAuth credentials and conversation history are stored on the user's
device/browser and are not committed to this project.

For production web OAuth, serve the app from its final HTTPS origin. The OAuth
callback returns to the same origin with `?oauth=openrouter`.

## Deployment

Build from this folder:

```sh
flutter build web --release
```

`vercel.json` contains the SPA rewrite needed for Flutter routes. Configure the
hosting output directory as `build/web` and do not point it at the parent
project's `dist` folder.
