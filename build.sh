#!/usr/bin/env bash
set -euo pipefail
terminal_root="$(cd "$(dirname "$0")" && pwd)"
cd "$terminal_root"
terminal_java_home="$(python3 tools/bootstrap_java.py 25)"
terminal_java8_home="$(python3 tools/bootstrap_java.py 8)"
terminal_java17_home="$(python3 tools/bootstrap_java.py 17)"
export JAVA_HOME="$terminal_java_home"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$terminal_root/.tools/gradle-cache}"
terminal_mirror=false
if [ "${1:-}" = "--mirror" ]; then
    shift
    terminal_mirror=true
    python3 tools/prepare_minecraft.py
fi
chmod +x gradlew
if [ "$#" -eq 0 ]; then
    set -- build
fi
terminal_gradle="${STRUCTURE_TERMINAL_GRADLE:-$terminal_root/gradlew}"
exec "$terminal_gradle" "$@" --console=plain \
    "-Porg.gradle.java.installations.paths=$terminal_java_home,$terminal_java8_home,$terminal_java17_home" \
    "-PterminalMirror=$terminal_mirror"
