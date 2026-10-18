package com.example.learning.task.sheets.service

import org.gradle.api.GradleException

import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Explicit STEP 2 B17 -> content/readme/docs/vi/BASE.md synchronization. */
class FillIntroductionService {
    static Map<String, Object> sync(File repositoryRoot, File moduleDirectory,
                                    String introduction, boolean apply) {
        File root = repositoryRoot.canonicalFile
        File module = moduleDirectory.canonicalFile
        File modules = new File(root, 'module').canonicalFile
        if (!module.toPath().startsWith(modules.toPath()) || module == modules ||
                !new File(module, 'gradle.properties').isFile()) {
            throw new GradleException('Invalid module destination for fillIntroduction: ' + module)
        }
        File destination = new File(module, 'content/readme/docs/vi/BASE.md')
        if (!introduction?.trim()) {
            return [status: 'SKIPPED_EMPTY_INTRODUCTION', file: destination]
        }
        if (introduction.codePointCount(0, introduction.length()) > 30000) {
            throw new GradleException('Introduction B17 exceeds the 30000 Unicode character limit.')
        }
        File cursor = destination
        while (cursor != null && cursor.toPath().startsWith(module.toPath())) {
            if (Files.isSymbolicLink(cursor.toPath())) {
                throw new GradleException('Refusing symlink in Introduction destination: ' + cursor)
            }
            cursor = cursor.parentFile
        }
        if (destination.exists() && !destination.isFile()) {
            throw new GradleException('Introduction BASE.md is not a file: ' + destination)
        }
        String previous = destination.isFile() ? destination.getText('UTF-8') : null
        if (previous == introduction) {
            return [status: 'UNCHANGED', file: destination]
        }
        if (!apply) return [status: 'DRY_RUN', file: destination]

        Files.createDirectories(destination.parentFile.toPath())
        File staged = File.createTempFile('.fill-introduction-', '.md', destination.parentFile)
        try {
            staged.setText(introduction, 'UTF-8')
            if ((destination.isFile() ? destination.getText('UTF-8') : null) != previous) {
                throw new GradleException('BASE.md changed during Introduction preflight.')
            }
            Files.move(staged.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING)
            if (destination.getText('UTF-8') != introduction) {
                throw new GradleException('BASE.md Introduction readback mismatch.')
            }
            return [status: previous == null ? 'IMPORTED' : 'UPDATED', file: destination]
        } finally {
            Files.deleteIfExists(staged.toPath())
        }
    }
}
