package com.example.learning.service;

import com.example.learning.model.PreparedMarkdown;
import com.vladsch.flexmark.formatter.Formatter;
import com.vladsch.flexmark.formatter.RenderPurpose;
import com.vladsch.flexmark.formatter.TranslationHandler;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FlexmarkTranslationService implements FlexmarkTranslationInterface {
    private final Parser parser;
    private final Formatter formatter;

    public FlexmarkTranslationService() {

        MutableDataSet options = new MutableDataSet()
                .set(Parser.BLANK_LINES_IN_AST, true)
                .set(Parser.HTML_FOR_TRANSLATOR, true)
                .set(Parser.PARSE_INNER_HTML_COMMENTS, true)
                .set(Formatter.MAX_TRAILING_BLANK_LINES, 0);

        this.parser = Parser.builder(options).build();
        this.formatter = Formatter.builder(options).build();
    }

    @Override
    public PreparedMarkdown prepare(String markdown) {

        if (markdown == null || markdown.isBlank()) {
            throw new IllegalArgumentException(
                    "Markdown cannot be empty"
            );
        }

        // Parse Markdown
        Document document = parser.parse(markdown);

        // Translation context riêng cho document này
        TranslationHandler handler = formatter.getTranslationHandler();

        // Flexmark tìm các span cần dịch
        formatter.translationRender(document, handler, RenderPurpose.TRANSLATION_SPANS);

        List<String> texts =
                new ArrayList<>(
                        handler.getTranslatingTexts()
                );

        return new PreparedMarkdown(
                document,
                handler,
                texts
        );
    }

    @Override
    public String render(PreparedMarkdown prepared, List<String> translatedTexts) {

        if (prepared == null) {
            throw new IllegalArgumentException(
                    "Prepared markdown cannot be null"
            );
        }

        if (translatedTexts == null) {
            throw new IllegalArgumentException(
                    "Translated texts cannot be null"
            );
        }

        if (prepared.getTexts().size()
                != translatedTexts.size()) {

            throw new IllegalArgumentException(
                    "Translated text count mismatch. Expected "
                            + prepared.getTexts().size()
                            + " but got "
                            + translatedTexts.size()
            );
        }

        TranslationHandler handler =
                prepared.getTranslationHandler();

        List<CharSequence> translated =
                new ArrayList<>(translatedTexts);

        handler.setTranslatedTexts(translated);

        // Markdown trung gian với translation
        String partial =
                formatter.translationRender(
                        prepared.getDocument(),
                        handler,
                        RenderPurpose.TRANSLATED_SPANS
                );

        // Parse lại
        Node partialDocument =
                parser.parse(partial);

        // Restore URL, code, placeholder...
        return formatter.translationRender(
                partialDocument,
                handler,
                RenderPurpose.TRANSLATED
        );
    }
}
