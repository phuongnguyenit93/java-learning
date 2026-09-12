package com.example.learning.task.intellij.service

import groovy.text.SimpleTemplateEngine
import groovy.xml.XmlUtil

class IntellijConfigTemplateService {

    void generate(
            String templateContent,
            File outputFile,
            Map<String, ?> binding
    ) {

        if (!templateContent?.trim()) {
            throw new IllegalArgumentException(
                    'IntelliJ run config template content must not be blank.'
            )
        }

        // ==========================================
        // Escape XML values
        // ==========================================

        Map<String, String> xmlBinding =
                binding.collectEntries { key, value ->

                    String escapedValue =
                            XmlUtil.escapeXml(
                                    value?.toString() ?: ''
                            )

                    [
                            key.toString(),
                            escapedValue
                    ]
                }

        // ==========================================
        // Render
        // ==========================================

        String output =
                new SimpleTemplateEngine()
                        .createTemplate(templateContent)
                        .make(xmlBinding)
                        .toString()

        // ==========================================
        // Output directory
        // ==========================================

        File outputDirectory =
                outputFile.parentFile

        if (!outputDirectory.exists()) {

            boolean created =
                    outputDirectory.mkdirs()

            if (!created) {
                throw new IllegalStateException(
                        """
Unable to create IntelliJ run configuration directory:

${outputDirectory.absolutePath}
"""
                )
            }
        }

        // ==========================================
        // Write
        // ==========================================

        outputFile.setText(
                output,
                'UTF-8'
        )
    }
}