$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$output = Join-Path $root 'Codex-Quota-Rear-Card.zip'
$sources = @('manifest.xml', 'reareye-card.json')

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (Test-Path -LiteralPath $output) { Remove-Item -LiteralPath $output -Force }

$stream = [System.IO.File]::Open($output, [System.IO.FileMode]::CreateNew)
try {
    $archive = [System.IO.Compression.ZipArchive]::new($stream, [System.IO.Compression.ZipArchiveMode]::Create, $false)
    try {
        foreach ($name in $sources) {
            $entry = $archive.CreateEntry($name, [System.IO.Compression.CompressionLevel]::Optimal)
            $entry.LastWriteTime = [DateTimeOffset]::new(1980, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
            $input = [System.IO.File]::OpenRead((Join-Path $root $name))
            $entryStream = $entry.Open()
            try { $input.CopyTo($entryStream) } finally { $entryStream.Dispose(); $input.Dispose() }
        }
    } finally { $archive.Dispose() }
} finally { $stream.Dispose() }

$archive = [System.IO.Compression.ZipFile]::OpenRead($output)
try {
    $names = @($archive.Entries | ForEach-Object FullName)
    if (($names -join ',') -ne ($sources -join ',')) { throw "unexpected package entries: $($names -join ',')" }
} finally { $archive.Dispose() }

Write-Output "Built $(Split-Path -Leaf $output) ($((Get-Item -LiteralPath $output).Length) bytes)"
