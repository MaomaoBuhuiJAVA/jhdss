param(
    [Parameter(Mandatory = $true)]
    [string]$ProjectRoot,
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$project = [IO.Path]::GetFullPath($ProjectRoot)
if (-not (Test-Path -LiteralPath (Join-Path $project 'pom.xml'))) {
    throw "Invalid project directory: $project"
}

$runtime = Join-Path $project '.jhds-runtime'
$tools = Join-Path $runtime 'tools'
$downloads = Join-Path $runtime 'downloads'
$runtimeEnv = Join-Path $runtime 'runtime-env.bat'
New-Item -ItemType Directory -Path $tools, $downloads -Force | Out-Null
$autoInstall = -not ($env:JHDS_AUTO_INSTALL -and $env:JHDS_AUTO_INSTALL.Trim().ToLowerInvariant() -eq 'false')

function Test-Executable([string]$Path) {
    return -not [string]::IsNullOrWhiteSpace($Path) -and (Test-Path -LiteralPath $Path -PathType Leaf)
}

function Resolve-CommandPath([string]$Name) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $command) { return $null }
    return $command.Source
}

function Download-File([string]$Name, [string]$Url) {
    if ($CheckOnly -or -not $autoInstall) { throw "$Name is missing and automatic installation is disabled" }
    $target = Join-Path $downloads $Name
    $partial = "$target.partial"
    Write-Host "[SETUP] Downloading $Name..."
    Remove-Item -LiteralPath $partial -Force -ErrorAction SilentlyContinue
    Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile $partial
    if (-not (Test-Path -LiteralPath $partial) -or (Get-Item -LiteralPath $partial).Length -lt 1024) {
        throw "Downloaded file is incomplete: $Name"
    }
    Move-Item -LiteralPath $partial -Destination $target -Force
    return $target
}

