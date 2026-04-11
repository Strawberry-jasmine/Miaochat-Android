param(
    [switch]$SkipTests
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = (Resolve-Path (Join-Path $scriptDir '..')).Path
. (Join-Path $scriptDir 'BuildCommon.ps1')

Set-RelayChatBuildEnv -ProjectRoot $projectRoot

$tasks = if ($SkipTests) {
    @('bundleRelease')
} else {
    @('testDebugUnitTest', 'bundleRelease')
}

Invoke-RelayChatGradle -ProjectRoot $projectRoot -Tasks $tasks
