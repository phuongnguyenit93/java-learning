package com.example.learning.setup.root.database.service.mongo

import com.example.learning.generated.settings.DatabaseListEnum
import com.example.learning.setup.root.database.service.DatabaseEnvironmentService
import com.example.learning.setup.root.database.service.DatabaseProjectResolverService
import groovy.json.JsonOutput
import org.apache.hc.client5.http.auth.AuthScope
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.StringEntity
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger


class MongoWhitelistService {

    private final Logger logger

    private final DatabaseProjectResolverService projectResolverService

    private final DatabaseEnvironmentService environmentService


    MongoWhitelistService(
            Logger logger,
            DatabaseProjectResolverService projectResolverService,
            DatabaseEnvironmentService environmentService
    ) {

        this.logger =
                logger

        this.projectResolverService =
                projectResolverService

        this.environmentService =
                environmentService
    }


    void updateCurrentIp(
            Project rootProject
    ) {

        // ======================================================
        // MongoDB module
        // ======================================================

        DatabaseListEnum database =
                projectResolverService
                        .resolveByName(
                                'MONGODB'
                        )


        Project databaseProject =
                projectResolverService
                        .resolveProject(
                                rootProject,
                                database
                        )


        Map<String, String> environment =
                environmentService
                        .load(
                                databaseProject
                        )


        // ======================================================
        // Atlas credentials
        // ======================================================

        String projectId =
                environmentService.requireValue(
                        environment,
                        'MONGODB_ATLAS_PROJECT_ID'
                )


        String publicKey =
                environmentService.requireValue(
                        environment,
                        'MONGODB_ATLAS_PUBLIC_KEY'
                )


        String privateKey =
                environmentService.requireValue(
                        environment,
                        'MONGODB_ATLAS_PRIVATE_KEY'
                )


        // ======================================================
        // Public IP
        // ======================================================

        String currentIp =
                resolveCurrentPublicIp()


        logger.lifecycle(
                '🌐 [MONGODB] Current public IP: {}',
                currentIp
        )


        // ======================================================
        // Authentication
        // ======================================================

        BasicCredentialsProvider credentialsProvider =
                new BasicCredentialsProvider()


        credentialsProvider.setCredentials(
                new AuthScope(
                        null,
                        -1
                ),
                new UsernamePasswordCredentials(
                        publicKey,
                        privateKey.toCharArray()
                )
        )


        def httpClient =
                HttpClients
                        .custom()
                        .setDefaultCredentialsProvider(
                                credentialsProvider
                        )
                        .build()


        try {

            HttpPost request =
                    new HttpPost(
                            "https://cloud.mongodb.com/api/atlas/v2/groups/${projectId}/accessList"
                    )


            request.addHeader(
                    'Accept',
                    'application/vnd.atlas.2023-02-01+json'
            )


            String payload =
                    JsonOutput.toJson(
                            [
                                    [
                                            ipAddress:
                                                    currentIp,

                                            comment:
                                                    "Auto-added by Gradle Task at ${new Date().format('yyyy-MM-dd HH:mm:ss')}"
                                    ]
                            ]
                    )


            request.setEntity(
                    new StringEntity(
                            payload,
                            ContentType.APPLICATION_JSON
                    )
            )


            httpClient
                    .execute(
                            request
                    )
                    .withCloseable {
                        response ->

                            int status =
                                    response.code


                            String responseBody =
                                    response.entity != null
                                            ? EntityUtils.toString(
                                            response.entity
                                    )
                                            : ''


                            if (status != 201) {

                                throw new GradleException(
                                        """
MongoDB Atlas whitelist update failed.

Status:
${status}

Response:
${responseBody}
""".stripIndent()
                                )
                            }


                            logger.lifecycle(
                                    '✅ [MONGODB] Whitelisted IP: {}',
                                    currentIp
                            )
                    }
        }
        catch (GradleException exception) {

            throw exception
        }
        catch (Exception exception) {

            throw new GradleException(
                    'Unable to update MongoDB Atlas whitelist.',
                    exception
            )
        }
        finally {

            httpClient.close()
        }
    }


    private static String resolveCurrentPublicIp() {

        try {

            URLConnection connection =
                    new URL(
                            'https://checkip.amazonaws.com'
                    )
                            .openConnection()


            connection.connectTimeout =
                    10_000


            connection.readTimeout =
                    10_000


            String currentIp =
                    connection
                            .inputStream
                            .getText(
                                    'UTF-8'
                            )
                            .trim()


            if (currentIp.isBlank()) {

                throw new GradleException(
                        'Unable to determine current public IP.'
                )
            }


            return currentIp
        }
        catch (GradleException exception) {

            throw exception
        }
        catch (Exception exception) {

            throw new GradleException(
                    'Unable to determine current public IP.',
                    exception
            )
        }
    }
}