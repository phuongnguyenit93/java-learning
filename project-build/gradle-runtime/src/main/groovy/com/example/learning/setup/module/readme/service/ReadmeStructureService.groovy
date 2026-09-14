package com.example.learning.setup.module.readme.service

import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger


class ReadmeStructureService {

    private final Logger logger


    ReadmeStructureService(
            Logger logger
    ) {

        this.logger =
                logger
    }


    void setup(
            Project project
    ) {

        // ====================================================
        // BUILD_README
        // ====================================================

        if (
                !ProjectPropertyUtils.isEnabled(
                        project,
                        'BUILD_README'
                )
        ) {

            logger.info(
                    '[README-STRUCTURE] Skip {} because BUILD_README != TRUE.',
                    project.path
            )

            return
        }


        // ====================================================
        // Root README.md
        // ====================================================

        ensureRootReadme(
                project
        )


        // ====================================================
        // Languages
        // ====================================================

        List<String> languages =
                resolveLanguages(
                        project
                )


        /*
         * Giữ nguyên behavior cũ:
         *
         * Không có README_LANGUAGE
         * hoặc danh sách rỗng
         * → chỉ đảm bảo README.md root tồn tại
         * → không cleanup language cũ.
         */
        if (languages.isEmpty()) {

            logger.info(
                    '[README-STRUCTURE] No README_LANGUAGE configured for {}.',
                    project.path
            )

            return
        }


        // ====================================================
        // Language structures
        // ====================================================

        languages.each {
            String language ->

                ensureLanguageStructure(
                        project,
                        language
                )
        }


        // ====================================================
        // Cleanup
        // ====================================================

        cleanupUnusedLanguages(
                project,
                languages
        )
    }


    // ========================================================
    // Root README
    // ========================================================

    private void ensureRootReadme(
            Project project
    ) {

        File readmeFile =
                new File(
                        project.projectDir,
                        'README.md'
                )


        if (readmeFile.exists()) {

            if (!readmeFile.isFile()) {

                throw new GradleException(
                        """
README path exists but is not a file:

${readmeFile.absolutePath}
""".stripIndent()
                )
            }


            logger.info(
                    '[README-STRUCTURE] Root README already exists: {}',
                    readmeFile.absolutePath
            )

            return
        }


        createEmptyFile(
                readmeFile
        )


        logger.lifecycle(
                '✨ [README-STRUCTURE] Created root README: {}',
                readmeFile.absolutePath
        )
    }


    // ========================================================
    // Language structure
    // ========================================================

    private void ensureLanguageStructure(
            Project project,
            String language
    ) {

        File languageDirectory =
                new File(
                        project.projectDir,
                        "readme/${language}"
                )


        File menuDirectory =
                new File(
                        languageDirectory,
                        'menu'
                )


        // ====================================================
        // Language directory
        // ====================================================

        if (
                languageDirectory.exists() &&
                        !languageDirectory.isDirectory()
        ) {

            throw new GradleException(
                    """
README language path exists but is not a directory:

${languageDirectory.absolutePath}
""".stripIndent()
            )
        }


        // ====================================================
        // Menu directory
        // ====================================================

        ensureDirectory(
                menuDirectory
        )


        // ====================================================
        // BASE.md
        // ====================================================

        ensureLanguageFile(
                new File(
                        languageDirectory,
                        'BASE.md'
                )
        )


        // ====================================================
        // LIST.md
        // ====================================================

        ensureLanguageFile(
                new File(
                        languageDirectory,
                        'LIST.md'
                )
        )


        logger.info(
                '[README-STRUCTURE] Language structure ready: {}',
                language
        )
    }


    private void ensureLanguageFile(
            File file
    ) {

        if (file.exists()) {

            if (!file.isFile()) {

                throw new GradleException(
                        """
README structure path exists but is not a file:

${file.absolutePath}
""".stripIndent()
                )
            }


            /*
             * Human-owned.
             *
             * File đã tồn tại thì tuyệt đối không sửa,
             * kể cả nội dung rỗng.
             */
            return
        }


        createEmptyFile(
                file
        )


        logger.lifecycle(
                '✨ [README-STRUCTURE] Created: {}',
                file.absolutePath
        )
    }


    // ========================================================
    // Cleanup
    // ========================================================

