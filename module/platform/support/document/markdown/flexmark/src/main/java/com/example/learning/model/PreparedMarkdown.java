package com.example.learning.model;

import com.vladsch.flexmark.formatter.TranslationHandler;
import com.vladsch.flexmark.util.ast.Document;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PreparedMarkdown {
    private final Document document;
    private final TranslationHandler translationHandler;
    private final List<String> texts;
}
