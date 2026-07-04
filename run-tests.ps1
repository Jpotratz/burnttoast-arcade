# run-tests.ps1 -- thin wrapper: the build is Gradle now.
#
# Usage:  ./run-tests.ps1
#
# The Gradle wrapper downloads Gradle itself; the Java toolchain wants a JDK 21
# (Liberica 21 Full is the local install). Same thing CI runs on every push.

$ErrorActionPreference = "Stop"
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Program Files\BellSoft\LibericaJDK-21-Full"
}
& "$PSScriptRoot\gradlew.bat" test
exit $LASTEXITCODE
