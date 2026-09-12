$ErrorActionPreference = "Stop"

$secretDirectory =
Join-Path `
        $env:LOCALAPPDATA `
        "ExampleLearning\secrets"


Write-Host ""
Write-Host "=========================================="
Write-Host " Local Secrets"
Write-Host "=========================================="
Write-Host ""


if (-not (Test-Path $secretDirectory)) {

    Write-Host "No secret directory found."
    Write-Host ""
    Write-Host "Add a secret with:"
    Write-Host ""
    Write-Host "  .\scripts\secrets\set-secret.ps1"
    Write-Host ""

    exit 0
}


$secretFiles =
Get-ChildItem `
        -LiteralPath $secretDirectory `
        -Filter "*.xml" `
        -File |
        Sort-Object Name


if ($secretFiles.Count -eq 0) {

    Write-Host "No secrets configured."
    Write-Host ""

    exit 0
}


Write-Host "Configured secrets:"
Write-Host ""


foreach ($secretFile in $secretFiles) {

    Write-Host "  - $($secretFile.BaseName)"
}


Write-Host ""
Write-Host "Total: $($secretFiles.Count)"
Write-Host ""