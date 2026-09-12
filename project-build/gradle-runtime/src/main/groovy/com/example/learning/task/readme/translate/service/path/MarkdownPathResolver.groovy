package com.example.learning.task.readme.translate.service.path

import java.nio.file.Path

class MarkdownPathResolver {

    private static final String README_DIRECTORY =
            'readme'


    File resolve(
            File projectDir,
            String language,
            String fileLocation
    ) {

        String normalizedLocation =
                normalizeFileLocation(
                        fileLocation
                )


        return new File(
                projectDir,
                "${README_DIRECTORY}/${language}/${normalizedLocation}"
        )
    }


    private String normalizeFileLocation(
            String value
    ) {

        if (
                value == null ||
                        value.isBlank()
        ) {

            throw new IllegalArgumentException(
                    'fileLocation must not be empty'
            )
        }


        String normalized =
                value
                        .trim()
                        .replace(
                                '\\',
                                '/'
                        )


        while (
                normalized.startsWith('/')
        ) {

            normalized =
                    normalized.substring(1)
        }


        Path path =
                Path.of(
                        normalized
                )
                        .normalize()


        if (
                path.isAbsolute() ||
                        path.startsWith('..')
        ) {

            throw new IllegalArgumentException(
                    """
Invalid Markdown file location:

${value}

fileLocation must be relative to:

readme/{language}/
"""
            )
        }


        return path
                .toString()
                .replace(
                        '\\',
                        '/'
                )
    }
}