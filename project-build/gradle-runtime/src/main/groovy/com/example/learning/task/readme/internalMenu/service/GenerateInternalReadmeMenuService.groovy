package com.example.learning.task.readme.internalMenu.service

import java.util.regex.Matcher
import java.util.regex.Pattern

class GenerateInternalReadmeMenuService {

    private static final String BACK_TO_TOP_ID =
            'back-to-top'


    private static final String BACK_TO_TOP_ANCHOR =
            '<a id="back-to-top"></a>'


    private static final String DETAILS_OPEN =
            '''<details>
<summary>Click for details</summary>'''


    private static final String DETAILS_CLOSE =
            '</details>'


    private static final String BACK_TO_TOP_LINK =
            '- [Quay lại đầu trang](#back-to-top)'


    /*
     * Hỗ trợ:
     *
     * <a id="thread-basic">Thread Basic</a>
     *
     * hoặc:
     *
     * ## <a id="thread-basic">Thread Basic</a>
     */
    private static final Pattern SECTION_PATTERN =
            Pattern.compile(
                    '(?m)^(##\\s+)?' +
                            '(<a\\s+id\\s*=\\s*"([^"]+)"\\s*>(.+?)</a>)' +
                            '\\s*$'
            )


    /*
     * Menu do generator tạo.
     */
    private static final Pattern MENU_PATTERN =
            Pattern.compile(
                    '(?ms)' +
                            '^## Menu\\s*\\n' +
                            '(?:' +
                            '- \\[[^\\n]*\\]\\(#[^\\n)]*\\)' +
                            '\\s*\\n?' +
                            ')*'
            )


    private static final Pattern BACK_TO_TOP_ANCHOR_PATTERN =
            Pattern.compile(
                    '(?m)^\\s*' +
                            '<a\\s+id\\s*=\\s*"back-to-top"\\s*>' +
                            '\\s*</a>' +
                            '\\s*$'
            )


    private static final Pattern DETAILS_OPEN_PATTERN =
            Pattern.compile(
                    '(?s)^\\s*' +
                            '<details>\\s*' +
                            '<summary>Click for details</summary>' +
                            '\\s*'
            )


    private static final Pattern DETAILS_CLOSE_PATTERN =
            Pattern.compile(
                    '(?s)' +
                            '\\s*</details>\\s*' +
                            '(?:' +
                            '- \\[Quay lại đầu trang\\]' +
                            '\\(#back-to-top\\)' +
                            '\\s*' +
                            ')?' +
                            '(?:---\\s*)?' +
                            '$'
            )


    void generate(
            File markdownFile
    ) {

        String content =
                markdownFile.getText(
                        'UTF-8'
                )


        content =
                normalizeLineEnding(
                        content
                )


        ParsedDocument document =
                parseDocument(
                        content
                )


        String generatedContent =
                renderDocument(
                        document
                )


        markdownFile.setText(
                generatedContent,
                'UTF-8'
        )
    }


    private static ParsedDocument parseDocument(
            String content
    ) {

        Matcher matcher =
                SECTION_PATTERN.matcher(
                        content
                )


        List<SectionMatch> matches = []


        while (matcher.find()) {

            String id =
                    matcher.group(3)
                            ?.trim()


            /*
             * back-to-top không phải section.
             */
            if (
                    id == BACK_TO_TOP_ID
            ) {

                continue
            }


            matches.add(
                    new SectionMatch(
                            matcher.start(),
                            matcher.end(),
                            matcher.group(1) ?: '',
                            matcher.group(2),
                            id,
                            matcher.group(4)?.trim()
                    )
            )
        }


        if (matches.isEmpty()) {

            throw new IllegalStateException(
                    """
No internal README section anchors were found.

Expected format:

<a id="example-section">Example Section</a>
"""
            )
        }


        String introduction =
                content.substring(
                        0,
                        matches.first().start
                )


        introduction =
                cleanIntroduction(
                        introduction
                )


        List<InternalReadmeSection> sections = []


        matches.eachWithIndex {
            SectionMatch sectionMatch,
            int index ->

                int bodyStart =
                        sectionMatch.end


                int bodyEnd =
                        index + 1 < matches.size()
                                ? matches[index + 1].start
                                : content.length()


                String body =
                        content.substring(
                                bodyStart,
                                bodyEnd
                        )


                body =
                        cleanSectionBody(
                                body
                        )


                sections.add(
                        new InternalReadmeSection(
                                sectionMatch.headingPrefix,
                                sectionMatch.anchor,
                                sectionMatch.id,
                                sectionMatch.name,
                                body
                        )
                )
        }


        return new ParsedDocument(
                introduction,
                sections
        )
    }


    private static String cleanIntroduction(
            String content
    ) {

        String result =
                normalizeLineEnding(
                        content
                )


        /*
         * Xóa mọi back-to-top anchor cũ.
         */
        result =
                BACK_TO_TOP_ANCHOR_PATTERN
                        .matcher(
                                result
                        )
                        .replaceAll(
                                ''
                        )


        /*
         * Xóa menu đã generate trước đó.
         *
         * Nếu trước đây vì bug menu bị generate nhiều lần,
         * replaceAll sẽ xóa toàn bộ các block matching.
         */
        result =
                MENU_PATTERN
                        .matcher(
                                result
                        )
                        .replaceAll(
                                ''
                        )


        return normalizeWhitespace(
                result
        )
                .trim()
    }