function Expand-Tool([string]$Name, [string]$Url, [string]$ArchiveType = '') {
    $destination = Join-Path $tools $Name
    if (-not $destination.StartsWith($tools, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Unsafe tool destination: $destination"
    }
    $archiveName = if ($ArchiveType -eq 'tar.gz' -or $Url -match '\.tar\.gz(?:\?|$)') { "$Name.tar.gz" } else { "$Name.zip" }
    $archive = Download-File $archiveName $Url
    Remove-Item -LiteralPath $destination -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    Write-Host "[SETUP] Extracting $Name..."
    if ($archive.ToLowerInvariant().EndsWith('.tar.gz')) {
        tar -xzf $archive -C $destination
        if ($LASTEXITCODE -ne 0) { throw "Unable to extract $Name" }
    } else {
        Expand-Archive -LiteralPath $archive -DestinationPath $destination -Force
    }
    return $destination
}

function Find-File([string[]]$Roots, [string]$Name, [string]$ParentName) {
    foreach ($root in $Roots) {
        if ([string]::IsNullOrWhiteSpace($root) -or -not (Test-Path -LiteralPath $root)) { continue }
        $found = Get-ChildItem -LiteralPath $root -Filter $Name -File -Recurse -ErrorAction SilentlyContinue |
            Where-Object { -not $ParentName -or $_.Directory.Name -ieq $ParentName } |
            Select-Object -First 1
        if ($null -ne $found) { return $found.FullName }
    }
    return $null
}

function Find-JavaHome {
    if ($env:JAVA_HOME -and (Test-Executable (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        return [IO.Path]::GetFullPath($env:JAVA_HOME)
    }
    $java = Resolve-CommandPath 'java.exe'
    if (Test-Executable $java) { return Split-Path (Split-Path $java -Parent) -Parent }
    $roots = @(
        (Join-Path $tools 'jdk17'),
        (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
        (Join-Path $env:ProgramFiles 'Microsoft'),
        (Join-Path $env:ProgramFiles 'Java')
    )
    $java = Find-File $roots 'java.exe' 'bin'
    if (Test-Executable $java) { return Split-Path (Split-Path $java -Parent) -Parent }
    $root = Expand-Tool 'jdk17' 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' 'tar.gz'
    $java = Find-File @($root) 'java.exe' 'bin'
    if (-not (Test-Executable $java)) { throw 'The downloaded JDK does not contain java.exe' }
    return Split-Path (Split-Path $java -Parent) -Parent
}

function Find-MavenHome {
    if ($env:MAVEN_HOME -and (Test-Executable (Join-Path $env:MAVEN_HOME 'bin\mvn.cmd'))) {
        return [IO.Path]::GetFullPath($env:MAVEN_HOME)
    }
    $mvn = Resolve-CommandPath 'mvn.cmd'
    if (Test-Executable $mvn) { return Split-Path (Split-Path $mvn -Parent) -Parent }
    $mvn = Find-File @((Join-Path $tools 'maven')) 'mvn.cmd' 'bin'
    if (-not (Test-Executable $mvn)) {
        $root = Expand-Tool 'maven' 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip'
        $mvn = Find-File @($root) 'mvn.cmd' 'bin'
    }
    if (-not (Test-Executable $mvn)) { throw 'The downloaded Maven archive does not contain mvn.cmd' }
    return Split-Path (Split-Path $mvn -Parent) -Parent
}

function Resolve-ConfiguredProgram([string]$Configured, [string]$CommandName) {
    if (Test-Executable $Configured) { return [IO.Path]::GetFullPath($Configured) }
    if ($Configured -and -not [IO.Path]::IsPathRooted($Configured)) {
        $resolved = Resolve-CommandPath $Configured
        if (Test-Executable $resolved) { return $resolved }
    }
    $resolved = Resolve-CommandPath $CommandName
    if (Test-Executable $resolved) { return $resolved }
    return $null
}

function Find-FFmpegPrograms {
    $ffmpeg = Resolve-ConfiguredProgram $env:CAMERA_LOCAL_FFMPEG_PATH 'ffmpeg.exe'
    $ffprobe = Resolve-ConfiguredProgram $env:CAMERA_LOCAL_FFPROBE_PATH 'ffprobe.exe'
    if ((Test-Executable $ffmpeg) -and -not (Test-Executable $ffprobe)) {
        $sibling = Join-Path (Split-Path $ffmpeg -Parent) 'ffprobe.exe'
        if (Test-Executable $sibling) { $ffprobe = $sibling }
    }
    if (-not (Test-Executable $ffmpeg) -or -not (Test-Executable $ffprobe)) {
        $root = Join-Path $tools 'ffmpeg'
        $ffmpeg = Find-File @($root) 'ffmpeg.exe' 'bin'
        $ffprobe = Find-File @($root) 'ffprobe.exe' 'bin'
    }
    if (-not (Test-Executable $ffmpeg) -or -not (Test-Executable $ffprobe)) {
        $root = Expand-Tool 'ffmpeg' 'https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip'
        $ffmpeg = Find-File @($root) 'ffmpeg.exe' 'bin'
        $ffprobe = Find-File @($root) 'ffprobe.exe' 'bin'
    }
    if (-not (Test-Executable $ffmpeg) -or -not (Test-Executable $ffprobe)) {
        throw 'The downloaded FFmpeg archive is incomplete'
    }
    return @{ Ffmpeg = $ffmpeg; Ffprobe = $ffprobe }
}

function Test-Python([string]$Path) {
    if (-not (Test-Executable $Path)) { return $false }
    & $Path -c "import sys; raise SystemExit(0 if sys.version_info >= (3, 9) else 1)" 2>$null
    return $LASTEXITCODE -eq 0
}

function Find-Python {
    $candidates = @(
        $env:AI_YOLO_PYTHON_PATH,
        (Join-Path $runtime 'python311\python.exe'),
        'D:\jhdss-tools\yolo-venv311\Scripts\python.exe',
        (Resolve-CommandPath 'python.exe')
    )
    foreach ($candidate in $candidates) {
        if (Test-Python $candidate) { return [IO.Path]::GetFullPath($candidate) }
    }
    if ($CheckOnly) { throw 'Python 3.9+ is missing (check-only mode)' }
    $installer = Download-File 'python-3.11.9-amd64.exe' 'https://www.python.org/ftp/python/3.11.9/python-3.11.9-amd64.exe'
    $target = Join-Path $runtime 'python311'
    Write-Host '[SETUP] Installing project-local Python 3.11...'
    $arguments = @('/quiet', 'InstallAllUsers=0', "TargetDir=`"$target`"", 'Include_pip=1',
        'Include_launcher=0', 'PrependPath=0', 'Include_test=0', 'Include_doc=0')
    $process = Start-Process -FilePath $installer -ArgumentList $arguments -Wait -PassThru
    $python = Join-Path $target 'python.exe'
    if ($process.ExitCode -ne 0 -or -not (Test-Python $python)) {
        throw "Python installation failed with exit code $($process.ExitCode)"
    }
    return $python
}

function Ensure-Yolo([string]$Python) {
    & $Python -c 'import cv2, numpy, torch, ultralytics' 2>$null
    if ($LASTEXITCODE -eq 0) { return }
    if ($CheckOnly) { throw 'YOLO Python packages are missing (check-only mode)' }
    Write-Host '[SETUP] Installing YOLO dependencies. The first installation can take several minutes...'
    & $Python -m pip install --disable-pip-version-check --upgrade pip
    if ($LASTEXITCODE -ne 0) { throw 'Unable to update pip' }
    & $Python -m pip install --disable-pip-version-check 'ultralytics>=8.3,<9'
    if ($LASTEXITCODE -ne 0) { throw 'Unable to install YOLO dependencies' }
    & $Python -c 'import cv2, numpy, torch, ultralytics'
    if ($LASTEXITCODE -ne 0) { throw 'YOLO dependency validation failed' }
}

function Test-LocalPort([int]$Port) {
    $client = New-Object Net.Sockets.TcpClient
    try {
        $result = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        return $result.AsyncWaitHandle.WaitOne(600) -and $client.Connected
    } catch { return $false } finally { $client.Close() }
}

function Find-ServiceLike([string]$Pattern) {
    return Get-Service -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match $Pattern -or $_.DisplayName -match $Pattern } |
        Select-Object -First 1
}

function Assert-Administrator {
    $principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'Administrator privileges are required to install Windows services'
    }
}

function Install-WindowsPackage([string]$Id, [string]$FallbackName, [string]$FallbackUrl) {
    if ($CheckOnly -or -not $autoInstall) { throw "$Id is missing and automatic installation is disabled" }
    Assert-Administrator
    $winget = Resolve-CommandPath 'winget.exe'
    if (Test-Executable $winget) {
        Write-Host "[SETUP] Installing $Id with winget..."
        & $winget install --id $Id --exact --silent --accept-source-agreements `
            --accept-package-agreements --disable-interactivity
        if ($LASTEXITCODE -eq 0) { return }
        Write-Warning "winget failed for $Id; using its official installer"
    }
    $installer = Download-File $FallbackName $FallbackUrl
    $process = Start-Process msiexec.exe -ArgumentList "/i `"$installer`" /qn /norestart" -Wait -PassThru
    if ($process.ExitCode -ne 0 -and $process.ExitCode -ne 3010) {
        throw "$Id installation failed with exit code $($process.ExitCode)"
    }
}

function Find-MySqlProgram([string]$Name) {
    $fromPath = Resolve-CommandPath $Name
    if (Test-Executable $fromPath) { return $fromPath }
    $roots = @((Join-Path $env:ProgramFiles 'MySQL'), (Join-Path ${env:ProgramFiles(x86)} 'MySQL'))
    return Find-File $roots $Name 'bin'
}

function Configure-MySqlService {
    $mysqld = Find-MySqlProgram 'mysqld.exe'
    if (-not (Test-Executable $mysqld)) { throw 'MySQL was installed but mysqld.exe was not found' }
    $base = Split-Path (Split-Path $mysqld -Parent) -Parent
    $data = Join-Path $runtime 'mysql-data'
    $config = Join-Path $runtime 'mysql.ini'
    $baseIni = $base.Replace('\', '/')
    $dataIni = $data.Replace('\', '/')
    $ini = "[client]`r`nport=3306`r`n[mysqld]`r`nbasedir=$baseIni`r`ndatadir=$dataIni`r`nport=3306`r`nbind-address=127.0.0.1`r`ncharacter-set-server=utf8mb4`r`n"
    [IO.File]::WriteAllText($config, $ini, (New-Object Text.UTF8Encoding($false)))
    $newData = -not (Test-Path -LiteralPath (Join-Path $data 'mysql'))
    if ($newData) {
        New-Item -ItemType Directory -Path $data -Force | Out-Null
        Write-Host '[SETUP] Initializing a local MySQL data directory...'
        & $mysqld "--defaults-file=$config" --initialize-insecure --console
        if ($LASTEXITCODE -ne 0) { throw 'MySQL data initialization failed' }
    }
    if (-not (Get-Service -Name JHDSMySQL -ErrorAction SilentlyContinue)) {
        Assert-Administrator
        $binary = ('"{0}" --defaults-file="{1}"' -f $mysqld, $config)
        New-Service -Name JHDSMySQL -BinaryPathName $binary -DisplayName 'JHDS MySQL' `
            -StartupType Automatic | Out-Null
    }
    Start-Service -Name JHDSMySQL
    for ($attempt = 0; $attempt -lt 40 -and -not (Test-LocalPort 3306); $attempt++) {
        Start-Sleep -Milliseconds 500
    }
    if (-not (Test-LocalPort 3306)) { throw 'The new MySQL service did not open port 3306' }
    if ($newData) {
        $mysql = Find-MySqlProgram 'mysql.exe'
        $password = if ($env:SPRING_DATASOURCE_PASSWORD) { $env:SPRING_DATASOURCE_PASSWORD } else { 'a123456' }
        $escaped = $password.Replace('\', '\\').Replace("'", "''")
        "ALTER USER 'root'@'localhost' IDENTIFIED BY '$escaped';" |
            & $mysql '--protocol=tcp' '--host=127.0.0.1' '--user=root'
        if ($LASTEXITCODE -ne 0) { throw 'Unable to configure the new MySQL root password' }
    }
}

function Ensure-LocalServices {
    if (-not (Test-LocalPort 3306) -and -not (Find-ServiceLike 'mysql')) {
        Install-WindowsPackage 'Oracle.MySQL' 'mysql-8.4.9-winx64.msi' `
            'https://cdn.mysql.com/Downloads/MySQL-8.4/mysql-8.4.9-winx64.msi'
        Start-Sleep -Seconds 2
        if (-not (Find-ServiceLike 'mysql')) { Configure-MySqlService }
    }
    if (-not (Test-LocalPort 6379) -and -not (Find-ServiceLike 'redis|memurai')) {
        Install-WindowsPackage 'Memurai.MemuraiDeveloper' 'Memurai-Developer-v4.1.2.msi' `
            'https://dist.memurai.com/releases/Memurai-Developer/4.1.2/Memurai-Developer-v4.1.2.msi'
        Start-Sleep -Seconds 2
        if (-not (Find-ServiceLike 'redis|memurai')) {
            throw 'Memurai was installed but its Windows service was not found'
        }
    }
}

function Escape-BatchValue([string]$Value) {
    if ($null -eq $Value) { return '' }
    return $Value.Replace('%', '%%').Replace('"', '')
}

Write-Host '[SETUP] Checking the local runtime...'
$javaHome = Find-JavaHome
$mavenHome = Find-MavenHome
$media = Find-FFmpegPrograms
$python = $null
$yoloEnabled = -not ($env:AI_YOLO_ENABLED -and $env:AI_YOLO_ENABLED.Trim().ToLowerInvariant() -eq 'false')
$device = $env:AI_YOLO_DEVICE
if ($yoloEnabled) {
    $python = Find-Python
    Ensure-Yolo $python
    $model = if ($env:AI_YOLO_MODEL_PATH) { $env:AI_YOLO_MODEL_PATH } else { '.\weights\black_longhorn_best.pt' }
    if (-not [IO.Path]::IsPathRooted($model)) { $model = Join-Path $project $model }
    if (-not (Test-Path -LiteralPath $model -PathType Leaf)) {
        throw "YOLO model is missing: $model. Include weights\black_longhorn_best.pt in the deployment."
    }
    if ([string]::IsNullOrWhiteSpace($device)) {
        $device = (& $python -c "import torch; print('0' if torch.cuda.is_available() else 'cpu')").Trim()
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($device)) { $device = 'cpu' }
    }
}

Ensure-LocalServices

$lines = @(
    '@echo off',
    'chcp 65001 >nul',
    ('set "JAVA_HOME={0}"' -f (Escape-BatchValue $javaHome)),
    ('set "MAVEN_HOME={0}"' -f (Escape-BatchValue $mavenHome)),
    ('set "CAMERA_LOCAL_FFMPEG_PATH={0}"' -f (Escape-BatchValue $media.Ffmpeg)),
    ('set "CAMERA_LOCAL_FFPROBE_PATH={0}"' -f (Escape-BatchValue $media.Ffprobe))
)
if ($yoloEnabled) {
    $lines += ('set "AI_YOLO_PYTHON_PATH={0}"' -f (Escape-BatchValue $python))
    $lines += ('set "AI_YOLO_DEVICE={0}"' -f (Escape-BatchValue $device))
}
$mysqlClient = Find-MySqlProgram 'mysql.exe'
if (Test-Executable $mysqlClient) {
    $lines += ('set "MYSQL_CLIENT_PATH={0}"' -f (Escape-BatchValue $mysqlClient))
}
$content = ($lines -join "`r`n") + "`r`n"
[IO.File]::WriteAllText($runtimeEnv, $content, (New-Object Text.UTF8Encoding($false)))

Write-Host "[OK] Java: $javaHome"
Write-Host "[OK] Maven: $mavenHome"
Write-Host "[OK] FFmpeg: $($media.Ffmpeg)"
if ($yoloEnabled) { Write-Host "[OK] YOLO Python: $python (device=$device)" }
Write-Host '[OK] Runtime check completed.'
