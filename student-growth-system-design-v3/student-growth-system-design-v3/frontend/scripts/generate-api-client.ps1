$ErrorActionPreference = 'Stop'

$frontendRoot = Split-Path $PSScriptRoot -Parent
$projectRoot = Split-Path $frontendRoot -Parent
$generatorVersion = '7.10.0'
$generatorJar = Join-Path $env:USERPROFILE ".m2\repository\org\openapitools\openapi-generator-cli\$generatorVersion\openapi-generator-cli-$generatorVersion.jar"
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$output = Join-Path $frontendRoot 'src\api\generated'
$sourceSpec = Join-Path $projectRoot 'api\openapi.yaml'
$normalizedSpec = Join-Path $projectRoot 'target\frontend-openapi-typescript.yaml'

if (-not (Test-Path $generatorJar)) {
    & (Join-Path $projectRoot 'mvnw.cmd') "dependency:get" "-Dartifact=org.openapitools:openapi-generator-cli:$generatorVersion"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

if (Test-Path $output) {
    Remove-Item -Recurse -Force $output
}

$contract = [IO.File]::ReadAllText($sourceSpec)
$confirmationText = -join ([char[]]@(0x786E, 0x8BA4, 0x6062, 0x590D))
$constDeclaration = "confirmationText: {type: string, const: $confirmationText}"
$enumDeclaration = "confirmationText: {type: string, enum: [$confirmationText], x-enum-varnames: [ConfirmRestore]}"
if (-not $contract.Contains($constDeclaration)) {
    throw 'Expected Restore confirmationText const was not found in the frozen contract'
}
New-Item -ItemType Directory -Force (Split-Path $normalizedSpec -Parent) | Out-Null
[IO.File]::WriteAllText($normalizedSpec, $contract.Replace($constDeclaration, $enumDeclaration), [Text.UTF8Encoding]::new($false))

Push-Location $projectRoot
try {
    & $java -jar $generatorJar generate `
        -i 'target/frontend-openapi-typescript.yaml' `
        -g typescript-fetch `
        -o 'frontend/src/api/generated' `
        --additional-properties 'typescriptThreePlus=true,supportsES6=true' `
        --global-property 'apis,models,supportingFiles=index.ts:runtime.ts'
} finally {
    Pop-Location
}

if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Remove-Item -Recurse -Force (Join-Path $output '.openapi-generator') -ErrorAction SilentlyContinue
