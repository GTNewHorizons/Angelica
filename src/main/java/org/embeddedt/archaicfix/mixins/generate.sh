#!/bin/bash
perform_generation() {
pushd $1 &>/dev/null
side=$(echo $1 | awk '{ print toupper($0) }')
find * -name \*.java | while read -r line; do
pkgname=$(echo $line | sed 's;/;.;g' | sed 's/.java$//g')
enumname=$(echo $pkgname | sed 's/\./_/g')
echo "    ${1}_${enumname}(Side.$side, always(), \"$pkgname\"),"
done
popd &>/dev/null
}

echo "    // COMMON MIXINS"
perform_generation common
echo "    // CLIENT MIXINS"
perform_generation client