    private static String cleanSectionBody(
            String content
    ) {

        String result =
                normalizeLineEnding(
                        content
                )


        /*
         * Một file cũ có thể từng bị generate lặp:
         *
         * <details>
         * <summary>...</summary>
         *
         * <details>
         * <summary>...</summary>
         *
         * ...
         *
         * Vì vậy không remove một lần,
         * mà remove cho đến khi không còn generated prefix.
         */
        while (
                DETAILS_OPEN_PATTERN
                        .matcher(
                                result
                        )
                        .find()
        ) {

            result =
                    DETAILS_OPEN_PATTERN
                            .matcher(
                                    result
                            )
                            .replaceFirst(
                                    ''
                            )
        }


        /*
         * Tương tự với phần đóng.
         *
         * Điều này xử lý luôn các file legacy
         * đã từng bị duplicate nhiều lần.
         */
        while (
                DETAILS_CLOSE_PATTERN
                        .matcher(
                                result
                        )
                        .find()
        ) {

            result =
                    DETAILS_CLOSE_PATTERN
                            .matcher(
                                    result
                            )
                            .replaceFirst(
                                    ''
                            )
        }


        return normalizeWhitespace(
                result
        )
                .trim()
    }


    private static String renderDocument(
            ParsedDocument document
    ) {

        String menu =
                buildMenu(
                        document.sections
                )


        String introduction =
                insertMenu(
                        document.introduction,
                        menu
                )


        StringBuilder output =
                new StringBuilder()


        output.append(
                BACK_TO_TOP_ANCHOR
        )

        output.append(
                '\n\n'
        )

        output.append(
                introduction.trim()
        )


        document.sections.eachWithIndex {
            InternalReadmeSection section,
            int index ->

                output.append(
                        '\n\n'
                )


                output.append(
                        section.headingPrefix
                )

                output.append(
                        section.anchor
                )


                output.append(
                        '\n\n'
                )

                output.append(
                        DETAILS_OPEN
                )


                if (!section.body.isBlank()) {

                    output.append(
                            '\n\n'
                    )

                    output.append(
                            section.body.trim()
                    )
                }


                output.append(
                        '\n\n'
                )

                output.append(
                        DETAILS_CLOSE
                )


                output.append(
                        '\n\n'
                )

                output.append(
                        BACK_TO_TOP_LINK
                )


                if (
                        index <
                                document.sections.size() - 1
                ) {

                    output.append(
                            '\n\n---'
                    )
                }
        }


        return normalizeWhitespace(
                output.toString()
        )
                .trim() +
                '\n'
    }


    private static String buildMenu(
            List<InternalReadmeSection> sections
    ) {

        List<String> menuLinks =
                sections.collect {
                    InternalReadmeSection section ->

                        "- [${section.name}](#${section.id})"
                }


        return '## Menu\n' +
                menuLinks.join(
                        '\n'
                )
    }


    private static String insertMenu(
            String introduction,
            String menu
    ) {

        Pattern titlePattern =
                Pattern.compile(
                        '(?m)^#\\s+.+$'
                )


        Matcher matcher =
                titlePattern.matcher(
                        introduction
                )


        if (!matcher.find()) {

            throw new IllegalStateException(
                    """
README title was not found.

Expected:

# README Title
"""
            )
        }


        String title =
                matcher.group()


        return introduction.substring(
                0,
                matcher.start()
        ) +
                title.trim() +
                '\n\n' +
                menu +
                introduction.substring(
                        matcher.end()
                )
    }


    private static String normalizeLineEnding(
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


    private static String normalizeWhitespace(
            String content
    ) {

        String result =
                normalizeLineEnding(
                        content
                )


        result =
                result.replaceAll(
                        /(?m)^[ \t]+$/,
                        ''
                )


        result =
                result.replaceAll(
                        /\n{3,}/,
                        '\n\n'
                )


        return result
    }


    private static class ParsedDocument {

        final String introduction

        final List<InternalReadmeSection> sections


        ParsedDocument(
                String introduction,
                List<InternalReadmeSection> sections
        ) {

            this.introduction =
                    introduction

            this.sections =
                    sections
        }
    }


    private static class SectionMatch {

        final int start

        final int end

        final String headingPrefix

        final String anchor

        final String id

        final String name


        SectionMatch(
                int start,
                int end,
                String headingPrefix,
                String anchor,
                String id,
                String name
        ) {

            this.start =
                    start

            this.end =
                    end

            this.headingPrefix =
                    headingPrefix

            this.anchor =
                    anchor

            this.id =
                    id

            this.name =
                    name
        }
    }


    private static class InternalReadmeSection {

        final String headingPrefix

        final String anchor

        final String id

        final String name

        final String body


        InternalReadmeSection(
                String headingPrefix,
                String anchor,
                String id,
                String name,
                String body
        ) {

            this.headingPrefix =
                    headingPrefix

            this.anchor =
                    anchor

            this.id =
                    id

            this.name =
                    name

            this.body =
                    body
        }
    }
}