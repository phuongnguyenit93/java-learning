package com.example.learning.module.dependency.extension

import org.gradle.api.provider.Property


abstract class ImplementationModuleExtension {

    abstract Property<String> getServiceList()
}