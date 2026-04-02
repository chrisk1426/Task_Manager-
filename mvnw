#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper startup script for Unix/macOS
#
# What this script does:
#   1. Locates a valid Java installation (via JAVA_HOME or PATH).
#   2. Checks whether the Maven Wrapper jar (.mvn/wrapper/maven-wrapper.jar)
#      is present. If not, downloads it from the URL in maven-wrapper.properties
#      using curl or wget.
#   3. Invokes the wrapper jar, which in turn downloads the correct Maven
#      version (specified in maven-wrapper.properties) if not already cached,
#      then delegates to Maven with all arguments passed to this script.
#
# Usage:
#   ./mvnw spring-boot:run     — start the application
#   ./mvnw test                — run all tests
#   ./mvnw compile             — compile source files only
# ----------------------------------------------------------------------------

set -euf

# Silence download progress output unless MVNW_VERBOSE=true
[ "${MVNW_VERBOSE-}" != true ] && exec 3>/dev/null || exec 3>&1

# ---------------------------------------------------------------------------
# Step 1: Locate Java
# Prefer JAVA_HOME if set, otherwise fall back to whatever 'java' is on PATH.
# ---------------------------------------------------------------------------
if [ -n "${JAVA_HOME-}" ]; then
    JAVACMD="${JAVA_HOME}/bin/java"
    if [ ! -x "$JAVACMD" ]; then
        echo "ERROR: JAVA_HOME is set to '${JAVA_HOME}' but no executable found at '${JAVACMD}'." >&2
        exit 1
    fi
elif command -v java >/dev/null 2>&1; then
    JAVACMD="java"
else
    echo "ERROR: JAVA_HOME is not set and 'java' was not found on PATH." >&2
    echo "Please install Java 17 and set JAVA_HOME, or add java to your PATH." >&2
    exit 1
fi

# ---------------------------------------------------------------------------
# Step 2: Resolve the project base directory
# We walk up from the script's location looking for the .mvn directory,
# so this script works correctly even when called from a subdirectory.
# ---------------------------------------------------------------------------
find_maven_basedir() {
    BASEDIR="$(cd "$(dirname "$0")" && pwd -P)"
    WDIR="$BASEDIR"
    while [ "$WDIR" != "/" ]; do
        if [ -d "$WDIR/.mvn" ]; then
            echo "$WDIR"
            return 0
        fi
        WDIR="$(cd "$WDIR/.." && pwd -P)"
    done
    # Fallback: use the directory the script lives in
    echo "$BASEDIR"
}

MAVEN_PROJECTBASEDIR="$(find_maven_basedir)"
MAVEN_WRAPPER_JAR="${MAVEN_PROJECTBASEDIR}/.mvn/wrapper/maven-wrapper.jar"
MAVEN_WRAPPER_PROPERTIES="${MAVEN_PROJECTBASEDIR}/.mvn/wrapper/maven-wrapper.properties"

# ---------------------------------------------------------------------------
# Step 3: Download the Maven Wrapper jar if it is not already present
# The jar is a small bootstrap binary that handles downloading Maven itself.
# We support both curl and wget as download tools.
# ---------------------------------------------------------------------------
if [ ! -f "$MAVEN_WRAPPER_JAR" ]; then
    # Extract the wrapperUrl from maven-wrapper.properties
    WRAPPER_URL=$(grep "^wrapperUrl" "$MAVEN_WRAPPER_PROPERTIES" | cut -d= -f2-)
    echo "Downloading Maven Wrapper jar from: $WRAPPER_URL" >&3

    if command -v curl >/dev/null 2>&1; then
        curl -fsSL --create-dirs \
             -o "$MAVEN_WRAPPER_JAR" \
             "$WRAPPER_URL" >&3 2>&1
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "$MAVEN_WRAPPER_JAR" "$WRAPPER_URL" >&3 2>&1
    else
        echo "ERROR: Neither 'curl' nor 'wget' is available." >&2
        echo "Please install one of these tools to download the Maven Wrapper." >&2
        exit 1
    fi
fi

# ---------------------------------------------------------------------------
# Step 4: Execute Maven via the wrapper jar
# The wrapper jar reads maven-wrapper.properties, downloads the specified
# Maven distribution if not cached, and then runs Maven with all arguments.
# ---------------------------------------------------------------------------
exec "$JAVACMD" \
     ${MAVEN_OPTS:-} \
     -classpath "$MAVEN_WRAPPER_JAR" \
     "-Dmaven.multiModuleProjectDirectory=${MAVEN_PROJECTBASEDIR}" \
     org.apache.maven.wrapper.MavenWrapperMain \
     "$@"
