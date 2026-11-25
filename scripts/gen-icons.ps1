Add-Type -AssemblyName System.Drawing
$source = 'C:\Users\guytu\Downloads\longbox-icons\android-chrome-512x512.png'
$img = [System.Drawing.Image]::FromFile($source)
$sizes = @{
    'mipmap-mdpi' = 48
    'mipmap-hdpi' = 72
    'mipmap-xhdpi' = 96
    'mipmap-xxhdpi' = 144
    'mipmap-xxxhdpi' = 192
}
foreach ($dir in $sizes.Keys) {
    $sz = $sizes[$dir]
    $outDir = "C:\workspace\longbox\androidApp\src\main\res\$dir"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $bmp = New-Object System.Drawing.Bitmap($sz, $sz)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.DrawImage($img, 0, 0, $sz, $sz)
    $g.Dispose()
    $bmp.Save("$outDir\ic_launcher.png", [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "Created $outDir ($sz x $sz)"
}
$img.Dispose()
Write-Host "Done."
