$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$output = Join-Path $root 'OuterView-Codex-Quota-Wallpaper.mrc'
$sources = @('manifest.xml', 'description.xml', 'metadata.mrm')

[xml]$manifest = Get-Content -LiteralPath (Join-Path $root 'manifest.xml') -Raw -Encoding utf8
if ($manifest.DocumentElement.Name -ne 'Widget') { throw "unsupported wallpaper root: $($manifest.DocumentElement.Name)" }
if ($manifest.DocumentElement.version -ne '1') { throw 'rear wallpaper must use Widget version=1' }
if ($manifest.DocumentElement.type -ne 'awesome') { throw 'rear wallpaper must use the awesome MAML runtime' }

[xml]$description = Get-Content -LiteralPath (Join-Path $root 'description.xml') -Raw -Encoding utf8
if ($description.DocumentElement.Name -ne 'theme') { throw 'description.xml must use a theme root' }
Get-Content -LiteralPath (Join-Path $root 'metadata.mrm') -Raw -Encoding utf8 | ConvertFrom-Json | Out-Null

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (Test-Path -LiteralPath $output) { Remove-Item -LiteralPath $output -Force }

$stream = [System.IO.File]::Open($output, [System.IO.FileMode]::CreateNew)
try {
    $archive = [System.IO.Compression.ZipArchive]::new(
        $stream,
        [System.IO.Compression.ZipArchiveMode]::Create,
        $false
    )
    try {
        foreach ($name in $sources) {
            $entry = $archive.CreateEntry($name, [System.IO.Compression.CompressionLevel]::Optimal)
            $entry.LastWriteTime = [DateTimeOffset]::new(1980, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
            $input = [System.IO.File]::OpenRead((Join-Path $root $name))
            $entryStream = $entry.Open()
            try { $input.CopyTo($entryStream) } finally { $entryStream.Dispose(); $input.Dispose() }
        }
    } finally {
        $archive.Dispose()
    }
} finally {
    $stream.Dispose()
}

$archive = [System.IO.Compression.ZipFile]::OpenRead($output)
try {
    $names = @($archive.Entries | ForEach-Object FullName)
    if (($names -join ',') -ne ($sources -join ',')) { throw "unexpected package entries: $($names -join ',')" }
    $manifestEntry = $archive.GetEntry('manifest.xml')
    if ($manifestEntry.Length -gt 2MB) { throw 'manifest.xml exceeds OuterView descriptor limit' }
    $expanded = ($archive.Entries | Measure-Object -Property Length -Sum).Sum
    if ($expanded -gt 128MB) { throw 'expanded package exceeds OuterView limit' }
} finally {
    $archive.Dispose()
}

$size = (Get-Item -LiteralPath $output).Length
Write-Output "Built $(Split-Path -Leaf $output) ($size bytes)"
