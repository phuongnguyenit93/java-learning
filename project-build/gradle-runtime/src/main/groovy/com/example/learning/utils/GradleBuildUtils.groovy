package com.example.learning.utils

import org.gradle.api.Project

class GradleBuildUtils {
    // ========================================================
    // Directory
    // ========================================================
    static File findBaseDirectory(
            Project baseProject
    ) {

        File resourceDirectory = new File(baseProject.projectDir, 'src/main/resources')

        if (resourceDirectory.exists()) {
            return resourceDirectory
        }

        return baseProject.projectDir
    }

    static String normalizePath(
            String path
    ) {
        return path.replace(File.separator, '/')
    }
}
