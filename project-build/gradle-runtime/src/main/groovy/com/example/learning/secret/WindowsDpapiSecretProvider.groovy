package com.example.learning.secret

import java.nio.charset.StandardCharsets

class WindowsDpapiSecretProvider
        implements SecretProvider {

    private static final String SECRET_DIRECTORY =
            'ExampleLearning/secrets'


    @Override
    String findSecret(
            String name
    ) {

        if (!isWindows()) {
            return null
        }


        String localAppData =
                System.getenv(
                        'LOCALAPPDATA'
                )


        if (
                localAppData == null ||
                        localAppData.isBlank()
        ) {

            return null
        }


        File secretDirectory =
                new File(
                        localAppData,
                        SECRET_DIRECTORY
                )


        File secretFile =
                new File(
                        secretDirectory,
                        "${name}.xml"
                )


        if (!secretFile.exists()) {
            return null
        }


        return decrypt(
                secretFile
        )
    }


    private static String decrypt(
            File secretFile
    ) {

        String script =
                '''
$ErrorActionPreference = 'Stop'

try {

    $secure =
        Import-Clixml `
            -LiteralPath $env:EXAMPLE_LEARNING_SECRET_FILE

    $pointer =
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR(
            $secure
        )

    try {

        $plainText =
            [Runtime.InteropServices.Marshal]::PtrToStringBSTR(
                $pointer
            )

        [Console]::Out.Write(
            $plainText
        )

    }
    finally {

        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR(
            $pointer
        )
    }

}
catch {

    [Console]::Error.Write(
        $_.Exception.Message
    )

    exit 1
}
'''


        String encodedScript =
                Base64.getEncoder()
                        .encodeToString(
                                script.getBytes(
                                        StandardCharsets.UTF_16LE
                                )
                        )


        ProcessBuilder processBuilder =
                new ProcessBuilder(
                        'powershell.exe',
                        '-NoProfile',
                        '-NonInteractive',
                        '-EncodedCommand',
                        encodedScript
                )


        processBuilder.environment().put(
                'EXAMPLE_LEARNING_SECRET_FILE',
                secretFile.absolutePath
        )


        Process process =
                processBuilder.start()


        String standardOutput =
                process.inputStream
                        .getText('UTF-8')


        String errorOutput =
                process.errorStream
                        .getText('UTF-8')


        int exitCode =
                process.waitFor()


        if (exitCode != 0) {

            throw new IllegalStateException(
                    """
Unable to decrypt local secret:

${secretFile.absolutePath}

PowerShell:

${errorOutput.trim()}

The secret may belong to another Windows user
or another computer.

Run:

.\\scripts\\secrets\\set-secret.ps1
"""
            )
        }


        String secret =
                standardOutput.trim()


        if (secret.isBlank()) {
            return null
        }


        return secret
    }


    private static boolean isWindows() {

        return System.getProperty(
                'os.name'
        )
                .toLowerCase()
                .contains('windows')
    }
}