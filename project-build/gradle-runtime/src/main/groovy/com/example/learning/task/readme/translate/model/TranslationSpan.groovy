package com.example.learning.task.readme.translate.model

class TranslationSpan {

    final int start
    final int end
    final String text

    TranslationSpan(
            int start,
            int end,
            String text
    ) {

        this.start = start
        this.end = end
        this.text = text
    }
}