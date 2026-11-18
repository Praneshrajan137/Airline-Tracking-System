#!/bin/sh
set -eu
MAVEN_PROJECTBASEDIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"
if [ ! -f "$WRAPPER_JAR" ]; then
  WRAPPER_URL="$(sed -n 's/^wrapperUrl=//p' "$WRAPPER_PROPERTIES")"
  if [ -z "$WRAPPER_URL" ]; then
    echo "Could not find wrapperUrl in $WRAPPER_PROPERTIES" >&2
    exit 1
  fi
  echo "Downloading Maven Wrapper bootstrap..."
  if command -v curl >/dev/null 2>&1; then
    curl --fail --silent --show-error --location "$WRAPPER_URL" --output "$WRAPPER_JAR"
  elif command -v wget >/dev/null 2>&1; then
    wget --quiet "$WRAPPER_URL" --output-document="$WRAPPER_JAR"
  else
    echo "curl or wget is required to bootstrap Maven Wrapper" >&2
    exit 1
  fi
fi
exec java -Dmaven.multiModuleProjectDirectory="$MAVEN_PROJECTBASEDIR" -classpath "$WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain "$@"
