package com.example.learning.task.sheets.service

import groovy.json.JsonSlurper
import org.gradle.api.GradleException

/** Read-only STEP checkpoint. Prime owns writes to master.json after QA. */
class ModuleCurrentStepService {
    static String read(File module) {
        File master = new File(module, 'master.json')
        if (!master.isFile()) throw new GradleException('Missing module master.json: ' + master)
        Object json = new JsonSlurper().parse(master)
        Object item = json instanceof Map ? json.CURRENT_STEP : null
        if (!(item instanceof Map) || item.TYPE != 'string' ||
                !(item.VALUE instanceof String) ||
                !(item.VALUE ==~ /(?:0|[1-9][0-9]*[A-Z]?|FINAL)/)) {
            throw new GradleException('Invalid master.json CURRENT_STEP string: ' + master)
        }
        return item.VALUE as String
    }
}
