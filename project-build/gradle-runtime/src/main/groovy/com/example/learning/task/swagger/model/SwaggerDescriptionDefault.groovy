package com.example.learning.task.swagger.model

class SwaggerDescriptionDefault {

    final String summary

    final String description

    final boolean enableVideoYoutube

    final String videoYoutubeId

    final String videoYoutubeTitle


    SwaggerDescriptionDefault(
            String summary,
            String description,
            boolean enableVideoYoutube,
            String videoYoutubeId,
            String videoYoutubeTitle
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
    }
}