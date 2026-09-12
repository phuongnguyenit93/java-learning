package com.example.learning.task.swagger.extension

import org.gradle.api.provider.ListProperty

abstract class GenerateApiSwaggerDescriptionExtension {
    abstract ListProperty<String> getLanguages()
}