    private void cleanupUnusedLanguages(
            Project project,
            List<String> languages
    ) {

        File readmeDirectory =
                new File(
                        project.projectDir,
                        'readme'
                )


        if (!readmeDirectory.exists()) {
            return
        }


        if (!readmeDirectory.isDirectory()) {

            throw new GradleException(
                    """
README path exists but is not a directory:

${readmeDirectory.absolutePath}
""".stripIndent()
            )
        }


        File[] languageDirectories =
                readmeDirectory.listFiles()


        if (languageDirectories == null) {

            throw new GradleException(
                    """
Unable to read README directory:

${readmeDirectory.absolutePath}
""".stripIndent()
            )
        }


        languageDirectories.each {
            File languageDirectory ->

                /*
                 * Chỉ xử lý directory.
                 *
                 * File nằm trực tiếp trong readme/
                 * không thuộc trách nhiệm cleanup này.
                 */
                if (!languageDirectory.isDirectory()) {
                    return
                }


                /*
                 * Language vẫn còn active.
                 */
                if (
                        languages.contains(
                                languageDirectory.name
                        )
                ) {

                    return
                }


                cleanupUnusedLanguage(
                        languageDirectory
                )
        }
    }


    private void cleanupUnusedLanguage(
            File languageDirectory
    ) {

        File menuDirectory =
                new File(
                        languageDirectory,
                        'menu'
                )


        /*
         * Không có menu/
         *
         * Không đủ cơ sở để khẳng định đây là structure
         * được generator tạo ra.
         *
         * → giữ nguyên.
         */
        if (!menuDirectory.exists()) {

            logger.info(
                    '[README-STRUCTURE] Keep obsolete language {} because menu directory does not exist.',
                    languageDirectory.name
            )

            return
        }


        if (!menuDirectory.isDirectory()) {

            throw new GradleException(
                    """
README menu path exists but is not a directory:

${menuDirectory.absolutePath}
""".stripIndent()
            )
        }


        String[] menuContents =
                menuDirectory.list()


        if (menuContents == null) {

            throw new GradleException(
                    """
Unable to read README menu directory:

${menuDirectory.absolutePath}
""".stripIndent()
            )
        }


        /*
         * menu có dữ liệu
         *
         * → language đã được sử dụng thực tế
         * → tuyệt đối không auto-delete.
         */
        if (menuContents.length > 0) {

            logger.lifecycle(
                    '🛡️ [README-STRUCTURE] Keep obsolete language [{}] because menu contains data.',
                    languageDirectory.name
            )

            return
        }


        /*
         * Language không còn được khai báo
         * +
         * menu tồn tại và rỗng
         *
         * → xem như structure được tạo nhầm/chưa sử dụng.
         * → xóa toàn bộ language directory.
         */
        boolean deleted =
                languageDirectory.deleteDir()


        if (
                !deleted &&
                        languageDirectory.exists()
        ) {

            throw new GradleException(
                    """
Unable to delete obsolete README language directory:

${languageDirectory.absolutePath}
""".stripIndent()
            )
        }


        logger.lifecycle(
                '🗑️ [README-STRUCTURE] Removed obsolete unused language: {}',
                languageDirectory.name
        )
    }


    // ========================================================
    // Languages
    // ========================================================

    private static List<String> resolveLanguages(
            Project project
    ) {

        return ProjectPropertyUtils
                .getCsvList(
                        project,
                        'README_LANGUAGE'
                )
                .collect {
                    String language ->

                        language.toLowerCase(
                                Locale.ROOT
                        )
                }
                .unique()
    }


    // ========================================================
    // File helpers
    // ========================================================

    private static void ensureDirectory(
            File directory
    ) {

        if (directory.exists()) {

            if (!directory.isDirectory()) {

                throw new GradleException(
                        """
Expected directory but found another filesystem entry:

${directory.absolutePath}
""".stripIndent()
                )
            }


            return
        }


        if (
                !directory.mkdirs() &&
                        !directory.isDirectory()
        ) {

            throw new GradleException(
                    """
Unable to create directory:

${directory.absolutePath}
""".stripIndent()
            )
        }
    }


    private static void createEmptyFile(
            File file
    ) {

        try {

            boolean created =
                    file.createNewFile()


            if (
                    !created &&
                            !file.isFile()
            ) {

                throw new GradleException(
                        """
Unable to create file:

${file.absolutePath}
""".stripIndent()
                )
            }
        }
        catch (GradleException exception) {

            throw exception
        }
        catch (Exception exception) {

            throw new GradleException(
                    """
Unable to create file:

${file.absolutePath}

Reason:
${exception.message}
""".stripIndent(),
                    exception
            )
        }
    }
}