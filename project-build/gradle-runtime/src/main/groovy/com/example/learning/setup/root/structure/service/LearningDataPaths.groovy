package com.example.learning.setup.root.structure.service

/**
 * Build-only Java learning data shared by every Portal projection.
 * The consumer repository publishes/deploys these files separately.
 */
final class LearningDataPaths {

    static final String ROOT_DIRECTORY = 'build/learning-data/java'
    static final String MODULE_DIRECTORY = "${ROOT_DIRECTORY}/module"
    static final String CATALOG_FILE = "${ROOT_DIRECTORY}/module-catalog.json"

    private LearningDataPaths() {
    }
}
