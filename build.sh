#!/bin/bash

PLATFORM=0

usage() {
    echo "Usage: $0 <-windows | -linux | -mac>"
    echo
    echo "  -windows | -linux | -mac    Build for specific platform"
    echo "  -h, --help                  Show help"
    echo
    exit 1
}

# Only match arguments if any are provided, default is Android
if [[ $# -eq 1 ]]; then
        case "$1" in
            -windows) PLATFORM=0 ;;
            -linux) PLATFORM=1 ;;
            -mac) PLATFORM=2 ;;
            -h|--help) usage ;;
            *) error "Unknown option: $1"; usage ;;
        esac
else
    usage
fi

if [[ "$PLATFORM" -eq 0 ]]; then
    echo javac -cp ".;./lib/*" "server/*.java"
    javac -cp ".;./lib/*" server/*.java
	echo javac -cp ".;./lib/*" "client/*.java"
	javac -cp ".;./lib/*" client/*.java
else
    echo javac -cp ".:./lib/*" "server/*.java"
    javac -cp ".:./lib/*" server/*.java
	echo javac -cp ".:./lib/*" "client/*.java"
	javac -cp ".:./lib/*" client/*.java
fi