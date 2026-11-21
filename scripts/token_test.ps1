# Test uploadu: dla każdego folderu w Materiały\Materiały_dc (poza "Materiały do zadania dla Uczestników")
# wysyła maksymalnie 1 plik każdego typu (pdf, doc, docx, ods, xlsx, jpg, jpeg, png).
# Użycie: .\scripts\upload-tests.ps1 [-Url http://localhost:8080/upload]
param([string]$Url = "http://localhost:8080/upload")

# Token JWT: $env:JWT_TOKEN = .\scripts\generate-jwt.ps1
if (-not $env:JWT_TOKEN) { Write-Error "Ustaw JWT_TOKEN (np. `$env:JWT_TOKEN = .\scripts\generate-jwt.ps1)"; exit 2 }

$root = Join-Path (Split-Path -Parent $PSScriptRoot) "Materiały\Materiały_dc"
$exts = @("pdf", "doc", "docx", "ods", "xlsx", "jpg", "jpeg", "png")
$skip = "Materiały do zadania dla Uczestników"
$ok = 0; $fail = 0

foreach ($dir in Get-ChildItem -LiteralPath $root -Directory | Sort-Object Name) {
    if ($dir.Name -eq $skip) { continue }
    Write-Host "== $($dir.Name)"
    foreach ($ext in $exts) {
        $file = Get-ChildItem -LiteralPath $dir.FullName -File |
            Where-Object { $_.Extension.TrimStart('.') -ieq $ext -and $_.Name -notlike '*wizualizacj*' } |
            Sort-Object Name | Select-Object -First 1
        if (-not $file) { Write-Host ("   {0,-5} brak pliku" -f $ext); continue }
        $code = & curl.exe -s -o NUL -w "%{http_code}" -H "Authorization: Bearer $($env:JWT_TOKEN)" -X PUT -F "file=@$($file.FullName)" $Url
        if ($code -match '^2') { $ok++ } else { $fail++ }
        Write-Host ("   {0,-5} {1}  {2}" -f $ext, $code, $file.Name)
    }
}

Write-Host ""
Write-Host "Podsumowanie: OK=$ok, BŁĘDY=$fail"
if ($fail -gt 0) { exit 1 }
