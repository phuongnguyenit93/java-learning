package com.example.learning.task.yml.service

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

class YamlMergeService {

    /**
     * Load file application.yml gốc.
     *
     * Key:
     *   default
     *   local
     *   prod
     *   ...
     */
    Map<String, Map> loadBase(
            File sourceFile
    ) {

        Map<String, Map> documents =
                new LinkedHashMap<>()

        loadDocuments(sourceFile).each { Map document ->

            String profileName =
                    getProfileName(document)

            documents[profileName] =
                    document
        }

        return documents
    }


    /**
     * Merge toàn bộ document của một application.yml
     * vào documents hiện tại.
     */
    void merge(
            Map<String, Map> baseDocuments,
            File sourceFile
    ) {

        loadDocuments(sourceFile).each { Map document ->

            String profileName =
                    getProfileName(document)


            if (
                    baseDocuments.containsKey(
                            profileName
                    )
            ) {

                mergeMaps(
                        baseDocuments[profileName],
                        document
                )

            } else {

                baseDocuments[profileName] =
                        document
            }
        }
    }


    /**
     * Ghi toàn bộ YAML documents ra file.
     */
    void write(
            File outputFile,
            Collection<Map> documents,
            String header = null
    ) {

        if (!outputFile.parentFile.exists()) {

            boolean created =
                    outputFile.parentFile.mkdirs()

            if (!created) {

                throw new IllegalStateException(
                        """
Unable to create directory:

${outputFile.parentFile.absolutePath}
"""
                )
            }
        }


        DumperOptions options =
                new DumperOptions()

        options.setDefaultFlowStyle(
                DumperOptions.FlowStyle.BLOCK
        )

        options.setExplicitStart(
                true
        )


        Yaml writer =
                new Yaml(options)


        outputFile.withWriter(
                'UTF-8'
        ) { Writer out ->

            if (
                    header != null &&
                            !header.isBlank()
            ) {

                out.write(header)

                if (!header.endsWith('\n')) {
                    out.write('\n')
                }
            }


            documents.each { Map document ->

                writer.dump(
                        document,
                        out
                )
            }
        }
    }


    /**
     * Load tất cả YAML document trong file.
     */
    private static List<Map> loadDocuments(
            File sourceFile
    ) {

        Yaml yaml =
                new Yaml()

        List<Map> documents = []


        yaml.loadAll(
                sourceFile.getText('UTF-8')
        ).each { Object document ->

            if (document instanceof Map) {

                documents.add(
                        document as Map
                )
            }
        }


        return documents
    }


    /**
     * Spring profile:
     *
     * spring:
     *   config:
     *     activate:
     *       on-profile: local
     *
     * Không có profile -> default
     */
    private static String getProfileName(
            Map document
    ) {

        Object spring =
                document.get('spring')

        if (!(spring instanceof Map)) {
            return 'default'
        }


        Object config =
                spring.get('config')

        if (!(config instanceof Map)) {
            return 'default'
        }


        Object activate =
                config.get('activate')

        if (!(activate instanceof Map)) {
            return 'default'
        }


        Object profile =
                activate.get('on-profile')


        return profile
                ?.toString()
                ?.trim() ?: 'default'
    }


    /**
     * Deep merge.
     *
     * override thắng base.
     */
    static void mergeMaps(
            Map base,
            Map override
    ) {

        override.each { key, value ->

            if (base.containsKey(key)) {

                if (
                        value instanceof Map &&
                                base[key] instanceof Map
                ) {

                    mergeMaps(
                            base[key] as Map,
                            value as Map
                    )

                } else if (
                        base[key] != value
                ) {

                    base[key] =
                            value
                }

            } else {

                base[key] =
                        value
            }
        }
    }
}