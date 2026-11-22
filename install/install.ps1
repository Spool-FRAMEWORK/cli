# Installs the spool command: irm https://spoolframework.dev/install.ps1 | iex
#
#   SPOOL_HOME            where to install it, ~\.spool by default
#   SPOOL_NO_MODIFY_PATH  set it to leave the PATH alone
#   SPOOL_INSTALL_JRE     yes or no, to answer whether to download a Java runtime without being asked
#   SPOOL_DOWNLOAD_URL    where spool.zip and spool.zip.sha256 are, the latest release by default
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$RequiredJava = 21
$SpoolHome = if ($env:SPOOL_HOME) { $env:SPOOL_HOME } else { Join-Path $HOME '.spool' }
$DownloadUrl = if ($env:SPOOL_DOWNLOAD_URL) { $env:SPOOL_DOWNLOAD_URL } else { 'https://github.com/Spool-FRAMEWORK/cli/releases/latest/download' }
$Adoptium = "https://api.adoptium.net/v3/binary/latest/$RequiredJava/ga/windows/x64/jre/hotspot/normal/eclipse"

function Stop-Install([string] $Message) {
    throw "spool: $Message"
}

function Get-Download([string] $Url, [string] $File) {
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        (New-Object Net.WebClient).DownloadFile($Url, $File)
    } catch {
        Stop-Install "could not download $Url"
    }
}

function Assert-Sha256([string] $Zip, [string] $Sha256File) {
    $expected = ((Get-Content $Sha256File -Raw).Trim() -split '\s+')[0]
    $actual = (Get-FileHash $Zip -Algorithm SHA256).Hash
    if ($actual -ne $expected) { Stop-Install 'spool.zip does not match its SHA-256: nothing was installed' }
}

function Expand-Spool([string] $Work) {
    $unpacked = Join-Path $Work 'unpacked'
    Expand-Archive (Join-Path $Work 'spool.zip') $unpacked -Force
    New-Item -ItemType Directory -Force $SpoolHome | Out-Null
    foreach ($folder in 'bin', 'lib') {
        Remove-Item (Join-Path $SpoolHome $folder) -Recurse -Force -ErrorAction SilentlyContinue
        Move-Item (Join-Path $unpacked $folder) $SpoolHome
    }
}

function Add-ToPath {
    $bin = Join-Path $SpoolHome 'bin'
    if ($env:SPOOL_NO_MODIFY_PATH) {
        Write-Output "Add $bin to your PATH to run spool."
        return
    }
    $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
    if (($userPath -split ';') -notcontains $bin) {
        [Environment]::SetEnvironmentVariable('Path', (@($bin, $userPath) | Where-Object { $_ }) -join ';', 'User')
    }
    Write-Output "Added $bin to your PATH. Open a new terminal to run spool."
}

function Get-JavaMajor([string] $Java) {
    $version = cmd /c "`"$Java`" -version 2>&1"
    if ("$version" -match 'version "(\d+)') { return [int] $Matches[1] }
    return 0
}

function Test-Java {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += Join-Path $env:JAVA_HOME 'bin\java.exe' }
    $onPath = Get-Command java -ErrorAction SilentlyContinue
    if ($onPath) { $candidates += $onPath.Source }
    foreach ($java in $candidates) {
        if ((Test-Path $java) -and (Get-JavaMajor $java) -ge $RequiredJava) { return $true }
    }
    return $false
}

function Test-WantsJre {
    switch ($env:SPOOL_INSTALL_JRE) {
        'yes' { return $true }
        'no' { return $false }
    }
    if (-not [Environment]::UserInteractive) { return $false }
    $answer = Read-Host "Download a Temurin Java $RequiredJava runtime into $SpoolHome\jre? [y/N]"
    return $answer -in 'y', 'yes'
}

function Install-Jre([string] $Work) {
    $archive = Join-Path $Work 'jre.zip'
    Get-Download $Adoptium $archive
    $unpacked = Join-Path $Work 'jre'
    Expand-Archive $archive $unpacked -Force
    $java = Get-ChildItem $unpacked -Recurse -Filter java.exe | Select-Object -First 1
    $jre = Join-Path $SpoolHome 'jre'
    Remove-Item $jre -Recurse -Force -ErrorAction SilentlyContinue
    Move-Item $java.Directory.Parent.FullName $jre
    Write-Output "Installed a Temurin Java $RequiredJava runtime in $jre"
}

function Assert-Java([string] $Work) {
    if (Test-Java) { return }
    Write-Output "spool needs Java $RequiredJava or newer, and none was found."
    Write-Output "It can download a Temurin Java $RequiredJava runtime into $SpoolHome\jre, only for spool."
    if (Test-WantsJre) { Install-Jre $Work }
    else { Write-Output "Install Java $RequiredJava, or run this again with SPOOL_INSTALL_JRE=yes, before running spool." }
}

function Install-Spool {
    $work = Join-Path ([IO.Path]::GetTempPath()) ([Guid]::NewGuid())
    New-Item -ItemType Directory $work | Out-Null
    try {
        Get-Download "$DownloadUrl/spool.zip" (Join-Path $work 'spool.zip')
        Get-Download "$DownloadUrl/spool.zip.sha256" (Join-Path $work 'spool.zip.sha256')
        Assert-Sha256 (Join-Path $work 'spool.zip') (Join-Path $work 'spool.zip.sha256')
        Expand-Spool $work
        Write-Output "Installed spool in $SpoolHome"
        Add-ToPath
        Assert-Java $work
    } finally {
        Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Install-Spool
