#!/usr/bin/env bash

if ! git diff --name-only HEAD HEAD~1 | grep -qF 'build.gradle'; then
  new_version="$(date +%s)"
  sed --in-place "s!^//version:.*!//version: $new_version!g" build.gradle
  git add build.gradle
  git commit -m "[ci skip] update build script version to $new_version"
  git push
  printf 'Updated buildscript version to %s\n' "$new_version"
else
  printf 'Ignored buildscript version update: no changes detected\n'
fi
