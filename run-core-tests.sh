#!/bin/sh
set -eu
cd "$(dirname "$0")"
rm -rf .test-out
mkdir .test-out
javac -source 8 -target 8 -Xlint:-options -d .test-out \
  $(find app/src/main/java/studio/craze/keyboard/core -name '*.java') \
  $(find core-tests -name '*.java')
java -cp .test-out studio.craze.keyboard.core.CoreTests
