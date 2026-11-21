# Generuje token JWT (HS256) akceptowany przez usługę. Wypisuje sam token.
# Parametry z env: JWT_SECRET (wymagany, min. 32 znaki), JWT_ISSUER (formforge),
# JWT_TTL_SECONDS (3600), JWT_SUBJECT (jury).
# Użycie:  $env:JWT_SECRET = "..."; $token = .\scripts\generate-jwt.ps1
$secret = $env:JWT_SECRET
if (-not $secret -or $secret.Length -lt 32) { Write-Error "Ustaw JWT_SECRET (min. 32 znaki, ten sam co w .env aplikacji)"; exit 1 }
$issuer  = if ($env:JWT_ISSUER) { $env:JWT_ISSUER } else { "formforge" }
$ttl     = if ($env:JWT_TTL_SECONDS) { [int]$env:JWT_TTL_SECONDS } else { 3600 }
$subject = if ($env:JWT_SUBJECT) { $env:JWT_SUBJECT } else { "jury" }

function ConvertTo-Base64Url([byte[]]$bytes) {
    [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$header  = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes('{"alg":"HS256","typ":"JWT"}'))
$payload = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes(('{{"iss":"{0}","sub":"{1}","iat":{2},"exp":{3}}}' -f $issuer, $subject, $now, ($now + $ttl))))
$hmac = New-Object System.Security.Cryptography.HMACSHA256
$hmac.Key = [Text.Encoding]::UTF8.GetBytes($secret)
$signature = ConvertTo-Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes("$header.$payload")))
"$header.$payload.$signature"
