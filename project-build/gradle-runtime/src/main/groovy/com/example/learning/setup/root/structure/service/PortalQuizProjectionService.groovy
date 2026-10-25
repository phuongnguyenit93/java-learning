package com.example.learning.setup.root.structure.service

import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.yaml.snakeyaml.Yaml

/**
 * Publish chapter-local Quiz YAML/index through the shared question projector.
 * Keep API-link diagnostics for existing authored questions.
 */
class PortalQuizProjectionService {
    private final Logger logger

    PortalQuizProjectionService(Logger logger) {
        this.logger = logger
    }

    void generate(Project rootProject) {
        Map<String, Map> apiCache = new LinkedHashMap<>()
        ChapterQuestionProjectionSupport.generate(rootProject, 'quiz', 'BUILD_QUIZ', logger) {
            Project module, String language, String questionId, Map question ->
                String cacheKey = "${module.projectDir.absolutePath}/${language}"
                Map descriptions = apiCache.computeIfAbsent(cacheKey) {
                    File api = new File(module.projectDir,
                            "src/main/resources/swagger/${language}/api-descriptions.yml")
                    if (!api.isFile()) return [:]
                    Object data = new Yaml().load(api.getText('UTF-8'))
                    return data instanceof Map ? data as Map : [:]
                }
                validateApiRelation(module, language, questionId,
                        question.apiRelated, descriptions)
        }
    }

    private void validateApiRelation(Project module, String language, String questionId,
                                     Object rawRelation, Map descriptions) {
        if (!(rawRelation instanceof Map)) return
        Map relation = rawRelation as Map
        String controller = (relation.controller ?: '').toString().trim()
        String method = (relation.methodSignature ?: '').toString().trim()
        if (controller.isBlank() && method.isBlank()) return

        String reason
        if (controller.isBlank() || method.isBlank()) {
            reason = 'both controller and methodSignature must be filled together'
        } else {
            Object entry = descriptions[controller]
            if (!(entry instanceof Map)) {
                reason = 'controller was not found in api-descriptions.yml'
            } else {
                Object methodEntry = (entry as Map)[method]
                if (!(methodEntry instanceof Map)) {
                    reason = 'method signature was not found in api-descriptions.yml'
                } else if ((methodEntry as Map).usage != true) {
                    reason = 'API exists but usage is not true, so it is not exposed in Portal API Docs'
                }
            }
        }
        if (reason != null) {
            String routeId = (module.findProperty('SERVICE_NAME') ?: module.projectDir.name).toString()
            logger.warn('[QUIZ-RELATION] Unresolved apiRelated for {}/{}/{}: controller={}, ' +
                            'methodSignature={}. Reason: {}.',
                    routeId, language, questionId, controller, method, reason)
        }
    }
}
