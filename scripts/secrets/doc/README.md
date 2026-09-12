# Local Secret Management

This project uses local secret management to store API keys and other sensitive credentials required by optional Gradle tasks.

Secrets are stored **outside the Git repository** and are never committed to source control.

On Windows, local secrets are protected using Windows DPAPI and stored under:

```text
%LOCALAPPDATA%\ExampleLearning\secrets
```

Example:

```text
C:\Users\<username>\AppData\Local\ExampleLearning\secrets\
├── AZURE_TRANSLATOR_KEY.xml
├── GCP_TRANSLATOR_KEY.xml
└── OPENAI_API_KEY.xml
```

The encrypted secret files are tied to the Windows user and machine that created them.

---

## Secret Naming Convention

Secret names must use uppercase environment-variable format:

```text
[A-Z][A-Z0-9_]*
```

Recommended examples:

```text
AZURE_TRANSLATOR_KEY
GCP_TRANSLATOR_KEY
OPENAI_API_KEY
```

Invalid examples:

```text
azure-key
AzureTranslatorKey
my secret
../secret
```

Using environment-variable-style names allows the same secret names to be reused later in CI/CD, Docker, or other deployment environments.

---

# First-Time Setup

When a Gradle task requires a secret, add it using:

```powershell
.\scripts\secrets\set-secret.ps1
```

The script first asks for the secret name:

```text
Secret name (example: AZURE_TRANSLATOR_KEY):
```

Example:

```text
AZURE_TRANSLATOR_KEY
```

It then asks for the secret value:

```text
Secret value:
```

The value is hidden while typing.

The secret is encrypted and stored outside the project repository.

After this setup, you do **not** need to enter the API key again when running Gradle tasks.

---

# Running Gradle Tasks

After the secret has been configured, Gradle tasks can be executed normally.

From IntelliJ:

```text
Gradle
└── Tasks
    └── ...
        └── translateMarkdown
            └── Run
```

Or from the command line:

```powershell
.\gradlew translateMarkdown
```

No additional PowerShell wrapper is required.

The Gradle task automatically resolves the required secret when it runs.

---

# Add a Secret

Run:

```powershell
.\scripts\secrets\set-secret.ps1
```

Enter the secret name:

```text
GCP_TRANSLATOR_KEY
```

Then enter its value.

The new secret will be stored locally and can immediately be used by Gradle tasks.

---

# List Configured Secrets

To see which secrets are currently configured:

```powershell
.\scripts\secrets\list-secrets.ps1
```

Example output:

```text
==========================================
 Local Secrets
==========================================

Configured secrets:

  - AZURE_TRANSLATOR_KEY
  - GCP_TRANSLATOR_KEY
  - OPENAI_API_KEY

Total: 3
```

Only secret names are displayed.

Secret values are never printed.

---

# Update or Replace a Secret

If an API key is:

* expired
* regenerated
* rotated
* revoked
* replaced
* no longer valid

you do **not** need to delete the old secret first.

Run:

```powershell
.\scripts\secrets\set-secret.ps1
```

Enter the existing secret name:

```text
AZURE_TRANSLATOR_KEY
```

The script will detect that the secret already exists:

```text
Secret already exists:

  AZURE_TRANSLATOR_KEY

Replace existing secret? (y/N):
```

Enter:

```text
y
```

Then provide the new value.

The existing encrypted secret will be replaced.

The new value will automatically be used the next time the Gradle task runs.

You do not need to:

* restart Windows
* restart IntelliJ
* reload Gradle
* rebuild the project

---

# Remove a Secret

To remove a local secret:

```powershell
.\scripts\secrets\remove-secret.ps1
```

Enter the secret name:

```text
AZURE_TRANSLATOR_KEY
```

The script will ask for confirmation:

```text
Remove this secret? (y/N):
```

Enter:

```text
y
```

The corresponding encrypted secret file will be deleted.

---

# Missing Secret

If a Gradle task requires a secret that has not been configured, it should report an error similar to:

```text
Azure Translator secret is not configured.

Required secret:

AZURE_TRANSLATOR_KEY
```

Configure the missing secret with:

```powershell
.\scripts\secrets\set-secret.ps1
```

Then run the Gradle task again.

---

# Invalid API Key

A configured secret may still contain an invalid or outdated API key.

For example, Azure Translator may return:

```text
401 Unauthorized
```

Possible causes include:

* the API key is invalid
* the API key was regenerated
* the API key was rotated
* the API key was revoked
* the key belongs to another resource
* the configured service region is incorrect

Obtain the current API key and replace the local secret:

