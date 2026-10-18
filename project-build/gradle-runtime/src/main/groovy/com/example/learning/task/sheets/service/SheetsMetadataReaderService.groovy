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
 * Read-only verification for standard importers. Interview normalization
 * APPLY opts into a separate write-scoped context.
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
        Map<String, Object> session = openTab(fileUrl, sheetId)
        String actualModuleLocation = session.moduleLocation as String
        String tabTitle = session.tabTitle as String
        String tabId = session.sheetId as String

        if (!actualModuleLocation?.trim()) {
            throw new GradleException(
                    "Module Location is empty or missing at tab '${tabTitle}' " +
                            "(gid=${tabId}), cell ${MODULE_LOCATION_CELL}. " +
                            "Expected module: '${expectedModuleLocation}'. Stop before content generation."
            )
        }

        if (actualModuleLocation != expectedModuleLocation) {
            throw new GradleException(
                    "Module Location mismatch at tab '${tabTitle}' " +
                            "(gid=${tabId}), cell ${MODULE_LOCATION_CELL}. " +
                            "Task module: '${expectedModuleLocation}'; " +
                            "Sheet module: '${actualModuleLocation}'. Stop before content generation."
            )
        }

        return [
                spreadsheetTitle: session.spreadsheetTitle as String,
                tabTitle        : tabTitle,
                spreadsheetId   : session.spreadsheetId as String,
                sheetId         : tabId,
                moduleLocation  : actualModuleLocation
        ]
    }

    /**
     * Opens an explicitly selected tab for root-level importers, even if
     * I3 points to a documentation folder that is not a Gradle project.
     * Callers MUST validate I3 as a safe destination before writing.
     */
    Map<String, Object> openTab(
            String fileUrl,
            String sheetId
    ) {
        return readTab(openSpreadsheet(fileUrl), sheetId)
    }

    /** Write scope is available only to explicit Sheet normalization tasks. */
    Map<String, Object> openTabForWrite(
            String fileUrl,
            String sheetId
    ) {
        return readTab(openSpreadsheetInternal(fileUrl, true), sheetId)
    }

    /**
     * One authenticated client and one metadata request per spreadsheet.
     * The root batch importer reuses this context for every tab.
     */
    Map<String, Object> openSpreadsheet(String fileUrl) {
        return openSpreadsheetInternal(fileUrl, false)
    }

    private Map<String, Object> openSpreadsheetInternal(
            String fileUrl,
            boolean writeAccess
    ) {
        String spreadsheetId = extractSpreadsheetId(fileUrl)

        try {
            GoogleCredentials credentials = resolveCredentials(writeAccess)

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

            List<Map<String, Object>> tabs = (spreadsheet.sheets ?: []).collect { entry ->
                [
                        title: entry.properties?.title ?: '',
                        gid  : entry.properties?.sheetId?.toString() ?: ''
                ] as Map<String, Object>
            }
            // Google Sheets has a per-minute read-request limit. One batch
            // request for all I3 cells prevents 19+ individual API calls.
            List<String> locationRanges = tabs.collect { Map tab ->
                "'${tab.title.replace("'", "''")}'!${MODULE_LOCATION_CELL}"
            }
            if (locationRanges) {
                def data = client.spreadsheets().values()
                        .batchGet(spreadsheetId)
                        .setRanges(locationRanges)
                        .execute()
                tabs.eachWithIndex { Map tab, int index ->
                    List<List<Object>> rows =
                            data.valueRanges[index]?.values as List<List<Object>>
                    tab.moduleLocation = (rows && rows[0])
                            ? rows[0][0]?.toString()
                            : null
                }
            }
            return [
                    spreadsheetTitle: spreadsheet.properties?.title ?: '',
                    spreadsheetId   : spreadsheetId,
                    client          : client,
                    tabs            : tabs
            ]
        }
        catch (GradleException exception) {
            throw exception
        }
        catch (Exception exception) {
            throw new GradleException(
                    "Unable to read Google Sheets metadata using ADC (spreadsheet '${spreadsheetId}'). " +
                            'Verify that Sheets API and IAM Credentials API are enabled, ADC impersonation has ' +
                            'Service Account Token Creator permission, and the spreadsheet is shared with the ' +
                            (writeAccess ? 'service account as Editor.' : 'service account as Viewer.') +
                            ' Cause: ' + exception.message,
                    exception
            )
        }
    }

    Map<String, Object> readTab(
            Map<String, Object> spreadsheet,
            String sheetId
    ) {
        String tabId = sheetId?.trim()
        if (!(tabId ==~ /[0-9]+/)) {
            throw new GradleException(
                    "sheetId must be a numeric Google Sheets tab gid; received '${tabId}'."
            )
        }
        Map tab = (spreadsheet.tabs as List<Map>).find {
            Map entry -> entry.gid == tabId
        }
        if (!tab) {
            throw new GradleException(
                    "Spreadsheet is accessible, but tab gid '${tabId}' was not found."
            )
        }
        return [
                spreadsheetTitle: spreadsheet.spreadsheetTitle,
                spreadsheetId   : spreadsheet.spreadsheetId,
                client          : spreadsheet.client,
                tabTitle        : tab.title,
                sheetId         : tabId,
                moduleLocation  : tab.moduleLocation
        ]
    }

    /**
     * ADC may contain an authorized_user credential even after running
     * gcloud auth application-default login --impersonate-service-account.
     * A user refresh token obtained only for cloud-platform cannot be
     * expanded to the Sheets scope by createScoped().
     *
     * When a target service account is supplied via the environment,
     * request a short-lived impersonated token with the scope selected by the
     * caller (read-only normally; writable for Interview APPLY).
     * No service-account keys are needed.
     */
    private static GoogleCredentials resolveCredentials(boolean writeAccess) {
        GoogleCredentials adc = GoogleCredentials.getApplicationDefault()
        List<String> scopes =
                Collections.singletonList(writeAccess
                        ? SheetsScopes.SPREADSHEETS
                        : SheetsScopes.SPREADSHEETS_READONLY)

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
