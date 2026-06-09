#!/bin/bash
# =============================================================
# WebTerm - external Tomcat WAR build script
# =============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

WAR_FILE="target/WebTerm.war"

build() {
    echo "============================================"
    echo " Building WebTerm WAR for external Tomcat"
    echo "============================================"
    mvn -f pom.xml clean package -DskipTests
    echo ""
    echo "============================================"
    echo " External Tomcat deployment artifact"
    echo "============================================"
    echo " Upload this file:"
    echo "   $WAR_FILE"
    echo ""
    echo " Tomcat context path:"
    echo "   /WebTerm"
    echo ""
    echo " URL after deployment:"
    echo "   https://your-domain/WebTerm"
    echo "============================================"
}

clean() {
    echo "============================================"
    echo " Cleaning external Tomcat WAR build"
    echo "============================================"
    mvn -f pom.xml clean
    echo "Clean complete."
}

case "${1:-build}" in
    build)
        build
        ;;
    clean)
        clean
        ;;
    --help|-h)
        echo "Usage: $0 [build|clean]"
        echo ""
        echo "Commands:"
        echo "  build    Build target/WebTerm.war for external Tomcat (default)"
        echo "  clean    Clean Maven target directory"
        echo ""
        echo "Note:"
        echo "  Use target/WebTerm.war for Tomcat Manager or webapps upload."
        echo "  Do not upload target/WebTerm-embedded.war to external Tomcat."
        ;;
    *)
        echo "Unknown option: $1"
        echo "Usage: $0 [build|clean]"
        exit 1
        ;;
esac
