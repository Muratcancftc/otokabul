#!/usr/bin/env bash
# OtoKabul release APK — macOS (Homebrew Android SDK + Java 17)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Homebrew Android command-line tools
if [[ -d /opt/homebrew/share/android-commandlinetools ]]; then
  export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
elif [[ -d "$HOME/Library/Android/sdk" ]]; then
  export ANDROID_HOME="$HOME/Library/Android/sdk"
else
  echo "Android SDK bulunamadı. Kurulum:"
  echo "  brew install --cask android-commandlinetools openjdk@17"
  exit 1
fi

# Java 17 (Gradle uyumluluğu — Java 25 desteklenmez)
if [[ -d /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ]]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
elif [[ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
else
  echo "Java 17 gerekli: brew install openjdk@17"
  exit 1
fi

export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# Eski/yanlış JAVA_HOME (Android Studio JBR) temizle
unset JAVA_TOOL_OPTIONS 2>/dev/null || true

if [[ ! -d "$ANDROID_HOME/platforms" ]]; then
  echo "SDK paketleri kuruluyor..."
  yes | sdkmanager --licenses >/dev/null
  sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
fi

flutter config --android-sdk "$ANDROID_HOME"
flutter pub get
flutter build apk --release

echo ""
echo "APK: $ROOT/build/app/outputs/flutter-apk/app-release.apk"
