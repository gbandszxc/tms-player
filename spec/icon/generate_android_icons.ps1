param(
    [string]$SourcePath = "spec/icon/icon_raw.png",
    [string]$ResRoot = "app/src/main/res"
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

if (-not (Test-Path -LiteralPath $SourcePath)) {
    throw "Source image not found: $SourcePath"
}

if (-not (Test-Path -LiteralPath $ResRoot)) {
    throw "Android res directory not found: $ResRoot"
}

$densityConfig = @(
    @{ Name = "mdpi";  Legacy = 48;  Foreground = 108 },
    @{ Name = "hdpi";  Legacy = 72;  Foreground = 162 },
    @{ Name = "xhdpi"; Legacy = 96;  Foreground = 216 },
    @{ Name = "xxhdpi"; Legacy = 144; Foreground = 324 },
    @{ Name = "xxxhdpi"; Legacy = 192; Foreground = 432 }
)

$sourceImage = [System.Drawing.Image]::FromFile((Resolve-Path $SourcePath))
try {
    $minSide = [Math]::Min($sourceImage.Width, $sourceImage.Height)
    $offsetX = [int](($sourceImage.Width - $minSide) / 2)
    $offsetY = [int](($sourceImage.Height - $minSide) / 2)

    foreach ($item in $densityConfig) {
        $dir = Join-Path $ResRoot ("mipmap-" + $item.Name)
        New-Item -ItemType Directory -Path $dir -Force | Out-Null

        $legacyTargets = @("ic_launcher.png", "ic_launcher_round.png")
        foreach ($targetName in $legacyTargets) {
            $targetPath = Join-Path $dir $targetName
            $legacySize = [int]$item.Legacy
            $bitmap = New-Object System.Drawing.Bitmap($legacySize, $legacySize)
            try {
                $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
                try {
                    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
                    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                    $graphics.DrawImage(
                        $sourceImage,
                        (New-Object System.Drawing.Rectangle(0, 0, $legacySize, $legacySize)),
                        (New-Object System.Drawing.Rectangle($offsetX, $offsetY, $minSide, $minSide)),
                        [System.Drawing.GraphicsUnit]::Pixel
                    )
                } finally {
                    $graphics.Dispose()
                }
                $bitmap.Save($targetPath, [System.Drawing.Imaging.ImageFormat]::Png)
            } finally {
                $bitmap.Dispose()
            }
        }

        $foregroundPath = Join-Path $dir "ic_launcher_foreground.png"
        $foregroundSize = [int]$item.Foreground
        $foregroundBitmap = New-Object System.Drawing.Bitmap($foregroundSize, $foregroundSize)
        try {
            $foregroundGraphics = [System.Drawing.Graphics]::FromImage($foregroundBitmap)
            try {
                $foregroundGraphics.Clear([System.Drawing.Color]::Transparent)
                $foregroundGraphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
                $foregroundGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $foregroundGraphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                $foregroundGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $foregroundGraphics.DrawImage(
                    $sourceImage,
                    (New-Object System.Drawing.Rectangle(0, 0, $foregroundSize, $foregroundSize)),
                    (New-Object System.Drawing.Rectangle($offsetX, $offsetY, $minSide, $minSide)),
                    [System.Drawing.GraphicsUnit]::Pixel
                )
            } finally {
                $foregroundGraphics.Dispose()
            }
            $foregroundBitmap.Save($foregroundPath, [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $foregroundBitmap.Dispose()
        }
    }
} finally {
    $sourceImage.Dispose()
}

Write-Host "Icon assets generated from $SourcePath into $ResRoot"
