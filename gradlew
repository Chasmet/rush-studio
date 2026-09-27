#!/usr/bin/env sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ -f "$WRAPPER_JAR" ]; then
  exec java -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi
GRADLE_VERSION=8.7
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/rush-wrapper"
GRADLE_HOME="$BASE/gradle-$GRADLE_VERSION"
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  ZIP="${TMPDIR:-/tmp}/gradle-$GRADLE_VERSION-bin.zip"
  mkdir -p "$BASE"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -O "$ZIP"
  else
    echo "curl ou wget requis pour initialiser Gradle." >&2
    exit 1
  fi
  unzip -q -o "$ZIP" -d "$BASE"
fi
exec "$GRADLE_HOME/bin/gradle" "$@"