```powershell
.\scripts\secrets\set-secret.ps1
```

Enter the existing secret name and replace its value.

---

# API Quota and Permission Errors

Not every API error means that the secret is incorrect.

For example:

```text
403 Forbidden
```

may indicate:

* subscription restrictions
* missing permissions
* disabled resource
* exhausted free/trial quota
* incorrect service configuration

And:

```text
429 Too Many Requests
```

usually indicates:

* rate limit exceeded
* request quota exceeded
* subscription quota exceeded

Replacing the API key may not resolve these errors.

Check the corresponding cloud provider's subscription, quota, permissions, and resource configuration.

---

# Secret Resolution Order

Gradle resolves secrets using the following order:

```text
1. Process environment variable
2. Windows local DPAPI secret
```

Example:

```text
AZURE_TRANSLATOR_KEY
```

If the environment variable exists, it is used first.

If it does not exist and the build is running on Windows, Gradle attempts to load:

```text
%LOCALAPPDATA%\ExampleLearning\secrets\AZURE_TRANSLATOR_KEY.xml
```

This allows the same Gradle code to work across different environments.

---

# Local Development

For local Windows development:

```text
Gradle
    ↓
Environment variable?
    ↓ no
Windows DPAPI local secret
    ↓
API key
```

The secret only needs to be configured once per Windows user and machine.

---

# CI/CD

CI/CD environments should **not** use the local Windows DPAPI secret files.

Instead, configure secrets using the secret-management feature provided by the CI/CD platform.

For example:

```text
AZURE_TRANSLATOR_KEY
GCP_TRANSLATOR_KEY
OPENAI_API_KEY
```

should be injected into the Gradle process environment.

Example conceptual flow:

```text
CI Secret Store
      ↓
Environment Variable
      ↓
Gradle
      ↓
SecretProvider
```

Because environment variables have higher priority, no Windows-specific secret storage is required in CI.

---

# Docker

Do not copy local secret files into Docker images.

Never add:

```text
%LOCALAPPDATA%\ExampleLearning\secrets
```

to the Docker build context.

Secrets required during Docker builds should be supplied through the secret mechanism provided by Docker or the build platform.

The final Docker image should not contain API keys unless the application explicitly requires them at runtime.

---

# Moving to Another Computer

Local DPAPI secret files should not be copied to another computer.

For example, do not copy:

```text
AZURE_TRANSLATOR_KEY.xml
```

from one machine to another.

The encrypted value is associated with the Windows user and machine that created it.

On a new computer, configure the secret again:

```powershell
.\scripts\secrets\set-secret.ps1
```

This setup is required once for each Windows user/machine.

---

# New Developer Setup

After cloning the repository, a new developer should:

1. Build or reload the Gradle project normally.
2. Run only the Gradle task they need.
3. If the task reports a missing secret, identify the required secret name.
4. Obtain the API key from the project maintainer or create their own cloud resource.
5. Run:

```powershell
.\scripts\secrets\set-secret.ps1
```

6. Enter the requested secret name and value.
7. Run the Gradle task again.

Example:

```text
Clone repository
       ↓
Run translateMarkdown
       ↓
Missing AZURE_TRANSLATOR_KEY
       ↓
set-secret.ps1
       ↓
Enter AZURE_TRANSLATOR_KEY
       ↓
Enter API key
       ↓
Run translateMarkdown again
       ↓
Success
```

The developer only needs to configure that secret once.

---

# Available Commands

## Add or Update

```powershell
.\scripts\secrets\set-secret.ps1
```

## List

```powershell
.\scripts\secrets\list-secrets.ps1
```

## Remove

```powershell
.\scripts\secrets\remove-secret.ps1
```

---

# Security Rules

Never commit API keys or secret values to Git.

Do not place secret values inside:

```text
build.gradle
gradle.properties
application.yml
README.md
source code
Dockerfile
```

Do not print secret values in:

```text
Gradle logs
CI logs
application logs
exception messages
```

Only secret names such as:

```text
AZURE_TRANSLATOR_KEY
```

should appear in source code or documentation.

Secret values must remain outside the repository.

---

# Summary

Local Windows development:

```text
set-secret.ps1
      ↓
Windows DPAPI
      ↓
local encrypted secret
      ↓
Gradle task
```

CI/CD:

```text
CI Secret Store
      ↓
environment variable
      ↓
Gradle task
```

Docker:

```text
Docker / Platform Secret
      ↓
temporary secret injection
      ↓
Gradle or application
```

The application and Gradle build logic only need to know the **secret name**.

They do not need to know where the actual secret value is stored.
