param(
    [Parameter(Mandatory=$true)][string]$MinecraftJar,
    [Parameter(Mandatory=$true)][string]$AppleSkinJar
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem
$minecraft = [IO.Compression.ZipFile]::OpenRead($MinecraftJar)
$apple = [IO.Compression.ZipFile]::OpenRead($AppleSkinJar)
$mcStream = $minecraft.GetEntry('assets/minecraft/textures/gui/icons.png').Open()
$appleStream = $apple.GetEntry('assets/appleskin/textures/icons.png').Open()
$mc = [Drawing.Bitmap]::new($mcStream)
$source = [Drawing.Bitmap]::new($appleStream)
$half = [Drawing.Bitmap]::new(9,9)
$g = [Drawing.Graphics]::FromImage($half)
$g.DrawImage($mc,[Drawing.Rectangle]::new(0,0,9,9),16,27,9,9,[Drawing.GraphicsUnit]::Pixel)
$g.DrawImage($mc,[Drawing.Rectangle]::new(0,0,9,9),61,27,9,9,[Drawing.GraphicsUnit]::Pixel)
$g.Dispose()
$body = [Drawing.Bitmap]::new($half)
for ($y=0; $y -lt 9; $y++) {
    for ($x=0; $x -lt 9; $x++) {
        $p = $source.GetPixel(27+$x,$y)
        if ($p.A -eq 0) { continue }
        $value = [Math]::Max($p.R,[Math]::Max($p.G,$p.B))
        $hex = if ($value -ge 210) { '#EAC580' } elseif ($value -ge 140) { '#B9823D' } else { '#795125' }
        $color = [Drawing.ColorTranslator]::FromHtml($hex)
        $body.SetPixel($x,$y,[Drawing.Color]::FromArgb($p.A,$color.R,$color.G,$color.B))
    }
}
$atlas = [Drawing.Bitmap]::new(22,11)
for ($y=0; $y -lt 9; $y++) {
    for ($x=0; $x -lt 9; $x++) {
        if ($body.GetPixel($x,$y).A -eq 0) { continue }
        foreach ($delta in @(@(-1,0),@(1,0),@(0,-1),@(0,1),@(0,0))) {
            $atlas.SetPixel(12+$x+$delta[0],1+$y+$delta[1],[Drawing.Color]::Black)
        }
    }
}
$g = [Drawing.Graphics]::FromImage($atlas)
$g.DrawImageUnscaled($half,1,1)
$g.DrawImageUnscaled($body,12,1)
$g.Dispose()
$output = [IO.Path]::GetFullPath("$PSScriptRoot/../src/main/resources/assets/cuisinejournal/textures/font/nutrition_icons.png")
$atlas.Save($output,[Drawing.Imaging.ImageFormat]::Png)
# Report the bitmap font's measured advances; the text renderer adds padding to 12px.
foreach ($offset in @(0,11)) {
    $right = 0
    for ($y=0; $y -lt 11; $y++) { for ($x=0; $x -lt 11; $x++) {
        if ($atlas.GetPixel($offset+$x,$y).A -gt 0) { $right = [Math]::Max($right,$x) }
    }}
    Write-Output "glyph $offset advance=$($right+2)"
}
$atlas.Dispose(); $body.Dispose(); $half.Dispose(); $mc.Dispose(); $source.Dispose()
$mcStream.Dispose(); $appleStream.Dispose(); $minecraft.Dispose(); $apple.Dispose()
