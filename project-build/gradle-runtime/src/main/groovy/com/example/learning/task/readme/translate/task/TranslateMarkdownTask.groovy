package com.example.learning.task.readme.translate.task

import com.example.learning.task.readme.translate.config.TranslationProvider
import com.example.learning.task.readme.translate.model.PreparedMarkdown
import com.example.learning.task.readme.translate.service.markdown.FlexmarkMarkdownService
import com.example.learning.task.readme.translate.service.translator.TranslationServiceFactory
import com.example.learning.task.readme.translate.service.translator.TranslationServiceInterface
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault


@DisableCachingByDefault(
        because = 'Calls external translation service'
)
abstract class TranslateMarkdownTask
        extends DefaultTask {


    // ========================================================
    // Translation configuration
    // ========================================================

    @Input
    abstract Property<String> getInputLanguage()

    @Input
    abstract Property<String> getOutputLanguage()

    @Input
    abstract Property<String> getFileLocation()

    @Input
    abstract Property<TranslationProvider> getProvider()


    // ========================================================
    // Resolved files
    // ========================================================

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getInputFile()


    @OutputFile
    abstract RegularFileProperty getOutputFile()


    // ========================================================
    // Task
    // ========================================================

    @TaskAction
    void translate() {
        File input = inputFile.get().asFile
        File output = outputFile.get().asFile

        logTranslationInformation(input, output)
        validateInput(input)

        // ====================================================
        // 1. Create Markdown processor
        // ====================================================
        FlexmarkMarkdownService markdownProcessor = new FlexmarkMarkdownService()

        // ====================================================
        // 2. Create Translation service
        // ====================================================
        TranslationServiceInterface translationService =
                TranslationServiceFactory.create(provider.get())

        // ====================================================
        // 3. Read source Markdown
        // ====================================================
        String markdown = input.getText('UTF-8')

        // ====================================================
        // 4. Prepare Markdown
        // ====================================================

        PreparedMarkdown prepared = markdownProcessor.prepare(markdown)
        logger.lifecycle('Found {} translation spans', prepared.spans.size())

        if (prepared.spans.isEmpty()) {
            writeOutput(output, markdown)
            logger.lifecycle('No translatable text found.')
            return
        }

        // ====================================================
        // 5. Extract texts
        // ====================================================

        List<String> sourceTexts = prepared.spans.collect {it.text}

        // ====================================================
        // 6. Translate
        // ====================================================

        List<String> translatedTexts = translationService.translate(sourceTexts, inputLanguage.get(), outputLanguage.get())
        validateTranslationResult(sourceTexts, translatedTexts)


        // ====================================================
        // 7. Restore translated Markdown
        // ====================================================

        String translatedMarkdown = markdownProcessor.render(prepared, translatedTexts)

        // ====================================================
        // 8. Write output
        // ====================================================

        writeOutput(output, translatedMarkdown)
        logCompleted(output)
    }

    // ========================================================
    // Validation
    // ========================================================

    private void validateInput(File input) {
        if (!input.exists()) {

            throw new GradleException(
                    """
Markdown input file does not exist.

Project:
${project.path}

Input language:
${inputLanguage.get()}

Output language:
${outputLanguage.get()}

File location:
${fileLocation.get()}

Resolved input:
${input.absolutePath}
"""
            )
        }


        if (!input.isFile()) {

            throw new GradleException(
                    """
Markdown input is not a file:

${input.absolutePath}
"""
            )
        }
    }


    private void validateTranslationResult(List<String> sourceTexts, List<String> translatedTexts) {
        if (translatedTexts == null) {

            throw new GradleException(
                    """
Translation provider returned null.

Provider:
${provider.get()}
"""
            )
        }


        if (
                sourceTexts.size()
                        != translatedTexts.size()
        ) {

            throw new GradleException(
                    """
Translation result count mismatch.

Provider:
${provider.get()}

Expected:
${sourceTexts.size()}

Actual:
${translatedTexts.size()}
"""
            )
        }
    }

    // ========================================================
    // Output
    // ========================================================

    private void writeOutput(File output, String content) {

        File parent = output.parentFile
        if (parent != null) {
            parent.mkdirs()
        }
        output.setText(content, 'UTF-8')
    }



    // ========================================================
    // Logging
    // ========================================================

    private void logTranslationInformation(File input, File output) {
        logger.lifecycle('')
        logger.lifecycle('==========================================')
        logger.lifecycle(' Markdown Translation')
        logger.lifecycle('==========================================')
        logger.lifecycle('Project  : {}', project.path)
        logger.lifecycle('Provider : {}', provider.get())
        logger.lifecycle('From     : {}', inputLanguage.get())
        logger.lifecycle('To       : {}', outputLanguage.get())
        logger.lifecycle('File     : {}', fileLocation.get())
        logger.lifecycle('Input    : {}', input.absolutePath)
        logger.lifecycle('Output   : {}', output.absolutePath)
        logger.lifecycle('==========================================')
        logger.lifecycle('')
    }

    private void logCompleted(File output) {
        logger.lifecycle('')
        logger.lifecycle('==========================================')
        logger.lifecycle(' TRANSLATION COMPLETED')
        logger.lifecycle('==========================================')
        logger.lifecycle('Provider : {}', provider.get())
        logger.lifecycle('Output   : {}', output.absolutePath)
        logger.lifecycle('==========================================')
        logger.lifecycle('')
    }
}