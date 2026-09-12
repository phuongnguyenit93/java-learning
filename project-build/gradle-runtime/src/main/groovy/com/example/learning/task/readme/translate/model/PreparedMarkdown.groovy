package com.example.learning.task.readme.translate.model

class PreparedMarkdown {

    final String source
    final List<TranslationSpan> spans


    PreparedMarkdown(
            String source,
            List<TranslationSpan> spans
    ) {

        this.source = source
        this.spans =
                Collections.unmodifiableList(
                        new ArrayList<>(spans)
                )
    }
}