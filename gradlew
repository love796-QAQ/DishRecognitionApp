#!/usr/bin/env sh

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

DIR="$(cd "$(dirname "$0")" && pwd)"
GRADLE_WRAPPER_DIR="$DIR/gradle/wrapper"

PROPS_FILE="$GRADLE_WRAPPER_DIR/gradle-wrapper.properties"
DISTRIBUTION_URL=$(grep distributionUrl "$PROPS_FILE" | sed 's/.*=//')

GRADLE_ZIP="$HOME/.gradle/wrapper/dists/${DISTRIBUTION_URL##*/}"

if [ ! -f "$GRADLE_ZIP" ]; then
  echo "Downloading Gradle from $DISTRIBUTION_URL"
  mkdir -p "$(dirname "$GRADLE_ZIP")"
  curl -L -o "$GRADLE_ZIP" "$DISTRIBUTION_URL"
fi

unzip -q -o "$GRADLE_ZIP" -d "$HOME/.gradle/wrapper/dists/"

GRADLE_DIR=$(find "$HOME/.gradle/wrapper/dists/" -type d -name "gradle-*")

exec "$GRADLE_DIR/bin/gradle" "$@"
