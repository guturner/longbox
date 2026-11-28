param(
    [string]$Source = ''
)

# If the caller didn't pass a source, ask interactively.
if (-not $Source) {
    $Source = Read-Host 'Path to 512x512 source PNG (e.g. ~/Downloads/icon.png)'
    $Source = $Source.Trim('"').Trim("'")
}

# Expand ~ to the home directory for convenience.
$Source = $Source -replace '^~', $HOME

Add-Type -AssemblyName System.Drawing
$img = [System.Drawing.Image]::FromFile($Source)

# Derive the repo root from this script's directory (scripts/ lives in the repo root).
$repoRoot = Split-Path -Parent $PSScriptRoot

$sizes = @{
    'mipmap-mdpi'    = 48
    'mipmap-hdpi'    = 72
    'mipmap-xhdpi'   = 96
    'mipmap-xxhdpi'  = 144
    'mipmap-xxxhdpi' = 192
}
foreach ($dir in $sizes.Keys) {
    $sz = $sizes[$dir]
    $outDir = Join-Path $repoRoot "androidApp\src\main\res\$dir"
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
Write-Host 'Done.'
