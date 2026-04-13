# перейти в папку скрипта

Set-Location $PSScriptRoot

$width = 8
$height = 8

$stepX = [int](255 / ($width - 1))
$stepY = [int](255 / ($height - 1))

$index = 0

for ($y = 0; $y -lt $height; $y++) {
for ($x = 0; $x -lt $width; $x++) {

```
    $g = $x * $stepX
    $b = $y * $stepY
    $r = 0

    if ($g -gt 255) { $g = 255 }
    if ($b -gt 255) { $b = 255 }

    # GBR → RGB (ImageMagick ждёт RGB)
    magick -size 1x1 xc:"rgb($r,$g,$b)" "$index.png"

    Write-Host "Created $index.png (GBR: $g,$b,$r)"

    $index++
}
```

}
