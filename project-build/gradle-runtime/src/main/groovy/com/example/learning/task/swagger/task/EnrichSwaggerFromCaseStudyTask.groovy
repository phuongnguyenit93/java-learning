package com.example.learning.task.swagger.task

import com.example.learning.task.swagger.service.SwaggerCaseStudyEnrichmentService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = 'Merges authored Swagger YAML with Case Study')
abstract class EnrichSwaggerFromCaseStudyTask extends DefaultTask {
    @Input
    abstract ListProperty<String> getLanguages()

    @TaskAction
    void enrich() {
        String mode = (project.findProperty('swaggerEnrichMode') ?: 'DRY_RUN')
                .toString().trim().toUpperCase(Locale.ROOT)
        if (!(mode in ['DRY_RUN', 'APPLY'])) {
            throw new GradleException('swaggerEnrichMode must be DRY_RUN or APPLY')
        }
        Map result = new SwaggerCaseStudyEnrichmentService().execute(
                project.projectDir, languages.get(), mode == 'APPLY')
        logger.lifecycle('[SWAGGER-CASE-STUDY] {} filesChanged={} languages={}',
                result.mode, result.filesChanged, result.languages)
    }
}
