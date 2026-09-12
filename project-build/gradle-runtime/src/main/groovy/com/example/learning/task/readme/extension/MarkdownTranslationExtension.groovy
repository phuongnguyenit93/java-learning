package com.example.learning.task.readme.extension

import org.gradle.api.provider.Property

abstract class MarkdownTranslationExtension {

    /**
     * Ví dụ:
     * vi
     */
    abstract Property<String> getInputLanguage()

    /**
     * Ví dụ:
     * en
     */
    abstract Property<String> getOutputLanguage()

    /**
     * Relative path bên trong:
     *
     * readme/{language}/
     *
     * Ví dụ:
     *
     * menu/1.Basic/Test.md
     */
    abstract Property<String> getFileLocation()

    abstract Property<String> getProvider()
}