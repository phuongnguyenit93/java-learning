package com.example.learning.setup.module.readme.service

import org.gradle.api.logging.Logger

import java.util.regex.Matcher
import java.util.regex.Pattern


class ReadmeKnowledgeParser {

    private static final Pattern SECTION_CANDIDATE_PATTERN =
            Pattern.compile(
                    '(?m)^##\\s+<a\\b[^\\r\\n]*$'
            )


    private static final Pattern SECTION_PATTERN =
            Pattern.compile(
                    '^##\\s+' +
                            '<a\\s+id\\s*=\\s*["\']([^"\']*)["\']\\s*>' +
                            '(.*?)' +
                            '</a>\\s*$'
            )


    private final Logger logger


    ReadmeKnowledgeParser(
            Logger logger
    ) {

        this.logger =
                logger
    }


    List<SectionCandidate> parse(
            File markdownFile
    ) {

        return parse(
                markdownFile,
                normalizeLineEnding(
                        markdownFile.getText(
                                'UTF-8'
                        )
                )
        )
    }


    List<SectionCandidate> parse(
            File markdownFile,
            String content
    ) {

        Matcher matcher =
                SECTION_CANDIDATE_PATTERN.matcher(
                        content
                )


        List<SectionCandidate> result = []


        while (matcher.find()) {

            String line =
                    matcher.group()


            Matcher sectionMatcher =
                    SECTION_PATTERN.matcher(
                            line
                    )


            if (!sectionMatcher.matches()) {

                warnMalformedSection(
                        markdownFile,
                        line
                )


                result.add(
                        SectionCandidate.invalid(
                                matcher.start(),
                                matcher.end()
                        )
                )


                continue
            }


            String id =
                    sectionMatcher
                            .group(1)
                            ?.trim()


            String title =
                    sectionMatcher
                            .group(2)
                            ?.trim()


            if (
                    id == null ||
                            id.isBlank() ||
                            title == null ||
                            title.isBlank()
            ) {

                warnMalformedSection(
                        markdownFile,
                        line
                )


                result.add(
                        SectionCandidate.invalid(
                                matcher.start(),
                                matcher.end()
                        )
                )


                continue
            }


            result.add(
                    SectionCandidate.valid(
                            matcher.start(),
                            matcher.end(),
                            id,
                            title
                    )
            )
        }


        return result
    }


    private void warnMalformedSection(
            File markdownFile,
            String line
    ) {

        logger.warn(
                '[README-KNOWLEDGE] Skip malformed section heading in {}: {}',
                markdownFile.absolutePath,
                line
        )
    }


    static String normalizeLineEnding(
            String content
    ) {

        return content
                .replace(
                        '\r\n',
                        '\n'
                )
                .replace(
                        '\r',
                        '\n'
                )
    }


    static class SectionCandidate {

        final int start
        final int end
        final boolean valid
        final String id
        final String title


        private SectionCandidate(
                int start,
                int end,
                boolean valid,
                String id,
                String title
        ) {

            this.start = start
            this.end = end
            this.valid = valid
            this.id = id
            this.title = title
        }


        static SectionCandidate valid(
                int start,
                int end,
                String id,
                String title
        ) {

            return new SectionCandidate(
                    start,
                    end,
                    true,
                    id,
                    title
            )
        }


        static SectionCandidate invalid(
                int start,
                int end
        ) {

            return new SectionCandidate(
                    start,
                    end,
                    false,
                    null,
                    null
            )
        }
    }
}
