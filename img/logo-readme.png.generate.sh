#!/bin/bash
magick convert logo-1x.png -scale 200% \( +clone -background black -shadow 60x6+0+0 \) +swap -background none -layers merge logo-readme.png
