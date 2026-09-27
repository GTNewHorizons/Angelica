#!/usr/bin/env bash
# Builds the libTracyClient and AngelicaTracyCapture shared libraries for the tracy-client module.
set -euo pipefail

TRACY_VERSION="${TRACY_VERSION:-v0.14.1}"
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

export MACOSX_DEPLOYMENT_TARGET="${MACOSX_DEPLOYMENT_TARGET:-11.0}"

CLIENT_BUILD_DIR="$WORK_DIR/build-client"
cmake -S "$REPO_ROOT/tracy-client/src/main/native" -B "$CLIENT_BUILD_DIR" \
    -DTRACY_SRC_DIR="$SRC_DIR" \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=ON \
    -DTRACY_STATIC=OFF \
    -DTRACY_ENABLE=ON \
    -DTRACY_ON_DEMAND=ON \
    -DTRACY_MANUAL_LIFETIME=ON \
    -DTRACY_NO_SAMPLING=ON \
    -DTRACY_NO_SYSTEM_TRACING=ON \
    -DTRACY_NO_CRASH_HANDLER=ON \
    -DTRACY_LTO=OFF
cmake --build "$CLIENT_BUILD_DIR" --config Release --parallel

CPM_CACHE_DIR="$WORK_DIR/cpm"
CAPTURE_BUILD_DIR="$WORK_DIR/build-capture"
cmake -S "$REPO_ROOT/tracy-client/src/main/native-capture" -B "$CAPTURE_BUILD_DIR" \
    -DTRACY_SRC_DIR="$SRC_DIR" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCPM_SOURCE_CACHE="$CPM_CACHE_DIR"
cmake --build "$CAPTURE_BUILD_DIR" --config Release --parallel

find_one() {
    find "$1" -name "$2" 2>/dev/null | head -1
}

CLIENT_LIB_NAME=""
CLIENT_LIB=""
for NAME in libTracyClient.so libTracyClient.dylib TracyClient.dll; do
    LIB="$(find_one "$CLIENT_BUILD_DIR" "$NAME")"
    if [[ -n "$LIB" ]]; then
        CLIENT_LIB="$LIB"
        CLIENT_LIB_NAME="$NAME"
        break
    fi
done
if [[ -z "$CLIENT_LIB" ]]; then
    echo "error: libTracyClient not found under $CLIENT_BUILD_DIR" >&2
    exit 1
fi

CAPTURE_LIB_NAME=""
CAPTURE_LIB=""
for NAME in libAngelicaTracyCapture.so libAngelicaTracyCapture.dylib AngelicaTracyCapture.dll; do
    LIB="$(find_one "$CAPTURE_BUILD_DIR" "$NAME")"
    if [[ -n "$LIB" ]]; then
        CAPTURE_LIB="$LIB"
        CAPTURE_LIB_NAME="$NAME"
        break
    fi
done
if [[ -z "$CAPTURE_LIB" ]]; then
    echo "error: AngelicaTracyCapture not found under $CAPTURE_BUILD_DIR" >&2
    exit 1
fi

STRIPPED_CAPTURE_LIB="$WORK_DIR/$CAPTURE_LIB_NAME"
cp -L "$CAPTURE_LIB" "$STRIPPED_CAPTURE_LIB"
case "$PLAT_OS" in
    macos) strip -x "$STRIPPED_CAPTURE_LIB" ;;
    linux) strip --strip-unneeded "$STRIPPED_CAPTURE_LIB" ;;
    *) ;;
esac

LICENSE_ZSTD="$(find_one "$CPM_CACHE_DIR/zstd" LICENSE)"
LICENSE_CAPSTONE="$(find_one "$CPM_CACHE_DIR/capstone" LICENSE.TXT)"
LICENSE_PPQSORT="$(find_one "$CPM_CACHE_DIR/ppqsort" LICENSE)"

LICENSE_NAMES=(LICENSE LICENSE-zstd LICENSE-capstone LICENSE-ppqsort)
LICENSE_SRCS=("$SRC_DIR/LICENSE" "$LICENSE_ZSTD" "$LICENSE_CAPSTONE" "$LICENSE_PPQSORT")

for i in "${!LICENSE_NAMES[@]}"; do
    if [[ -z "${LICENSE_SRCS[$i]}" || ! -f "${LICENSE_SRCS[$i]}" ]]; then
        echo "error: ${LICENSE_NAMES[$i]} source not found under $CPM_CACHE_DIR" >&2
        exit 1
    fi
done

mkdir -p "$OUT_DIR" "$RES_DIR"
for DEST in "$OUT_DIR" "$RES_DIR"; do
    cp -L "$CLIENT_LIB" "$DEST/$CLIENT_LIB_NAME"
    cp -L "$STRIPPED_CAPTURE_LIB" "$DEST/$CAPTURE_LIB_NAME"
    for i in "${!LICENSE_NAMES[@]}"; do
        cp "${LICENSE_SRCS[$i]}" "$DEST/${LICENSE_NAMES[$i]}"
    done
done

echo "built: $OUT_DIR/$CLIENT_LIB_NAME, $OUT_DIR/$CAPTURE_LIB_NAME ($TRACY_VERSION)"
echo "staged: $RES_DIR/$CLIENT_LIB_NAME, $RES_DIR/$CAPTURE_LIB_NAME"
