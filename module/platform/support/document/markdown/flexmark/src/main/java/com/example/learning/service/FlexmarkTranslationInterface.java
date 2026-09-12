package com.example.learning.service;

import com.example.learning.model.PreparedMarkdown;

import java.util.List;

public interface FlexmarkTranslationInterface {
    PreparedMarkdown prepare(String markdown);

    String render(
            PreparedMarkdown prepared,
            List<String> translatedTexts
    );
}
