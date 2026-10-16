package com.example.learning.task.sheets.service

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.Spreadsheet
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ImpersonatedCredentials
import org.gradle.api.GradleException

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Read-only verification of a configured spreadsheet, tab and module location.
 *
 * Credentials are resolved by Google ADC at execution time; never load
 * credentials or create tokens during Gradle configuration.
 */
class SheetsMetadataReaderService {

    private static final Pattern SPREADSHEET_URL =
            Pattern.compile('^/spreadsheets/d/([A-Za-z0-9_-]+)(?:/.*)?$')

    private static final String IMPERSONATE_ENV =
            'GOOGLE_SHEETS_IMPERSONATE_SERVICE_ACCOUNT'

    private static final String MODULE_LOCATION_CELL = 'I3'

    Map<String, String> verify(
            String fileUrl,
            String sheetId,
            String expectedModuleLocation
    ) {

        String spreadsheetId = extractSpreadsheetId(fileUrl)
        String tabId = sheetId?.trim()

        if (!(tabId ==~ /[0-9]+/)) {
            throw new GradleException(
                    "sheetId must be a numeric Google Sheets tab gid; received '${tabId}'."
            )
        }

        try {
            GoogleCredentials credentials = resolveReadOnlyCredentials()

            Sheets client =
                    new Sheets.Builder(
                            GoogleNetHttpTransport.newTrustedTransport(),
                            GsonFactory.getDefaultInstance(),
                            new HttpCredentialsAdapter(credentials)
                    )
                            .setApplicationName('java-learning-gradle-runtime')
                            .build()

            // Metadata only. Do not request grid content or cell values.
            Spreadsheet spreadsheet =
                    client.spreadsheets()
                            .get(spreadsheetId)
                            .setFields('spreadsheetId,properties(title),sheets(properties(sheetId,title))')
                            .execute()

            def sheet =
                    spreadsheet.sheets?.find { entry ->
                        entry.properties?.sheetId?.toString() == tabId
                    }

            if (sheet == null) {
                throw new GradleException(
                        "Spreadsheet is accessible, but tab gid '${tabId}' was not found."
                )
            }

            // STEP 1 writes the root-relative module path in the selected tab's I3.
            // Read ONLY that location before any future content-import operation.
            String escapedTabTitle = sheet.properties.title.replace("'", "''")
            String moduleLocationRange = "'${escapedTabTitle}'!${MODULE_LOCATION_CELL}"
            List<List<Object>> locationValues =
                    client.spreadsheets()
                            .values()
                            .get(spreadsheetId, moduleLocationRange)
                            .execute()
                            .values

            String actualModuleLocation = null
            if (locationValues && locationValues[0]) {
                actualModuleLocation = locationValues[0][0]?.toString()
            }

            if (!actualModuleLocation?.trim()) {
                throw new GradleException(
                        "Module Location is empty or missing at tab '${sheet.properties.title}' " +
                                "(gid=${tabId}), cell ${MODULE_LOCATION_CELL}. " +
                                "Expected module: '${expectedModuleLocation}'. Stop before content generation."
                )
            }

            if (actualModuleLocation != expectedModuleLocation) {
                throw new GradleException(
                        "Module Location mismatch at tab '${sheet.properties.title}' " +
                                "(gid=${tabId}), cell ${MODULE_LOCATION_CELL}. " +
                                "Task module: '${expectedModuleLocation}'; " +
                                "Sheet module: '${actualModuleLocation}'. Stop before content generation."
                )
            }

            return [
                    spreadsheetTitle: spreadsheet.properties?.title ?: '',
                    tabTitle        : sheet.properties?.title ?: '',
                    spreadsheetId   : spreadsheetId,
                    sheetId         : tabId,
                    moduleLocation  : actualModuleLocation
            ]
        }
        catch (GradleException exception) {
            throw exception
        }
        catch (Exception exception) {
            throw new GradleException(
                    "Unable to read Google Sheets metadata using ADC (spreadsheet '${spreadsheetId}', tab '${tabId}'). " +
                            'Verify that Sheets API and IAM Credentials API are enabled, ADC impersonation has ' +
                            'Service Account Token Creator permission, and the spreadsheet is shared with the ' +
                            'service account as Viewer. Cause: ' + exception.message,
                    exception
            )
        }
    }

    /**
     * ADC may contain an authorized_user credential even after running
     * gcloud auth application-default login --impersonate-service-account.
     * A user refresh token obtained only for cloud-platform cannot be
     * expanded to the Sheets scope by createScoped().
     *
     * When a target service account is supplied via the environment,
     * request a short-lived impersonated token with the Sheets read-only
     * scope. No service-account keys are needed.
     */
    private static GoogleCredentials resolveReadOnlyCredentials() {
        GoogleCredentials adc = GoogleCredentials.getApplicationDefault()
        List<String> scopes =
                Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY)

        String serviceAccount =
                System.getenv(IMPERSONATE_ENV)?.trim()

        if (serviceAccount) {
            if (!(serviceAccount ==~ /[A-Za-z0-9._-]+@[A-Za-z0-9.-]+\.iam\.gserviceaccount\.com/)) {
                throw new GradleException(
                        "${IMPERSONATE_ENV} must contain a valid Google Service Account email."
                )
            }

            // An impersonated ADC already owns its target identity.
            // Avoid switching identities silently when it differs.
            if (adc instanceof ImpersonatedCredentials) {
                if (adc.targetPrincipal != serviceAccount) {
                    throw new GradleException(
                            "${IMPERSONATE_ENV} does not match the target Service Account in ADC."
                    )
                }
                return adc.createScoped(scopes)
            }

            return ImpersonatedCredentials.newBuilder()
                    .setSourceCredentials(adc)
                    .setTargetPrincipal(serviceAccount)
                    .setScopes(scopes)
                    .build()
        }

        return adc.createScoped(scopes)
    }

    private static String extractSpreadsheetId(String fileUrl) {
        URI uri

        try {
            uri = new URI(fileUrl?.trim())
        }
        catch (Exception ignored) {
            throw new GradleException('fileUrl must be a valid Google Sheets document URL.')
        }

        if (uri.scheme != 'https' || uri.host != 'docs.google.com') {
            throw new GradleException(
                    'fileUrl must be an HTTPS Google Sheets URL on docs.google.com.'
            )
        }

        Matcher matcher = SPREADSHEET_URL.matcher(uri.path ?: '')

        if (!matcher.matches()) {
            throw new GradleException(
                    'fileUrl must use https://docs.google.com/spreadsheets/d/<spreadsheetId>.'
            )
        }

        return matcher.group(1)
    }
}
