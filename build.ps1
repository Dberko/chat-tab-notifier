<#
.SYNOPSIS
    Builds the Chat Tab Notifier RuneLite plugin from the command line, without Gradle.

.DESCRIPTION
    RuneLite already downloaded everything this plugin compiles against into
    ~/.runelite/repository2, so the only tool needed is a JDK's javac. This script compiles
    src/main/java against those jars, packages a jar, and copies it into
    ~/.runelite/sideloaded-plugins where the normal RuneLite client will pick it up.

.PARAMETER Verify
    Compile and run the self-check (message routing, chat view fallback, pulse maths) instead of installing.

.PARAMETER Run
    Compile and launch a RuneLite client with the plugin loaded, using RuneLite's bundled JRE.

.PARAMETER Install
    Force the copy into ~/.runelite/sideloaded-plugins (implied when neither -Verify nor -Run).

.PARAMETER Clean
    Delete the build directory first.

.EXAMPLE
    .\build.ps1 -Verify
    .\build.ps1
    .\build.ps1 -Run
#>
param(
    [switch]$Verify,
    [switch]$Run,
    [switch]$Install,
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'

$root        = $PSScriptRoot
$runeliteDir = Join-Path $env:USERPROFILE '.runelite'
$repoDir     = Join-Path $runeliteDir 'repository2'
$sideloadDir = Join-Path $runeliteDir 'sideloaded-plugins'
$buildDir    = Join-Path $root 'build'
$classesDir  = Join-Path $buildDir 'classes'
$devDir      = Join-Path $buildDir 'dev'
$jarPath     = Join-Path $buildDir 'chat-tab-notifier.jar'

function Get-JdkMajor([string]$toolPath)
{
    # Every JDK ships a 'release' file at its home naming its version, which beats running the tool
    # and parsing output that JDK 8 prints on stderr and everything since prints on stdout.
    $jdkHome = Split-Path (Split-Path $toolPath -Parent) -Parent
    $release = Join-Path $jdkHome 'release'

    if (-not (Test-Path $release)) { return 0 }

    $line = Select-String -Path $release -Pattern '^JAVA_VERSION="?([0-9._]+)' | Select-Object -First 1

    if (-not $line) { return 0 }

    $version = $line.Matches[0].Groups[1].Value
    $parts = $version.Split('.')

    # 1.8.0_392 is Java 8; 11.0.21 is Java 11.
    if ($parts[0] -eq '1' -and $parts.Count -gt 1) { return [int]$parts[1] }

    return [int]$parts[0]
}

function Find-JdkTool([string]$name, [int]$minMajor = 11)
{
    $candidates = @()

    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME "bin\$name.exe") }

    $onPath = Get-Command "$name.exe" -ErrorAction SilentlyContinue
    if ($onPath) { $candidates += $onPath.Source }

    $globs = @(
        (Join-Path $env:USERPROFILE "scoop\apps\*\current\bin\$name.exe"),
        (Join-Path ${env:ProgramFiles} "Eclipse Adoptium\*\bin\$name.exe"),
        (Join-Path ${env:ProgramFiles} "Microsoft\jdk-*\bin\$name.exe"),
        (Join-Path ${env:ProgramFiles} "Java\*\bin\$name.exe"),
        (Join-Path ${env:ProgramFiles} "Amazon Corretto\*\bin\$name.exe"),
        (Join-Path $env:LOCALAPPDATA "Programs\Eclipse Adoptium\*\bin\$name.exe")
    )

    foreach ($glob in $globs)
    {
        $hits = Get-ChildItem $glob -ErrorAction SilentlyContinue
        if ($hits) { $candidates += ($hits | Select-Object -ExpandProperty FullName) }
    }

    # JAVA_HOME often points at a JDK 8 that predates --release, so a candidate too old to target
    # Java 11 is skipped rather than failing the build with a flag error.
    $tooOld = @()

    foreach ($candidate in $candidates)
    {
        if (-not ($candidate -and (Test-Path $candidate))) { continue }

        $major = Get-JdkMajor $candidate

        if ($major -ge $minMajor) { return $candidate }

        $tooOld += "$candidate (Java $major)"
    }

    foreach ($skipped in ($tooOld | Select-Object -Unique))
    {
        Write-Host "skipped $skipped - too old to target Java $minMajor" -ForegroundColor DarkGray
    }

    return $null
}

