$ErrorActionPreference = "Stop"

$secretDirectory =
Join-Path `
        $env:LOCALAPPDATA `
        "ExampleLearning\secrets"


Write-Host ""
Write-Host "=========================================="
Write-Host " Local Secret Setup"
Write-Host "=========================================="
Write-Host ""


# ============================================================
# Create secret directory
# ============================================================

if (-not (Test-Path $secretDirectory)) {

    New-Item `
        -ItemType Directory `
        -Path $secretDirectory `
        -Force |
            Out-Null
}


# ============================================================
# Secret name
# ============================================================

$secretName =
Read-Host "Secret name (example: AZURE_TRANSLATOR_KEY)"


$secretName =
$secretName.Trim()


if ([string]::IsNullOrWhiteSpace($secretName)) {

    throw "Secret name cannot be empty."
}


# Only allow environment-variable friendly names
if ($secretName -notmatch '^[A-Z][A-Z0-9_]*$') {

    throw @"
Invalid secret name:

$secretName

Use uppercase environment-variable format.

Examples:

AZURE_TRANSLATOR_KEY
GCP_TRANSLATOR_KEY
OPENAI_API_KEY
"@
}


$secretFile =
Join-Path `
        $secretDirectory `
        "$secretName.xml"


# ============================================================
# Existing secret
# ============================================================

if (Test-Path $secretFile) {

    Write-Host ""
    Write-Host "Secret already exists:"
    Write-Host ""
    Write-Host "  $secretName"
    Write-Host ""

    $answer =
    Read-Host "Replace existing secret? (y/N)"


    if ($answer -notin @("y", "Y")) {

        Write-Host ""
        Write-Host "Cancelled."
        exit 0
    }
}


# ============================================================
# Secret value
# ============================================================

Write-Host ""
Write-Host "Enter value for:"
Write-Host ""
Write-Host "  $secretName"
Write-Host ""

$secretValue =
Read-Host `
        "Secret value" `
        -AsSecureString


if ($secretValue.Length -eq 0) {

    throw "Secret value cannot be empty."
}


# ============================================================
# Store using Windows DPAPI
# ============================================================

$secretValue |
        Export-Clixml `
        -LiteralPath $secretFile `
        -Force


Write-Host ""
Write-Host "=========================================="
Write-Host " Secret saved successfully"
Write-Host "=========================================="
Write-Host ""
Write-Host "Name:"
Write-Host "  $secretName"
Write-Host ""
Write-Host "Location:"
Write-Host "  $secretFile"
Write-Host ""