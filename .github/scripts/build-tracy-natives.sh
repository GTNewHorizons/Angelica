#!/usr/bin/env bash
# Builds the libTracyClient shared library for the tracy-client module.
set -euo pipefail

TRACY_VERSION="${TRACY_VERSION:-v0.14.0}"
TRACY_VERSION_BARE="${TRACY_VERSION#v}"
OUT_DIR="${OUT_DIR:-dist/tracy-natives}"
TRACY_REPO="https://github.com/wolfpld/tracy"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
case "$(uname -s)" in
    Darwin) PLAT_OS="macos" ;;
    Linux) PLAT_OS="linux" ;;
    *) PLAT_OS="windows" ;;
esac
case "$(uname -m)" in
    arm64|aarch64) PLAT_ARCH="arm64" ;;
    *) PLAT_ARCH="x64" ;;
esac
RES_DIR="${RES_DIR:-$REPO_ROOT/tracy-client/src/main/resources/natives/tracy/$TRACY_VERSION_BARE/$PLAT_OS-$PLAT_ARCH}"

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

cmake -S "$REPO_ROOT/tracy-client/src/main/native" -B "$BUILD_DIR" \
    -DTRACY_SRC_DIR="$SRC_DIR" \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=ON \
    -DTRACY_STATIC=OFF \
    -DTRACY_ENABLE=ON \
    -DTRACY_ON_DEMAND=ON \
    -DTRACY_DELAYED_INIT=ON \
    -DTRACY_MANUAL_LIFETIME=ON \
    -DTRACY_NO_SAMPLING=ON \
    -DTRACY_NO_SYSTEM_TRACING=ON \
    -DTRACY_NO_CRASH_HANDLER=ON \
    -DTRACY_LTO=OFF
cmake --build "$BUILD_DIR" --config Release --parallel

mkdir -p "$OUT_DIR"
for NAME in libTracyClient.so libTracyClient.dylib TracyClient.dll; do
    LIB="$(find "$BUILD_DIR" -name "$NAME" | head -1)"
    if [[ -n "$LIB" ]]; then
        cp -L "$LIB" "$OUT_DIR/$NAME"
        cp "$SRC_DIR/LICENSE" "$OUT_DIR/LICENSE"
        mkdir -p "$RES_DIR"
        cp -L "$LIB" "$RES_DIR/$NAME"
        cp "$SRC_DIR/LICENSE" "$RES_DIR/LICENSE"
        echo "built: $OUT_DIR/$NAME ($TRACY_VERSION), staged: $RES_DIR/$NAME"
        exit 0
    fi
done
echo "error: built library not found under $BUILD_DIR" >&2
exit 1
