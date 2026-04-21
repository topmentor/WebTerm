#!/bin/bash
# =============================================================
# SSF2026 - Embedded Tomcat 빌드 스크립트 (Linux/Mac)
# =============================================================
#
# 사용법:
#   ./embedded-build.sh          # 빌드만
#   ./embedded-build.sh run      # 빌드 후 실행
#   ./embedded-build.sh clean    # 클린 빌드
#   ./embedded-build.sh dev      # 개발 모드 실행 (빌드 후 소스 디렉토리 기반)
#
# 옵션 (환경변수):
#   SERVER_PORT=9090 ./embedded-build.sh run
#   CONTEXT_PATH=/myapp ./embedded-build.sh run
#   JAVA_OPTS="-Xmx512m" ./embedded-build.sh run
# =============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

SERVER_PORT="${SERVER_PORT:-8080}"
CONTEXT_PATH="${CONTEXT_PATH:-/SSF2026}"
JAVA_OPTS="${JAVA_OPTS:-}"

WAR_FILE="target/SSF2026-embedded.war"

build() {
    echo "============================================"
    echo " Building SSF2026 (Embedded Tomcat)"
    echo "============================================"
    mvn -f pom-embedded.xml clean package -DskipTests
    echo ""
    echo "Build complete: $WAR_FILE"
}

run_jar() {
    if [ ! -f "$WAR_FILE" ]; then
        echo "WAR file not found. Building first..."
        build
    fi
    echo "============================================"
    echo " Starting SSF2026"
    echo " Port: $SERVER_PORT"
    echo " Context: $CONTEXT_PATH"
    echo "============================================"
    java $JAVA_OPTS \
        -Dserver.port="$SERVER_PORT" \
        -Dserver.contextPath="$CONTEXT_PATH" \
        -jar "$WAR_FILE"
}

run_dev() {
    echo "============================================"
    echo " Building for development mode..."
    echo "============================================"
    mvn -f pom-embedded.xml compile -DskipTests

    echo "============================================"
    echo " Starting SSF2026 (Dev Mode)"
    echo " Port: $SERVER_PORT"
    echo " Context: $CONTEXT_PATH"
    echo " Webapp: web/"
    echo "============================================"

    # 클래스패스 구성: 컴파일된 클래스 + 모든 의존성
    CLASSPATH="target/classes"
    for jar in target/dependency/*.jar; do
        [ -f "$jar" ] && CLASSPATH="$CLASSPATH:$jar"
    done

    # 의존성 복사 (최초 1회)
    if [ ! -d "target/dependency" ]; then
        mvn -f pom-embedded.xml dependency:copy-dependencies -DoutputDirectory=target/dependency -DskipTests
    fi

    for jar in target/dependency/*.jar; do
        [ -f "$jar" ] && CLASSPATH="$CLASSPATH:$jar"
    done

    # lib/ 디렉토리의 로컬 JAR 추가
    for jar in lib/*.jar; do
        [ -f "$jar" ] && CLASSPATH="$CLASSPATH:$jar"
    done

    java $JAVA_OPTS \
        -Dserver.port="$SERVER_PORT" \
        -Dserver.contextPath="$CONTEXT_PATH" \
        -Dwebapp.base=web \
        -cp "$CLASSPATH" \
        com.ithows.EmbeddedApplication
}

case "${1:-build}" in
    build)
        build
        ;;
    run)
        run_jar
        ;;
    dev)
        run_dev
        ;;
    clean)
        mvn -f pom-embedded.xml clean
        echo "Clean complete."
        ;;
    *)
        echo "Usage: $0 {build|run|dev|clean}"
        exit 1
        ;;
esac
