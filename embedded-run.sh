#!/bin/bash
# =============================================================
# WebTerm - Embedded Tomcat run script (Linux/Mac)
# =============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

SERVER_PORT=8088
CONTEXT_PATH="/WebTerm"
JAVA_OPTS="${JAVA_OPTS:-"-Xms256m -Xmx512m"}"
WAR_FILE="target/WebTerm-embedded.war"
EXTRACT_DIR="target/embedded-webapp"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --port|-p)
            SERVER_PORT="$2"; shift 2 ;;
        --context|-c)
            CONTEXT_PATH="$2"; shift 2 ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  -p, --port PORT        HTTP port (default: 8088)"
            echo "  -c, --context PATH     Context path (default: /WebTerm)"
            echo "  -h, --help             Show help"
            echo ""
            echo "Environment:"
            echo "  JAVA_HOME              JDK path"
            echo "  JAVA_OPTS              JVM options (default: -Xms256m -Xmx512m)"
            exit 0 ;;
        *)
            echo "Unknown option: $1 (--help)"
            exit 1 ;;
    esac
done

if [ -n "$JAVA_HOME" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
    JAR_CMD="$JAVA_HOME/bin/jar"
else
    JAVA_CMD="java"
    JAR_CMD="jar"
fi

if ! command -v "$JAVA_CMD" &> /dev/null; then
    echo "ERROR: java not found. Set JAVA_HOME or add java to PATH."
    exit 1
fi

if [ ! -f "$WAR_FILE" ]; then
    echo "ERROR: WAR file not found: $WAR_FILE"
    echo ""
    echo "Build first:"
    echo "  ./embedded-build.sh"
    exit 1
fi

# Refresh extracted WAR before running so rebuilt static assets are served.
echo "Extracting WAR..."
rm -rf "$EXTRACT_DIR"
mkdir -p "$EXTRACT_DIR"
cd "$EXTRACT_DIR"
"$JAR_CMD" xf "../WebTerm-embedded.war"
cd "$SCRIPT_DIR"
echo "Extract complete."

# Build classpath
CP="$EXTRACT_DIR/WEB-INF/classes"
for jar in "$EXTRACT_DIR"/WEB-INF/lib/*.jar; do
    [ -f "$jar" ] && CP="$CP:$jar"
done

echo "============================================"
echo " WebTerm - Embedded Tomcat"
echo "============================================"
echo " Port    : $SERVER_PORT"
echo " Context : $CONTEXT_PATH"
echo " JVM     : $JAVA_OPTS"
echo "============================================"
echo ""
echo " URL: http://localhost:${SERVER_PORT}${CONTEXT_PATH}"
echo ""
echo " Stop: Ctrl+C"
echo "============================================"

"$JAVA_CMD" $JAVA_OPTS \
    -Dserver.port="$SERVER_PORT" \
    -Dserver.contextPath="$CONTEXT_PATH" \
    -Dwebapp.base="$EXTRACT_DIR" \
    -Dfile.encoding=UTF-8 \
    -cp "$CP" \
    com.ithows.EmbeddedApplication
