#!/usr/bin/env bash
# usage: companion-version.sh <name> <catalog-key>; TAG is <name>-v<version>[-N] or unset
set -euo pipefail

name="${1:?name required}"
key="${2:?catalog key required}"

cd "$(dirname "${BASH_SOURCE[0]}")/../.."
base="$(sed -nE "s/^$key = \"(.*)\"\$/\\1/p" gradle/libs.versions.toml)"
[[ -n "$base" ]] || { echo "$key not found" >&2; exit 1; }

tag="${TAG:-}"
if [[ -z "$tag" ]]; then
    echo "$base"
    exit 0
fi

version="${tag#"$name"-v}"
if [[ "$version" == "$tag" || ( "$version" != "$base" && ! "$version" =~ ^"$base"-[1-9][0-9]*$ ) ]]; then
    echo "bad tag '$tag': want $name-v$base[-N]" >&2
    exit 1
fi
echo "$version"
