#!/bin/bash

setup_environment() {
    local dir="$1"
    mkdir -p "$dir"
    export WORK_DIR="$dir"
    echo "Environment set up in $dir"
}

run_tests() {
    local test_dir="${1:-tests}"
    echo "Running tests in $test_dir..."
    if [ -d "$test_dir" ]; then
        for f in "$test_dir"/*.sh; do
            bash "$f" && echo "PASS: $f" || echo "FAIL: $f"
        done
    else
        echo "No test directory found: $test_dir"
        return 1
    fi
}

cleanup() {
    rm -rf "$WORK_DIR"
    echo "Cleaned up $WORK_DIR"
}

main() {
    setup_environment "/tmp/work"
    run_tests
    cleanup
}

main "$@"
