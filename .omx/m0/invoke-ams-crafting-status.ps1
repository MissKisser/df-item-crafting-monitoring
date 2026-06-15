param(
    [string]$Endpoint = "https://comm.ams.game.qq.com/ide/?iChartId=365589&iSubChartId=365589&sIdeToken=bQaMCQ&source=2",
    [string]$OutputPath = ".omx/m0/ams-crafting-status-auth-probe-response.redacted.json"
)

$ErrorActionPreference = "Stop"
$secrets = @()

function Get-RequiredEnv {
    param([string]$Name)

    $value = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = [Environment]::GetEnvironmentVariable($Name, "User")
    }
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = [Environment]::GetEnvironmentVariable($Name, "Machine")
    }
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Missing environment variable: $Name"
    }
    return $value
}

function Redact-Secret {
    param(
        [string]$Text,
        [string[]]$Secrets
    )

    $redacted = $Text
    foreach ($secret in $Secrets) {
        if (-not [string]::IsNullOrWhiteSpace($secret)) {
            $redacted = $redacted.Replace($secret, "[REDACTED]")
        }
    }
    return $redacted
}

try {
    $openid = Get-RequiredEnv "DF_AMS_OPENID"
    $accessToken = Get-RequiredEnv "DF_AMS_ACCESS_TOKEN"
    $acctype = Get-RequiredEnv "DF_AMS_ACCTYPE"
    $appid = Get-RequiredEnv "DF_AMS_APPID"
    $secrets = @($openid, $accessToken, $appid)

    if ($acctype -notin @("qc", "wx")) {
        throw "DF_AMS_ACCTYPE must be 'qc' for QQ or 'wx' for WeChat."
    }

    $cookie = "openid=$openid; acctype=$acctype; appid=$appid; access_token=$accessToken"
    $headers = @{
        "Cookie" = $cookie
        "Content-Type" = "application/x-www-form-urlencoded"
        "User-Agent" = "Mozilla/5.0"
    }

    $response = Invoke-WebRequest -Method Post -Uri $Endpoint -Headers $headers -Body "" -UseBasicParsing
    $body = [string]$response.Content
    $redactedBody = Redact-Secret -Text $body -Secrets $secrets

    $outputDir = Split-Path -Parent $OutputPath
    if (-not [string]::IsNullOrWhiteSpace($outputDir)) {
        New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
    }
    Set-Content -Path $OutputPath -Value $redactedBody -Encoding UTF8

    $json = $null
    try {
        $json = $body | ConvertFrom-Json
    } catch {
        # Keep raw redacted body as the primary artifact when AMS returns non-JSON.
    }

    Write-Output "Endpoint: $Endpoint"
    Write-Output "Auth route: acctype=$acctype appid=[REDACTED]"
    Write-Output "HTTP status: $($response.StatusCode)"
    if ($null -ne $json) {
        Write-Output "AMS ret: $($json.ret)"
        Write-Output "AMS iRet: $($json.iRet)"
        Write-Output "AMS sMsg: $($json.sMsg)"
        Write-Output "AMS serial: $($json.sAmsSerial)"
    }
    Write-Output "Redacted response saved: $OutputPath"
} catch {
    $message = Redact-Secret -Text $_.Exception.Message -Secrets $secrets
    [Console]::Error.WriteLine("Probe failed: $message")
    exit 1
}
