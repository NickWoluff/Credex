$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$output = Join-Path $root 'design-preview.png'
$bitmap = [System.Drawing.Bitmap]::new(480, 304)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

function New-ColorBrush([string]$hex) {
    [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($hex))
}

function Fill-Ellipse([float]$x, [float]$y, [float]$w, [float]$h, [string]$color) {
    $brush = New-ColorBrush $color
    try { $graphics.FillEllipse($brush, $x, $y, $w, $h) } finally { $brush.Dispose() }
}

function Fill-Rectangle([float]$x, [float]$y, [float]$w, [float]$h, [string]$color) {
    $brush = New-ColorBrush $color
    try { $graphics.FillRectangle($brush, $x, $y, $w, $h) } finally { $brush.Dispose() }
}

function Draw-Text(
    [string]$value,
    [float]$x,
    [float]$y,
    [float]$width,
    [float]$height,
    [float]$size,
    [string]$color,
    [bool]$bold = $false,
    [bool]$right = $false
) {
    $style = if ($bold) { [System.Drawing.FontStyle]::Bold } else { [System.Drawing.FontStyle]::Regular }
    $font = [System.Drawing.Font]::new('Segoe UI', $size, $style, [System.Drawing.GraphicsUnit]::Pixel)
    $brush = New-ColorBrush $color
    $format = [System.Drawing.StringFormat]::new()
    if ($right) { $format.Alignment = [System.Drawing.StringAlignment]::Far }
    try {
        $graphics.DrawString($value, $font, $brush, [System.Drawing.RectangleF]::new($x, $y, $width, $height), $format)
    } finally {
        $format.Dispose()
        $brush.Dispose()
        $font.Dispose()
    }
}

try {
    $graphics.Clear([System.Drawing.ColorTranslator]::FromHtml('#0d0d0c'))

    foreach ($ring in @(
        @(202, '#121210'),
        @(154, '#0f0f0e'),
        @(109, '#151513'),
        @(70, '#10100f')
    )) {
        $radius = [float]$ring[0]
        Fill-Ellipse (456 - $radius) (152 - $radius) (2 * $radius) (2 * $radius) $ring[1]
    }
    Fill-Ellipse 453 149 6 6 '#292926'

    Fill-Rectangle 183 17 1 269 '#292926'
    Fill-Ellipse 181 27 5 5 '#10a37f'

    Fill-Ellipse 196 25 12 12 '#f4f4ef'
    Fill-Ellipse 199 28 6 6 '#0d0d0c'
    Fill-Ellipse 203.5 26.5 3 3 '#10a37f'
    Draw-Text 'OuterView' 214 20 100 18 14 '#f4f4ef' $true
    Draw-Text 'CODEX USAGE / AMBIENT' 214 40 160 12 8 '#9daca7'

    Draw-Text '22:08' 200 52 250 64 54 '#f4f4ef' $true
    Draw-Text 'JUL 14' 202 116 180 18 12 '#d1d1c7'

    Draw-Text 'WEEKLY' 202 144 120 15 11 '#c6d2cb'
    Draw-Text '68%' 202 132 252 35 27 '#f4f4ef' $true $true
    Fill-Rectangle 202 174 252 5 '#3b4542'
    Fill-Rectangle 202 174 ([Math]::Round(252 * 0.68)) 5 '#55d6a8'
    Draw-Text 'Resets Jul 20, 08:00' 202 184 180 14 9 '#9daca7'

    Draw-Text '5-HOUR' 202 203 120 15 11 '#c6d2cb'
    Draw-Text '31%' 202 191 252 35 27 '#f4f4ef' $true $true
    Fill-Rectangle 202 233 252 5 '#3b4542'
    Fill-Rectangle 202 233 ([Math]::Round(252 * 0.31)) 5 '#ffb454'
    Draw-Text 'Resets 01:42' 202 243 180 14 9 '#9daca7'

    Fill-Ellipse 201.5 269.5 5 5 '#10a37f'
    Draw-Text 'SYNC 22:07' 213 264 100 15 8 '#9daca7'
    Draw-Text 'TOUCH / REFRESH' 330 264 124 15 8 '#9daca7' $false $true

    $bitmap.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $graphics.Dispose()
    $bitmap.Dispose()
}

Write-Output "Rendered $output"
