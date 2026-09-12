$ErrorActionPreference = "Stop"

$secretDirectory =
Join-Path `
        $env:LOCALAPPDATA `
        "ExampleLearning\secrets"


Write-Host ""
Write-Host "=========================================="
Write-Host " Remove Local Secret"
Write-Host "=========================================="
Write-Host ""


if (-not (Test-Path $secretDirectory)) {

    Write-Host "No secrets configured."
    exit 0
}


# ============================================================
# Secret name
# ============================================================

$secretName =
Read-Host "Secret name to remove"


$secretName =
$secretName.Trim()


if ([string]::IsNullOrWhiteSpace($secretName)) {

    throw "Secret name cannot be empty."
}


if ($secretName -notmatch '^[A-Z][A-Z0-9_]*$') {

    throw "Invalid secret name: $secretName"
}


$secretFile =
Join-Path `
        $secretDirectory `
        "$secretName.xml"


# ============================================================
# Check existence
# ============================================================

if (-not (Test-Path $secretFile)) {

    Write-Host ""
    Write-Host "Secret not found:"
    Write-Host ""
    Write-Host "  $secretName"
    Write-Host ""

    exit 0
}


# ============================================================
# Confirmation
# ============================================================

Write-Host ""
Write-Host "Secret:"
Write-Host ""
Write-Host "  $secretName"
Write-Host ""

$answer =
Read-Host "Remove this secret? (y/N)"


if ($answer -notin @("y", "Y")) {

    Write-Host ""
    Write-Host "Cancelled."

    exit 0
}


# ============================================================
# Delete
# ============================================================

Remove-Item `
    -LiteralPath $secretFile `
    -Force


Write-Host ""
Write-Host "Secret removed:"
Write-Host ""
Write-Host "  $secretName"
Write-Host ""