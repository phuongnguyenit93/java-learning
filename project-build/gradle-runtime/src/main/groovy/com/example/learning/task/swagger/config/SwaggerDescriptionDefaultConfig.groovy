package com.example.learning.task.swagger.config

import com.example.learning.task.swagger.model.SwaggerDescriptionDefault

class SwaggerDescriptionDefaultConfig {

    /*
     * Đây là nơi DUY NHẤT chỉnh default
     * cho từng language.
     */
    private static final Map<String, SwaggerDescriptionDefault> DEFAULTS = [

            vi: new SwaggerDescriptionDefault(
                    'Chưa có tiêu đề',
                    'Chưa có mô tả chi tiết',
                    'ID của video youtube chèn vào description trong API'
            ),

            en: new SwaggerDescriptionDefault(
                    'Title not provided',
                    'Detailed description not provided',
                    'ID of youtube video add to API description'
            )

    ].asImmutable()


    static SwaggerDescriptionDefault get(
            String language
    ) {

        String normalizedLanguage =
                language
                        ?.trim()
                        ?.toLowerCase()


        SwaggerDescriptionDefault defaults =
                DEFAULTS[
                        normalizedLanguage
                ]


        if (defaults == null) {

            throw new IllegalStateException(
                    """
Swagger description defaults are not configured for language:

${language}

Add the language configuration to:

${SwaggerDescriptionDefaultConfig.name}
"""
            )
        }


        return defaults
    }
}