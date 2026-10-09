$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$jar = Join-Path $PSScriptRoot 'wrapper\gradle-wrapper.jar'
$expected = '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046'
if (!(Test-Path $jar)) {
    $temporary = "$jar.$PID.part"
    try {
        Invoke-WebRequest -UseBasicParsing -Uri 'https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar' -OutFile $temporary -TimeoutSec 120
        if ((Get-FileHash $temporary -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) { throw 'Gradle wrapper checksum mismatch.' }
        Move-Item -Force $temporary $jar
    } finally {
        if (Test-Path $temporary) { Remove-Item $temporary }
    }
}
if ((Get-FileHash $jar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) { throw 'Untrusted Gradle wrapper. Refusing to run.' }
