function Resolve-JavaHome {
    if ($env:JAVA_HOME) {
        $javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if (Test-Path $javaExe) {
            return $env:JAVA_HOME
        }
    }

    $patterns = @(
        'C:\Program Files\Microsoft\jdk-17*',
        'C:\Program Files\Eclipse Adoptium\jdk-17*',
        'C:\Program Files\Java\jdk-17*'
    )

    foreach ($pattern in $patterns) {
        $match = Get-ChildItem $pattern -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            Select-Object -First 1
        if ($match) {
            $javaExe = Join-Path $match.FullName 'bin\java.exe'
            if (Test-Path $javaExe) {
                return $match.FullName
            }
        }
    }

    throw 'JDK 17 was not found. Install it and retry.'
}

function Set-RelayChatBuildEnv {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ProjectRoot
    )

    $javaHome = Resolve-JavaHome
    $env:JAVA_HOME = $javaHome
    $env:Path = "$javaHome\bin;$env:Path"

    if (-not $env:ANDROID_SDK_ROOT) {
        $defaultSdk = "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk"
        if (Test-Path $defaultSdk) {
            $env:ANDROID_SDK_ROOT = $defaultSdk
        }
    }

    $localProperties = Join-Path $ProjectRoot 'local.properties'
    if (-not (Test-Path $localProperties) -and $env:ANDROID_SDK_ROOT) {
        $sdkPath = $env:ANDROID_SDK_ROOT -replace '\\', '/'
        Set-Content -Path $localProperties -Value "sdk.dir=$sdkPath"
    }
}

function Invoke-RelayChatGradle {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ProjectRoot,

        [Parameter(Mandatory = $true)]
        [string[]]$Tasks
    )

    Push-Location $ProjectRoot
    try {
        & .\gradlew.bat @Tasks
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle failed with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
}
