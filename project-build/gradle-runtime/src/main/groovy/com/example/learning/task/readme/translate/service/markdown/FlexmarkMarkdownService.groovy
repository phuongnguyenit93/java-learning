package com.example.learning.task.readme.translate.service.markdown

import com.example.learning.task.readme.translate.model.PreparedMarkdown
import com.example.learning.task.readme.translate.model.TranslationSpan
import com.vladsch.flexmark.ast.Text
import com.vladsch.flexmark.parser.Parser

class FlexmarkMarkdownService {

    private final Parser parser

    FlexmarkMarkdownService() {
        this.parser = Parser.builder().build()
    }


    PreparedMarkdown prepare(
            String markdown
    ) {

        def document =
                parser.parse(markdown)


        List<TranslationSpan> spans = []


        collectTextNodes(
                document,
                markdown,
                spans
        )


        return new PreparedMarkdown(
                markdown,
                spans
        )
    }


    String render(
            PreparedMarkdown prepared,
            List<String> translatedTexts
    ) {

        if (
                prepared.spans.size()
                        != translatedTexts.size()
        ) {

            throw new IllegalArgumentException(
                    """
Translation count mismatch.

Expected:
${prepared.spans.size()}

Actual:
${translatedTexts.size()}
"""
            )
        }


        StringBuilder result =
                new StringBuilder(
                        prepared.source
                )


        /*
         * Replace từ cuối -> đầu để offset
         * của các span trước đó không bị thay đổi.
         */

        List<Integer> indexes =
                (0..<prepared.spans.size())
                        .toList()


        indexes
                .sort { a, b ->

                    prepared.spans[b].start <=>
                            prepared.spans[a].start
                }
                .each { index ->

                    TranslationSpan span =
                            prepared.spans[index]


                    result.replace(
                            span.start,
                            span.end,
                            translatedTexts[index]
                    )
                }


        return result.toString()
    }


    private void collectTextNodes(
            def node,
            String markdown,
            List<TranslationSpan> spans
    ) {

        def child =
                node.firstChild


        while (child != null) {

            if (child instanceof Text) {

                addTextSpan(
                        child,
                        markdown,
                        spans
                )
            }


            collectTextNodes(
                    child,
                    markdown,
                    spans
            )


            child =
                    child.next
        }
    }


    private void addTextSpan(
            Text node,
            String markdown,
            List<TranslationSpan> spans
    ) {

        int start =
                node.startOffset

        int end =
                node.endOffset


        if (
                start < 0 ||
                        end <= start ||
                        end > markdown.length()
        ) {
            return
        }


        String source =
                markdown.substring(
                        start,
                        end
                )


        /*
         * Không translate đoạn không chứa chữ.
         *
         * Ví dụ:
         * -
         * ...
         * 123
         */
        if (!(source =~ /\p{L}/).find()) {
            return
        }


        int leading = 0


        while (
                leading < source.length() &&
                        Character.isWhitespace(
                                source.charAt(leading)
                        )
        ) {

            leading++
        }


        int trailing =
                source.length()


        while (
                trailing > leading &&
                        Character.isWhitespace(
                                source.charAt(trailing - 1)
                        )
        ) {

            trailing--
        }


        if (leading >= trailing) {
            return
        }


        spans.add(
                new TranslationSpan(
                        start + leading,
                        start + trailing,
                        source.substring(
                                leading,
                                trailing
                        )
                )
        )
    }
}