function Get-NewestJars([string]$dir)
{
    # repository2 keeps old versions around after a client update, and plain alphabetical order puts
    # client-1.12.36.jar ahead of client-1.12.37.jar - which would silently build against the older
    # API. Keep only the newest jar per artifact.
    $newest = @{}

    foreach ($file in Get-ChildItem $dir -Filter *.jar)
    {
        if ($file.Name -match '^(?<stem>.+?)-(?<ver>\d+(\.\d+)+)(?<tail>[-.].*)?\.jar$')
        {
            $key = $matches['stem'] + $matches['tail']
            $version = [version]$matches['ver']
        }
        else
        {
            $key = $file.Name
            $version = [version]'0.0'
        }

        if ((-not $newest.ContainsKey($key)) -or ($newest[$key].Version -lt $version))
        {
            $newest[$key] = [pscustomobject]@{ Version = $version; Path = $file.FullName }
        }
    }

    return ($newest.Values | Select-Object -ExpandProperty Path)
}

function Invoke-Tool([string]$exe, [string[]]$toolArgs, [string]$what)
{
    & $exe @toolArgs
    if ($LASTEXITCODE -ne 0) { throw "$what failed (exit $LASTEXITCODE)" }
}

function Write-ArgFile([string]$path, [string[]]$files)
{
    # javac argfiles treat backslash as an escape, so hand it forward slashes.
    $lines = $files | ForEach-Object { '"' + ($_ -replace '\\', '/') + '"' }
    [System.IO.File]::WriteAllLines($path, $lines, (New-Object System.Text.UTF8Encoding $false))
}

# --- locate tools ---------------------------------------------------------------------

if (-not (Test-Path $repoDir))
{
    throw "No RuneLite dependency cache at $repoDir. Launch RuneLite once, then re-run this."
}

$javac = Find-JdkTool 'javac'

if (-not $javac)
{
    Write-Host ""
    Write-Host "No JDK found. RuneLite ships a JRE (no compiler), so you need a JDK 11 or newer:" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "    scoop bucket add java; scoop install temurin11-jdk"
    Write-Host "  or"
    Write-Host "    winget install --id EclipseAdoptium.Temurin.11.JDK -e"
    Write-Host ""
    Write-Host "Then open a new terminal and re-run this script."
    exit 1
}

$jdkBin = Split-Path $javac -Parent
$jar    = Join-Path $jdkBin 'jar.exe'

# Prefer RuneLite's own JRE to run the client, so the plugin runs on the same Java the client uses.
$java = Join-Path $env:LOCALAPPDATA 'RuneLite\jre\bin\java.exe'
if (-not (Test-Path $java)) { $java = Join-Path $jdkBin 'java.exe' }

Write-Host "javac : $javac"
Write-Host "jars  : $repoDir"

# --- compile --------------------------------------------------------------------------

if ($Clean -and (Test-Path $buildDir)) { Remove-Item $buildDir -Recurse -Force }

New-Item -ItemType Directory -Force -Path $classesDir | Out-Null

$jars = Get-NewestJars $repoDir
$classpath = $jars -join ';'

$clientJar = $jars | Where-Object { (Split-Path $_ -Leaf) -like 'client-*.jar' } | Select-Object -First 1
Write-Host "client: $(Split-Path $clientJar -Leaf)"

$mainSources = Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter *.java |
    Select-Object -ExpandProperty FullName

$argFile = Join-Path $buildDir 'sources.txt'
Write-ArgFile $argFile $mainSources

Invoke-Tool $javac @('--release', '11', '-encoding', 'UTF-8', '-nowarn',
    '-cp', $classpath, '-d', $classesDir, "@$argFile") 'javac'

$resources = Join-Path $root 'src\main\resources'
if (Test-Path $resources) { Copy-Item (Join-Path $resources '*') $classesDir -Recurse -Force }

Write-Host "compiled $($mainSources.Count) source files" -ForegroundColor Green

# --- self check -----------------------------------------------------------------------

if ($Verify)
{
    New-Item -ItemType Directory -Force -Path $devDir | Out-Null

    $checkSource = Join-Path $root 'src/test/java/com/chattabnotifier/SelfCheck.java'
    $checkArgFile = Join-Path $buildDir 'selfcheck.txt'
    Write-ArgFile $checkArgFile @($checkSource)

    Invoke-Tool $javac @('--release', '11', '-encoding', 'UTF-8', '-nowarn',
        '-cp', "$classpath;$classesDir", '-d', $devDir, "@$checkArgFile") 'javac (self check)'

    & $java '-cp' "$classpath;$classesDir;$devDir" 'com.chattabnotifier.SelfCheck'
    exit $LASTEXITCODE
}

