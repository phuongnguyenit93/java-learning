package com.example.learning.task.readme.extension

import org.gradle.api.provider.ListProperty

abstract class GenerateFinalReadmeExtension {
    abstract ListProperty<String> getLanguages()
}