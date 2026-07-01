# run-tests.ps1 -- compile and run the JUnit 5 test suite for Battleship v2.
#
# Usage:  ./run-tests.ps1
#
# Requires a JDK. It uses $env:JAVA_HOME if set, otherwise falls back to the
# Liberica JDK 17 install. The JUnit 5 standalone launcher is downloaded into
# lib/ automatically the first time if it isn't already present.

$ErrorActionPreference = "Stop"

# --- locate the JDK -------------------------------------------------------
if ($env:JAVA_HOME) {
    $javac = Join-Path $env:JAVA_HOME "bin\javac.exe"
    $java  = Join-Path $env:JAVA_HOME "bin\java.exe"
} else {
    $javac = "C:\Program Files\BellSoft\LibericaJDK-17-Full\bin\javac.exe"
    $java  = "C:\Program Files\BellSoft\LibericaJDK-17-Full\bin\java.exe"
}

# --- ensure the JUnit standalone launcher is available --------------------
$junitVersion = "1.10.2"
$jar = "lib\junit-platform-console-standalone-$junitVersion.jar"
if (-not (Test-Path $jar)) {
    Write-Host "JUnit launcher not found; downloading $junitVersion ..."
    New-Item -ItemType Directory -Force -Path lib | Out-Null
    $url = "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/$junitVersion/junit-platform-console-standalone-$junitVersion.jar"
    Invoke-WebRequest -Uri $url -OutFile $jar
}

# --- compile main sources, then tests -------------------------------------
Write-Host "Compiling main sources -> bin ..."
New-Item -ItemType Directory -Force -Path bin | Out-Null
& $javac -d bin (Get-ChildItem -Recurse src\*.java).FullName

Write-Host "Compiling tests -> bin-test ..."
New-Item -ItemType Directory -Force -Path bin-test | Out-Null
& $javac -cp "bin;$jar" -d bin-test (Get-ChildItem -Recurse test\*.java).FullName

# --- run the tests --------------------------------------------------------
Write-Host "Running tests ..."
& $java -jar $jar execute --class-path "bin;bin-test" --scan-class-path --details=tree