# --- package --------------------------------------------------------------------------

Invoke-Tool $jar @('--create', '--file', $jarPath, '-C', $classesDir, '.') 'jar'
Write-Host "packaged $jarPath" -ForegroundColor Green

# --- run a dev client -----------------------------------------------------------------

if ($Run)
{
    New-Item -ItemType Directory -Force -Path $devDir | Out-Null

    $launcherSource = Join-Path $root 'src/test/java/com/chattabnotifier/ChatTabNotifierPluginLauncher.java'
    $launcherArgFile = Join-Path $buildDir 'launcher.txt'
    Write-ArgFile $launcherArgFile @($launcherSource)

    Invoke-Tool $javac @('--release', '11', '-encoding', 'UTF-8', '-nowarn',
        '-cp', "$classpath;$classesDir", '-d', $devDir, "@$launcherArgFile") 'javac (launcher)'

    # In -Run the plugin is already on the classpath, so any other copy would register a second
    # instance of the same plugin from a different class loader - two overlays drawing the same
    # highlight. Park them for the duration and put them back when the client exits.
    $parked = @()

    $installed = Join-Path $sideloadDir 'chat-tab-notifier.jar'
    if (Test-Path $installed) { $parked += $installed }

    # A Plugin Hub install of this same plugin counts too.
    $hubDir = Join-Path $runeliteDir 'plugins'
    if (Test-Path $hubDir)
    {
        $parked += (Get-ChildItem $hubDir -Filter 'chat-tab-notifier_*.jar' | Select-Object -ExpandProperty FullName)
    }

    $moved = @()

    foreach ($jar in $parked)
    {
        try
        {
            Move-Item $jar "$jar.disabled" -Force -ErrorAction Stop
            $moved += $jar
            Write-Host "parked $(Split-Path $jar -Leaf) so the plugin loads once" -ForegroundColor Yellow
        }
        catch
        {
            # A running client holds its plugin jars open, so this means one is already up.
            foreach ($done in $moved) { Move-Item "$done.disabled" $done -Force }

            Write-Host ""
            Write-Host "Could not park $(Split-Path $jar -Leaf) - a RuneLite client is already running" -ForegroundColor Red
            Write-Host "and holding it open. Close that client and run this again, otherwise the"
            Write-Host "plugin would load twice: once from the hub jar and once from this build."
            exit 1
        }
    }

    $parked = $moved

    Write-Host "starting RuneLite with the plugin loaded..." -ForegroundColor Cyan
    Write-Host "(--debug is on, so the log names every plugin as it loads)" -ForegroundColor DarkGray

    try
    {
        & $java '-ea' '-cp' "$classpath;$classesDir;$devDir" 'com.chattabnotifier.ChatTabNotifierPluginLauncher' '--developer-mode' '--debug'
    }
    finally
    {
        # Count what actually moved back, not what was parked on the way in. An install run during
        # this one deletes the parked copy on purpose, and reporting a restore that never happened
        # makes it look as though a stale jar just overwrote the fresh one.
        $restored = 0

        foreach ($jar in $parked)
        {
            if (Test-Path "$jar.disabled")
            {
                Move-Item "$jar.disabled" $jar -Force
                $restored++
            }
        }

        if ($restored) { Write-Host "restored $restored parked jar(s)" -ForegroundColor Yellow }
    }

    exit $LASTEXITCODE
}

# --- install --------------------------------------------------------------------------

New-Item -ItemType Directory -Force -Path $sideloadDir | Out-Null
Copy-Item $jarPath (Join-Path $sideloadDir 'chat-tab-notifier.jar') -Force

# Clean up anything a previous -Run parked, so the two modes cannot both leave a copy behind.
$parked = Join-Path $sideloadDir 'chat-tab-notifier.jar.disabled'
if (Test-Path $parked) { Remove-Item $parked -Force }

Write-Host "installed to $sideloadDir" -ForegroundColor Green
Write-Host ""
Write-Host "Note: the official launcher (and therefore the Jagex Launcher) forces developer mode" -ForegroundColor Yellow
Write-Host "off, and the client only reads this directory in developer mode. So this jar loads only"
Write-Host "in a client started outside the launcher - see 'Getting it to actually load' in the README."
Write-Host ""
Write-Host "For a client you can start here: .\build.ps1 -Run"
