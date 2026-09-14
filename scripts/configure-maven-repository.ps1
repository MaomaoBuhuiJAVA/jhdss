param(
    [Parameter(Mandatory = $true)]
    [string]$ProjectRoot,
    [Parameter(Mandatory = $true)]
    [string]$MavenHome
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$project = [IO.Path]::GetFullPath($ProjectRoot)
$runtime = Join-Path $project '.jhds-runtime'
$fallbackSettings = Join-Path $runtime 'maven-settings.xml'
$runtimeConfig = Join-Path $runtime 'maven-runtime.bat'
$centralUrl = 'https://repo.maven.apache.org/maven2'
$probeArtifact = 'org/apache/poi/poi-ooxml/4.1.2/poi-ooxml-4.1.2.pom'

New-Item -ItemType Directory -Path $runtime -Force | Out-Null

function Write-RuntimeConfig([string]$SettingsPath) {
    $value = if ([string]::IsNullOrWhiteSpace($SettingsPath)) { '' } else { [IO.Path]::GetFullPath($SettingsPath) }
    $line = 'set "JHDS_MAVEN_SETTINGS={0}"' -f $value.Replace('%', '%%')
    [IO.File]::WriteAllText($runtimeConfig, "@echo off`r`n$line`r`n", (New-Object Text.UTF8Encoding($false)))
}

function Test-MirrorAppliesToCentral([string]$MirrorOf) {
    if ([string]::IsNullOrWhiteSpace($MirrorOf)) { return $false }
    $tokens = $MirrorOf.Split(',') | ForEach-Object { $_.Trim().ToLowerInvariant() }
    if ($tokens -contains '!central') { return $false }
    return ($tokens -contains 'central') -or ($tokens -contains '*') -or ($tokens -contains 'external:*')
}

function Read-CentralMirrors([string]$SettingsPath) {
    if (-not (Test-Path -LiteralPath $SettingsPath -PathType Leaf)) { return @() }
    try {
        [xml]$settings = Get-Content -LiteralPath $SettingsPath -Raw
        $nodes = $settings.SelectNodes("/*[local-name()='settings']/*[local-name()='mirrors']/*[local-name()='mirror']")
        return @($nodes | ForEach-Object {
            $mirrorOf = [string]$_.mirrorOf
            if (Test-MirrorAppliesToCentral $mirrorOf) {
                [PSCustomObject]@{
                    Id = [string]$_.id
                    Url = ([string]$_.url).TrimEnd('/')
                    SettingsPath = $SettingsPath
                }
            }
        } | Where-Object { -not [string]::IsNullOrWhiteSpace($_.Url) })
    } catch {
        Write-Host "[WARN] Unable to read Maven settings: $SettingsPath"
        return @()
    }
}

function Test-Repository([string]$RepositoryUrl) {
    try {
        $uri = [Uri]$RepositoryUrl
        [Net.Dns]::GetHostAddresses($uri.DnsSafeHost) | Out-Null
        $requestUrl = $RepositoryUrl.TrimEnd('/') + '/' + $probeArtifact
        Invoke-WebRequest -UseBasicParsing -Method Get -Uri $requestUrl -TimeoutSec 8 | Out-Null
        return $true
    } catch {
        return $false
    }
}

function Write-FallbackSettings {
    $content = @'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <mirrors>
    <mirror>
      <id>jhds-maven-central</id>
      <name>JHDS automatic Maven Central fallback</name>
      <url>https://repo.maven.apache.org/maven2</url>
      <mirrorOf>*</mirrorOf>
    </mirror>
  </mirrors>
</settings>
'@
    [IO.File]::WriteAllText($fallbackSettings, $content, (New-Object Text.UTF8Encoding($false)))
}

Write-RuntimeConfig ''

$settingsFiles = @(
    (Join-Path $env:USERPROFILE '.m2\settings.xml'),
    (Join-Path ([IO.Path]::GetFullPath($MavenHome)) 'conf\settings.xml')
) | Select-Object -Unique
$mirrors = @($settingsFiles | ForEach-Object { Read-CentralMirrors $_ })

if ($mirrors.Count -eq 0) {
    if (Test-Repository $centralUrl) {
        Write-Host '[OK] Maven Central is reachable; repository configuration does not require repair.'
        exit 0
    }
    Write-Host '[ERROR] Maven Central is unreachable.'
    Write-Host '        Check DNS, proxy, firewall, or the internet connection, then try again.'
    exit 1
}

$failedMirrors = @($mirrors | Where-Object { -not (Test-Repository $_.Url) })
if ($failedMirrors.Count -eq 0) {
    Write-Host '[OK] Configured Maven mirror is reachable.'
    exit 0
}

$failedMirrors | ForEach-Object {
    Write-Host "[WARN] Maven mirror is unavailable: $($_.Url)"
}

if (-not (Test-Repository $centralUrl)) {
    Write-Host '[ERROR] Maven mirror and Maven Central are both unreachable.'
    Write-Host '        Check DNS, proxy, firewall, or the internet connection, then try again.'
    exit 1
}

Write-FallbackSettings
Write-RuntimeConfig $fallbackSettings
Write-Host '[OK] Maven repository was repaired for this startup by switching to Maven Central.'
exit 0
