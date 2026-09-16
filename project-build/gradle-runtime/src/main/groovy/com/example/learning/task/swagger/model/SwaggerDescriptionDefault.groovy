package com.example.learning.task.swagger.model

class SwaggerDescriptionDefault {

    final String summary

    final String description

    final boolean enableVideoYoutube

    final String videoYoutubeId

    final String videoYoutubeTitle

    final String controllerDescriptionParagraph

    final String execution


    SwaggerDescriptionDefault(
            String summary,
            String description,
            boolean enableVideoYoutube,
            String videoYoutubeId,
            String videoYoutubeTitle,
            String controllerDescriptionParagraph,
            String execution
    ) {

        this.summary =
                summary

        this.description =
                description

        this.enableVideoYoutube =
                enableVideoYoutube

        this.videoYoutubeId =
                videoYoutubeId

        this.videoYoutubeTitle =
                videoYoutubeTitle

        this.controllerDescriptionParagraph =
                controllerDescriptionParagraph

        this.execution =
                execution
    }
}
