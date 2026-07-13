#!/usr/bin/env bash
# Builds the libTracyClient shared library for the tracy-client module.
set -euo pipefail

TRACY_VERSION="${TRACY_VERSION:-v0.13.1}"
OUT_DIR="${OUT_DIR:-dist/tracy-natives}"
TRACY_REPO="https://github.com/wolfpld/tracy"

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

if [[ -n "${TRACY_SRC:-}" ]]; then
    SRC_DIR="$WORK_DIR/tracy"
    git clone --shared --no-checkout "$TRACY_SRC" "$SRC_DIR"
    git -C "$SRC_DIR" checkout --detach "$TRACY_VERSION"
else
    SRC_DIR="$WORK_DIR/tracy"
    git clone --depth 1 --branch "$TRACY_VERSION" "$TRACY_REPO" "$SRC_DIR"
fi

BUILD_DIR="$WORK_DIR/build"
export MACOSX_DEPLOYMENT_TARGET="${MACOSX_DEPLOYMENT_TARGET:-11.0}"

cmake -S "$SRC_DIR" -B "$BUILD_DIR" \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=ON \
    -DTRACY_STATIC=OFF \
    -DTRACY_ENABLE=ON \
    -DTRACY_ON_DEMAND=ON \
    -DTRACY_DELAYED_INIT=ON \
    -DTRACY_MANUAL_LIFETIME=ON \
    -DTRACY_NO_CALLSTACK=ON \
    -DTRACY_NO_SAMPLING=ON \
    -DTRACY_NO_SYSTEM_TRACING=ON \
    -DTRACY_LTO=OFF
cmake --build "$BUILD_DIR" --config Release --parallel

mkdir -p "$OUT_DIR"
for NAME in libTracyClient.so libTracyClient.dylib TracyClient.dll; do
    LIB="$(find "$BUILD_DIR" -name "$NAME" | head -1)"
    if [[ -n "$LIB" ]]; then
        cp -L "$LIB" "$OUT_DIR/$NAME"
        cp "$SRC_DIR/LICENSE" "$OUT_DIR/LICENSE"
        echo "built: $OUT_DIR/$NAME ($TRACY_VERSION)"
        exit 0
    fi
done
echo "error: built library not found under $BUILD_DIR" >&2
exit 1
