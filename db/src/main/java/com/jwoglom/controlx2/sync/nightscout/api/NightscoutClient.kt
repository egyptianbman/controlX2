package com.jwoglom.controlx2.sync.nightscout.api

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.jwoglom.controlx2.sync.nightscout.NightscoutApiSecretHeader
import com.jwoglom.controlx2.sync.nightscout.hashNightscoutApiSecret
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutAnnouncement
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutDeviceStatus
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutEntry
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutProfile
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutStateSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Nightscout API client implementation
 *
 * This client handles authentication and HTTP communication with Nightscout.
 * It uses HttpURLConnection for simplicity - could be replaced with Retrofit later.
 */
class NightscoutClient(
    private val baseUrl: String,
    private val apiSecret: String
) : NightscoutApi {

    private val gson = Gson()
    private val apiSecretHash: String by lazy { hashNightscoutApiSecret(apiSecret) }
    private val announcementParser = NightscoutAnnouncementParser(gson)
    // Backing field (needed so the coordinator can set a hook that persists on this instance;
    // the interface default alone has no per-instance storage). null = no progress reporting.
    override var onChunkProgress: ((items: Int) -> Unit)? = null
    private companion object {
        // Uniform upload chunk size for treatments AND CGM entries. Sized empirically: the
        // largest item (combo bolus, 360 B) at 100 = ~36 KB, comfortably under the 30%-margin
        // budget (~53 KB) derived from the proven-safe 250-basal chunk. One shared limit keeps
        // every processor's POST well under Nightscout's response window -- the 30s read timeout
        // that timed out 2283 basal treatments on 2026-10-19. uploadEntries had no cap at all.
        const val UPLOAD_CHUNK_SIZE = 100

        // Pause between upload requests so Nocturne can drain each POST before the next burst.
        // Requests already run serially per processor; this only guards a tight back-to-back run.
        const val UPLOAD_REQUEST_DELAY_MS = 200L
    }

    override suspend fun uploadEntries(entries: List<NightscoutEntry>): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                // Chunk like treatments: CGM had no per-item cap, so a large retroactive range
                // would POST thousands of readings in one oversized request (same timeout risk).
                for (chunk in entries.chunked(UPLOAD_CHUNK_SIZE)) {
                    post("/api/v1/entries", chunk)
                    delay(UPLOAD_REQUEST_DELAY_MS)
                    onChunkProgress?.invoke(chunk.size)
                }
                Timber.d("Uploaded ${entries.size} entries to Nightscout")
                Result.success(entries.size)
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload entries to Nightscout")
                Result.failure(e)
            }
        }
    }

    override suspend fun uploadTreatments(treatments: List<NightscoutTreatment>): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                // Chunk large batches: a single oversized POST can exceed Nightscout's
                // response window and trip the 30s read timeout (2283 basal treatments timed
                // out 2026-10-19). Smaller batches stay well under the response window.
                for (chunk in treatments.chunked(UPLOAD_CHUNK_SIZE)) {
                    post("/api/v1/treatments", chunk)
                    delay(UPLOAD_REQUEST_DELAY_MS)
                    onChunkProgress?.invoke(chunk.size)
                }
                Timber.d("Uploaded ${treatments.size} treatments to Nightscout")
                Result.success(treatments.size)
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload treatments to Nightscout")
                Result.failure(e)
            }
        }
    }

    override suspend fun uploadDeviceStatus(status: NightscoutDeviceStatus): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                post("/api/v4/device-status/pump", listOf(status))
                Timber.d("Uploaded device status to Nightscout")
                Result.success(true)
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload device status to Nightscout")
                Result.failure(e)
            }
        }
    }

    override suspend fun uploadProfile(profile: NightscoutProfile): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                post("/api/v1/profile", listOf(profile))
                Timber.d("Uploaded profile to Nightscout")
                Result.success(true)
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload profile to Nightscout")
                Result.failure(e)
            }
        }
    }

    override suspend fun getActivePumpModeSpans(): Result<List<NightscoutStateSpan>> {
        return withContext(Dispatchers.IO) {
            try {
                // GET /api/v4/state-spans?category=PumpMode&active=true
                // Response envelope: {"data":[...],"pagination":{...}}
                val json = get("/api/v4/state-spans?category=PumpMode&active=true")
                val root = gson.fromJson(json, JsonElement::class.java)
                val spans = root.getAsJsonObject().getAsJsonArray("data")
                    .let { array ->
                        (0 until array.size()).map { i ->
                            gson.fromJson(array.get(i), NightscoutStateSpan::class.java)
                        }
                    }
                Timber.d("Fetched ${spans.size} open PumpMode spans")
                Result.success(spans)
            } catch (e: NightscoutAuthException) {
                Timber.w(e, "Nocturne auth failed while fetching PumpMode spans")
                Result.failure(e)
            } catch (e: JsonSyntaxException) {
                val wrapped = IllegalArgumentException("Malformed state-spans response", e)
                Timber.e(wrapped)
                Result.failure(wrapped)
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch open PumpMode spans")
                Result.failure(e)
            }
        }
    }

    override suspend fun createStateSpan(span: NightscoutStateSpan): Result<NightscoutStateSpan> {
        return withContext(Dispatchers.IO) {
            try {
                // POST /api/v4/state-spans -> 201; body is the created span (includes id)
                val json = post("/api/v4/state-spans", span)
                val created = gson.fromJson(json, NightscoutStateSpan::class.java)
                Timber.d("Created PumpMode span ${created.id} (${created.state})")
                // One span == one log; report per span so the bar moves in single-log steps for
                // mode changes (Nocturne has no batch state-spans endpoint). Delay removed: spans
                // are already one-at-a-time here, so the guard only added crawl.
                onChunkProgress?.invoke(1)
                Result.success(created)
            } catch (e: NightscoutAuthException) {
                Timber.w(e, "Nocturne auth failed while creating PumpMode span")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "Failed to create PumpMode span")
                Result.failure(e)
            }
        }
    }

    override suspend fun closeStateSpan(id: String, endMills: Long): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                // PUT /api/v4/state-spans/{id} {"endMills": <millis>}
                put("/api/v4/state-spans/${URLEncoder.encode(id, "UTF-8")}", mapOf("endMills" to endMills))
                Timber.d("Closed PumpMode span $id at $endMills")
                Result.success(true)
            } catch (e: NightscoutAuthException) {
                Timber.w(e, "Nocturne auth failed while closing PumpMode span")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "Failed to close PumpMode span")
                Result.failure(e)
            }
        }
    }

    override suspend fun getLastEntries(count: Int): Result<List<NightscoutEntry>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = get("/api/v1/entries?count=$count")
                val type = object : TypeToken<List<NightscoutEntry>>() {}.type
                val entries: List<NightscoutEntry> = gson.fromJson(response, type)
                Result.success(entries)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get last entries from Nightscout")
                Result.failure(e)
            }
        }
    }

    override suspend fun getLastTreatment(eventType: String): Result<NightscoutTreatment?> {
        return withContext(Dispatchers.IO) {
            try {
                val response = get("/api/v1/treatments?eventType=$eventType&count=1")
                val type = object : TypeToken<List<NightscoutTreatment>>() {}.type
                val treatments: List<NightscoutTreatment> = gson.fromJson(response, type)
                Result.success(treatments.firstOrNull())
            } catch (e: Exception) {
                Timber.e(e, "Failed to get last treatment from Nightscout")
                Result.failure(e)
            }
        }
    }

    override suspend fun getAnnouncements(count: Int): Result<List<NightscoutAnnouncement>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = get(NightscoutAnnouncementEndpoint.latest(count))
                Result.success(announcementParser.parseAnnouncements(response))
            } catch (e: NightscoutAuthException) {
                Timber.w(e, "Nightscout auth failed while fetching announcements")
                Result.failure(e)
            } catch (e: JsonSyntaxException) {
                val wrapped = IllegalArgumentException("Malformed announcements response payload", e)
                Timber.e(wrapped)
                Result.failure(wrapped)
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch announcements")
                Result.failure(e)
            }
        }
    }

    override suspend fun getAnnouncementsSince(
        sinceTimestamp: Long?,
        sinceId: String?,
        count: Int
    ): Result<List<NightscoutAnnouncement>> {
        return withContext(Dispatchers.IO) {
            try {
                val endpoint = NightscoutAnnouncementEndpoint.since(
                    sinceTimestamp = sinceTimestamp,
                    sinceId = sinceId,
                    count = count
                )
                val response = get(endpoint)
                Result.success(announcementParser.parseAnnouncements(response))
            } catch (e: NightscoutAuthException) {
                Timber.w(e, "Nightscout auth failed while fetching announcements since marker")
                Result.failure(e)
            } catch (e: JsonSyntaxException) {
                val wrapped = IllegalArgumentException("Malformed announcements response payload", e)
                Timber.e(wrapped)
                Result.failure(wrapped)
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch announcements since marker")
                Result.failure(e)
            }
        }
    }

    private fun post(endpoint: String, body: Any): String {
        val url = URL("$baseUrl$endpoint")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty(NightscoutApiSecretHeader, apiSecretHash)
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(gson.toJson(body))
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                readResponseBody(connection.inputStream)
            } else {
                throw createHttpException(responseCode, readResponseBody(connection.errorStream))
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun get(endpoint: String): String {
        val url = URL("$baseUrl$endpoint")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty(NightscoutApiSecretHeader, apiSecretHash)
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                readResponseBody(connection.inputStream)
            } else {
                throw createHttpException(responseCode, readResponseBody(connection.errorStream))
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun put(endpoint: String, body: Any): String {
        val url = URL("$baseUrl$endpoint")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty(NightscoutApiSecretHeader, apiSecretHash)
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(gson.toJson(body))
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                readResponseBody(connection.inputStream)
            } else {
                throw createHttpException(responseCode, readResponseBody(connection.errorStream))
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readResponseBody(stream: InputStream?): String {
        if (stream == null) {
            return ""
        }
        val reader = BufferedReader(InputStreamReader(stream))
        return reader.use { it.readText() }
    }

    private fun createHttpException(responseCode: Int, errorBody: String): Exception {
        return if (responseCode == 401 || responseCode == 403) {
            NightscoutAuthException("Nightscout API auth failed ($responseCode): $errorBody")
        } else {
            Exception("Nightscout API error ($responseCode): $errorBody")
        }
    }
}
