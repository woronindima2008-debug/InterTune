param(
    [string]$Serial = "192.168.0.103:5555",
    [string]$AdbPath = "",
    [string]$PackageName = "com.dd3boh.outertune.tv"
)
$ErrorActionPreference = "Stop"
$Repo = "woronindima2008-debug/InterTune"
$ReportBranch = "device-test-reports"

function Find-Adb {
    if ($AdbPath -and (Test-Path $AdbPath)) { return (Resolve-Path $AdbPath).Path }
    $cmd = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @((Join-Path $env:USERPROFILE "Desktop\platform-tools\adb.exe"),(Join-Path $env:USERPROFILE "Downloads\platform-tools\adb.exe"),"C:\platform-tools\adb.exe")
    foreach ($candidate in $candidates) { if (Test-Path $candidate) { return $candidate } }
    throw "adb.exe not found. Pass -AdbPath with the full path to adb.exe."
}
function Invoke-Adb([string[]]$Args) {
    $output = & $script:Adb @Args 2>&1
    if ($LASTEXITCODE -ne 0) { throw ("ADB command failed: " + ($output -join [Environment]::NewLine)) }
    return ($output -join [Environment]::NewLine).Trim()
}

$Adb = Find-Adb
$deviceList = Invoke-Adb @("devices")
if ($deviceList -notmatch [regex]::Escape($Serial) -or $deviceList -notmatch "$([regex]::Escape($Serial))\s+device") {
    throw "Device $Serial is not shown as 'device'. Check ADB AppControl and debugging."
}
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outDir = Join-Path $PSScriptRoot "InterTune-TV-Diagnostics"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$reportPath = Join-Path $outDir "device-report-$stamp.txt"
$shotPath = Join-Path $outDir "screen-$stamp.png"

$model = Invoke-Adb @("-s",$Serial,"shell","getprop","ro.product.model")
$android = Invoke-Adb @("-s",$Serial,"shell","getprop","ro.build.version.release")
$sdk = Invoke-Adb @("-s",$Serial,"shell","getprop","ro.build.version.sdk")
$size = Invoke-Adb @("-s",$Serial,"shell","wm","size")
$packageInfo = Invoke-Adb @("-s",$Serial,"shell","dumpsys","package",$PackageName)
$versionName = ([regex]::Match($packageInfo, "versionName=([^\s]+)")).Groups[1].Value
$versionCode = ([regex]::Match($packageInfo, "versionCode=(\d+)")).Groups[1].Value
$activityDump = Invoke-Adb @("-s",$Serial,"shell","dumpsys","activity","activities")
$focused = ([regex]::Match($activityDump, "mResumedActivity:.*")).Value
if (-not $focused) { $focused = "Not reported by ActivityManager" }

$crashDump = Invoke-Adb @("-s",$Serial,"logcat","-d","-t","2500","-s","AndroidRuntime:E")
$crashLines = @($crashDump -split "\r?\n" | Where-Object { $_ -match "FATAL EXCEPTION|Process: $([regex]::Escape($PackageName))|^\s+at " } | Select-Object -First 25)
if ($crashLines.Count -eq 0) { $crashSummary = "No AndroidRuntime fatal-exception lines found in the recent log buffer." }
else {
    $crashSummary = ($crashLines -join [Environment]::NewLine) -replace '(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}', '[redacted-email]'
    $crashSummary = $crashSummary -replace '(?i)https?://\S+', '[redacted-url]'
}
$report = @"
InterTune TV device diagnostic
Captured: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz")
Device model: $model
Android version: $android (SDK $sdk)
Display: $size
App package: $PackageName
App version name: $versionName
App version code: $versionCode
Resumed activity: $focused

Recent fatal-exception summary (filtered; no full logcat):
$crashSummary

Privacy: device serial, Wi-Fi details, full logcat, account data and screenshot are not included in this report.
"@
Set-Content -Path $reportPath -Value $report -Encoding UTF8

# Screenshot stays on this PC; it is never uploaded.
$remoteShot = "/sdcard/intertune-tv-diagnostic.png"
Invoke-Adb @("-s",$Serial,"shell","screencap","-p",$remoteShot) | Out-Null
Invoke-Adb @("-s",$Serial,"pull",$remoteShot,$shotPath) | Out-Null
Invoke-Adb @("-s",$Serial,"shell","rm",$remoteShot) | Out-Null

Write-Host "Diagnostic report: $reportPath" -ForegroundColor Green
Write-Host "Local screenshot (not uploaded): $shotPath" -ForegroundColor Green
$consent = Read-Host "Upload this filtered text report to the PUBLIC InterTune GitHub repository? Type YES"
if ($consent -cne "YES") { Write-Host "Upload skipped; nothing was sent." -ForegroundColor Yellow; exit 0 }

$gh = Get-Command gh.exe -ErrorAction SilentlyContinue
if (-not $gh) { throw "GitHub CLI is missing. Install gh.exe and run 'gh auth login' before uploading." }
& $gh.Source auth status | Out-Null
if ($LASTEXITCODE -ne 0) { throw "GitHub CLI is not authenticated. Run 'gh auth login'." }
$baseSha = (& $gh.Source api "repos/$Repo/git/ref/heads/visionos-fix" --jq ".object.sha").Trim()
if ($LASTEXITCODE -ne 0 -or -not $baseSha) { throw "Could not read visionos-fix branch from GitHub." }
& $gh.Source api "repos/$Repo/git/ref/heads/$ReportBranch" --silent 2>$null
if ($LASTEXITCODE -ne 0) {
    & $gh.Source api --method POST "repos/$Repo/git/refs" -f "ref=refs/heads/$ReportBranch" -f "sha=$baseSha" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not create report branch '$ReportBranch'." }
}
$relativeName = "device-reports/$stamp.txt"
$b64 = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($reportPath))
& $gh.Source api --method PUT "repos/$Repo/contents/$relativeName" -f "message=Add sanitized Android TV diagnostic report $stamp" -f "content=$b64" -f "branch=$ReportBranch" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "GitHub upload failed. Local report remains at $reportPath." }
Write-Host "Filtered report uploaded to '$ReportBranch/$relativeName'. No screenshot or raw logcat was uploaded." -ForegroundColor Green
