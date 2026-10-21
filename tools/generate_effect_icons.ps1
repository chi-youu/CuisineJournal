param([string]$MinecraftAssets = "$PSScriptRoot/../.gradle-user/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar")
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem
$output = [System.IO.Path]::GetFullPath("$PSScriptRoot/../src/main/resources/assets/cuisinejournal/textures/mob_effect")
[System.IO.Directory]::CreateDirectory($output) | Out-Null
$archive = [System.IO.Compression.ZipFile]::OpenRead($MinecraftAssets)
try {
    $entry = $archive.GetEntry('assets/minecraft/textures/block/ice.png')
    $stream = $entry.Open()
    try {
        $source = [System.Drawing.Bitmap]::new($stream)
        $bitmap = [System.Drawing.Bitmap]::new(18,18)
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $graphics.DrawImage($source, [System.Drawing.Rectangle]::new(1,1,16,16))
        $graphics.Dispose()
        $bitmap.Save("$output/ice_ice_ice.png", [System.Drawing.Imaging.ImageFormat]::Png)
        $bitmap.Dispose()
        $source.Dispose()
    } finally { $stream.Dispose() }
} finally { $archive.Dispose() }

# A small amber shield with a sprout marks nourishment converted into protection.
$rows = @(
'..................',
'..................',
'...###########....',
'...#+++++++++#....',
'...#+++++++++#....',
'...#++g+++g++#....',
'...#+++g+g+++#....',
'...#++++g++++#....',
'...#++++g++++#....',
'...#++++g++++#....',
'....#+++g+++#.....',
'....#+++++++#.....',
'.....#+++++#......',
'......#+++#.......',
'.......#+#........',
'........#.........',
'..................',
'..................'
)
$bitmap = [System.Drawing.Bitmap]::new(18,18)
$colors = @{
    '#' = [System.Drawing.Color]::FromArgb(255,117,70,30)
    '+' = [System.Drawing.Color]::FromArgb(255,238,193,87)
    'g' = [System.Drawing.Color]::FromArgb(255,61,111,68)
}
for ($y=0; $y -lt 18; $y++) {
    for ($x=0; $x -lt 18; $x++) {
        $symbol = [string]$rows[$y][$x]
        if ($colors.ContainsKey($symbol)) { $bitmap.SetPixel($x,$y,$colors[$symbol]) }
    }
}
$bitmap.Save("$output/mature.png", [System.Drawing.Imaging.ImageFormat]::Png)
$bitmap.Dispose()
