package com.example.learning.task.readme.extension

import org.gradle.api.provider.Property

abstract class GenerateInternalReadmeMenuExtension {

    abstract Property<String> getLocation()
}