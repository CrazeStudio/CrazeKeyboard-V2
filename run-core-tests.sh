#!/bin/sh
# Compiles the pure-Java dictionary core and runs its checks. Needs only a JDK (javac + java).
set -e
cd "$(dirname "$0")"
rm -rf .test-out && mkdir .test-out
if command -v javac >/dev/null 2>&1; then JAVAC=javac; else JAVAC="java -m jdk.compiler/com.sun.tools.javac.Main"; fi
$JAVAC -source 8 -target 8 -Xlint:-options -d .test-out \
  $(find app/src/main/java/studio/craze/keyboard/core -name '*.java') \
  $(find core-tests -name '*.java')
java -cp .test-out studio.craze.keyboard.core.CoreTests
