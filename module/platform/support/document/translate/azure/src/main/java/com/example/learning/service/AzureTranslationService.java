package com.example.learning.service;

import com.azure.ai.translation.text.TextTranslationClient;
import com.azure.ai.translation.text.models.TranslateInputItem;
import com.azure.ai.translation.text.models.TranslatedTextItem;
import com.azure.ai.translation.text.models.TranslationTarget;
import com.example.learning.config.AzureTranslationClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AzureTranslationService implements AzureTranslationInterface {
    private final TextTranslationClient client;

    @Override
    public String translate(String text, String sourceLanguage, String targetLanguage) {
        if (text == null || text.isBlank()) {
            return text;
        }

        TranslateInputItem input =
                new TranslateInputItem(
                        text,
                        List.of(
                                new TranslationTarget(targetLanguage)
                        )
                );

        input.setLanguage(sourceLanguage);

        List<TranslatedTextItem> result = client.translate(List.of(input));

        if (result.isEmpty()
                || result.get(0).getTranslations().isEmpty()) {
            throw new IllegalStateException(
                    "Azure Translator returned empty result"
            );
        }

        return result
                .get(0)
                .getTranslations()
                .get(0)
                .getText();
    }
}
