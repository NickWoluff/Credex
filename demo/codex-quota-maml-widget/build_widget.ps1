$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$design = Join-Path $repo 'codex-quota-companion\design'
$output = Join-Path $root 'OuterView-Balance-MAML-Widget.mtz'
$manifestPath = Join-Path $root 'manifest.xml'
$descriptionPath = Join-Path $root 'description.xml'
$metadataPath = Join-Path $root 'maml-widget.json'
$previewPath = Join-Path $design 'widget-preview-4x2.png'
$thumbnailPath = Join-Path $design 'widget-preview-2x2.png'

foreach ($path in @($manifestPath, $descriptionPath, $metadataPath, $previewPath, $thumbnailPath)) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing package input: $path" }
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (Test-Path -LiteralPath $output) { Remove-Item -LiteralPath $output -Force }

function Add-TextEntry([System.IO.Compression.ZipArchive]$Archive, [string]$Name, [string]$Text) {
    $entry = $Archive.CreateEntry($Name, [System.IO.Compression.CompressionLevel]::Optimal)
    $entry.LastWriteTime = [DateTimeOffset]::new(1980, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
    $entryStream = $entry.Open()
    try {
        $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($Text)
        $entryStream.Write($bytes, 0, $bytes.Length)
    } finally { $entryStream.Dispose() }
}

function Add-FileEntry([System.IO.Compression.ZipArchive]$Archive, [string]$Name, [string]$Path) {
    $entry = $Archive.CreateEntry($Name, [System.IO.Compression.CompressionLevel]::Optimal)
    $entry.LastWriteTime = [DateTimeOffset]::new(1980, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
    $input = [System.IO.File]::OpenRead($Path)
    $entryStream = $entry.Open()
    try { $input.CopyTo($entryStream) } finally { $entryStream.Dispose(); $input.Dispose() }
}

$manifest = Get-Content -LiteralPath $manifestPath -Raw
$manifest = $manifest -replace '<Widget(?=\s)', '<Gadget' -replace '</Widget>', '</Gadget>'

$stream = [System.IO.File]::Open($output, [System.IO.FileMode]::CreateNew)
try {
    $archive = [System.IO.Compression.ZipArchive]::new($stream, [System.IO.Compression.ZipArchiveMode]::Create, $false)
    try {
        Add-TextEntry $archive 'description.xml' (Get-Content -LiteralPath $descriptionPath -Raw)
        Add-TextEntry $archive 'maml-widget.json' (Get-Content -LiteralPath $metadataPath -Raw)
        Add-TextEntry $archive 'content/manifest.xml' $manifest
        Add-FileEntry $archive 'content/preview.png' $previewPath
        Add-FileEntry $archive 'content/thumbnail.png' $thumbnailPath
    } finally { $archive.Dispose() }
} finally { $stream.Dispose() }

$archive = [System.IO.Compression.ZipFile]::OpenRead($output)
try {
    $names = @($archive.Entries | ForEach-Object FullName)
    $required = @('description.xml', 'maml-widget.json', 'content/manifest.xml', 'content/preview.png', 'content/thumbnail.png')
    foreach ($name in $required) {
        if ($names -notcontains $name) { throw "missing package entry: $name" }
    }
    if ((Get-Content -LiteralPath $manifestPath -Raw) -match '<Gadget') { throw 'source manifest should stay readable as Widget; package transform did not run as expected' }
    $manifestEntry = $archive.GetEntry('content/manifest.xml')
    $reader = [System.IO.StreamReader]::new($manifestEntry.Open())
    try { $packagedManifest = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if ($packagedManifest -notmatch '<Gadget') { throw 'packaged MAML root is not Gadget' }
} finally { $archive.Dispose() }

Write-Output "Built $(Split-Path -Leaf $output) ($((Get-Item -LiteralPath $output).Length) bytes)"
