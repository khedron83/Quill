package com.khedron.quill.data.caldav

import android.util.Xml
import com.khedron.quill.domain.model.QuillTask
import com.khedron.quill.domain.model.TaskCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Singleton
class CalDAVClient @Inject constructor(private val baseClient: OkHttpClient) {

    private var serverUrl = ""
    private var username = ""
    private var password = ""
    private var verifySSL = true

    fun configure(serverUrl: String, username: String, password: String, verifySSL: Boolean = true) {
        this.serverUrl = serverUrl.trimEnd('/')
        this.username = username
        this.password = password
        this.verifySSL = verifySSL
    }

    private fun client(): OkHttpClient {
        if (verifySSL) return baseClient
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        val sslCtx = SSLContext.getInstance("SSL").apply { init(null, trustAll, SecureRandom()) }
        return baseClient.newBuilder()
            .sslSocketFactory(sslCtx.socketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    private val calendarsUrl get() = "$serverUrl/remote.php/dav/calendars/$username/"
    private fun creds() = Credentials.basic(username, password)

    // ── Public API ───────────────────────────────────────────────────────────

    suspend fun getCalendars(): List<TaskCalendar> = withContext(Dispatchers.IO) {
        val body = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
  <d:prop>
    <d:displayname/>
    <d:resourcetype/>
    <c:supported-calendar-component-set/>
  </d:prop>
</d:propfind>"""
        val req = Request.Builder()
            .url(calendarsUrl)
            .header("Authorization", creds())
            .header("Depth", "1")
            .method("PROPFIND", body.toRequestBody(XML_TYPE))
            .build()
        val resp = client().newCall(req).execute()
        parsePropfindCalendars(resp.body?.string() ?: "")
    }

    suspend fun getTasks(calendarUrl: String): List<QuillTask> = withContext(Dispatchers.IO) {
        val body = """<?xml version="1.0" encoding="utf-8"?>
<c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
  <d:prop>
    <d:getetag/>
    <c:calendar-data/>
  </d:prop>
  <c:filter>
    <c:comp-filter name="VCALENDAR">
      <c:comp-filter name="VTODO"/>
    </c:comp-filter>
  </c:filter>
</c:calendar-query>"""
        val req = Request.Builder()
            .url(calendarUrl)
            .header("Authorization", creds())
            .header("Depth", "1")
            .method("REPORT", body.toRequestBody(XML_TYPE))
            .build()
        val resp = client().newCall(req).execute()
        parseReportTasks(resp.body?.string() ?: "")
    }

    suspend fun createTask(calendarUrl: String, task: QuillTask): Boolean = withContext(Dispatchers.IO) {
        val url = "${calendarUrl.trimEnd('/')}/${task.uid}.ics"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", creds())
            .put(buildIcal(task).toRequestBody(ICAL_TYPE))
            .build()
        val resp = client().newCall(req).execute()
        resp.code in 200..204
    }

    suspend fun updateTask(task: QuillTask): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(task.href)
            .header("Authorization", creds())
            .put(buildIcal(task).toRequestBody(ICAL_TYPE))
            .build()
        val resp = client().newCall(req).execute()
        resp.code in 200..204
    }

    suspend fun deleteTask(task: QuillTask): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(task.href)
            .header("Authorization", creds())
            .delete()
            .build()
        val resp = client().newCall(req).execute()
        resp.code in 200..204
    }

    suspend fun createCalendar(name: String): Boolean = withContext(Dispatchers.IO) {
        val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "calendar" }
        val url = "${calendarsUrl}$slug/"
        val body = """<?xml version="1.0" encoding="utf-8"?>
<c:mkcalendar xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
  <d:set>
    <d:prop>
      <d:displayname>$name</d:displayname>
      <c:supported-calendar-component-set>
        <c:comp name="VTODO"/>
      </c:supported-calendar-component-set>
    </d:prop>
  </d:set>
</c:mkcalendar>"""
        val req = Request.Builder()
            .url(url)
            .header("Authorization", creds())
            .method("MKCALENDAR", body.toRequestBody(XML_TYPE))
            .build()
        val resp = client().newCall(req).execute()
        resp.code in 200..204 || resp.code == 201
    }

    // ── XML parsers ──────────────────────────────────────────────────────────

    private fun parsePropfindCalendars(xml: String): List<TaskCalendar> {
        if (xml.isBlank()) return emptyList()
        val result = mutableListOf<TaskCalendar>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(xml.reader())

            var inResponse = false
            var href = ""
            var displayName = ""
            var isCalendar = false
            var hasVtodo = false
            var currentTag = ""

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.localName
                        when (parser.localName) {
                            "response" -> {
                                inResponse = true; href = ""; displayName = ""
                                isCalendar = false; hasVtodo = false
                            }
                            "calendar" -> if (inResponse) isCalendar = true
                            "comp" -> if (inResponse && parser.getAttributeValue(null, "name") == "VTODO") hasVtodo = true
                        }
                    }
                    XmlPullParser.TEXT -> if (inResponse) when (currentTag) {
                        "href" -> href = parser.text.trim()
                        "displayname" -> displayName += parser.text
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.localName == "response" && isCalendar && hasVtodo && href.isNotBlank()) {
                            val full = if (href.startsWith("http")) href else "$serverUrl$href"
                            val url = if (full.endsWith("/")) full else "$full/"
                            result.add(TaskCalendar(
                                name = displayName.trim().ifBlank { href.trimEnd('/').substringAfterLast('/') },
                                url = url,
                            ))
                        }
                        if (parser.localName == "response") inResponse = false
                        currentTag = ""
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {}
        return result
    }

    private fun parseReportTasks(xml: String): List<QuillTask> {
        if (xml.isBlank()) return emptyList()
        val result = mutableListOf<QuillTask>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(xml.reader())

            var inResponse = false
            var inCalData = false
            var href = ""
            var currentTag = ""
            val calData = StringBuilder()

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.localName
                        when (parser.localName) {
                            "response" -> { inResponse = true; href = ""; calData.clear() }
                            "calendar-data" -> { inCalData = true; calData.clear() }
                        }
                    }
                    XmlPullParser.TEXT -> when {
                        inCalData -> calData.append(parser.text)
                        currentTag == "href" && inResponse -> href = parser.text.trim()
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.localName == "calendar-data") inCalData = false
                        if (parser.localName == "response" && inResponse) {
                            val fullHref = if (href.startsWith("http")) href else "$serverUrl$href"
                            parseIcal(calData.toString(), fullHref)?.let { result.add(it) }
                            inResponse = false
                        }
                        currentTag = ""
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {}
        return result
    }

    // ── iCal ─────────────────────────────────────────────────────────────────

    private fun buildIcal(task: QuillTask): String = buildString {
        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        appendLine("PRODID:-//Quill Android//EN")
        appendLine("BEGIN:VTODO")
        appendLine("UID:${task.uid}")
        appendLine("SUMMARY:${task.summary.escIcal()}")
        if (task.description.isNotBlank()) appendLine("DESCRIPTION:${task.description.escIcal()}")
        task.dueDate?.let { appendLine("DUE;VALUE=DATE:${it.format(DateTimeFormatter.BASIC_ISO_DATE)}") }
        appendLine("STATUS:${if (task.completed) "COMPLETED" else "NEEDS-ACTION"}")
        if (task.completed) appendLine("PERCENT-COMPLETE:100")
        appendLine("END:VTODO")
        appendLine("END:VCALENDAR")
    }

    private fun parseIcal(ical: String, href: String): QuillTask? {
        val lines = unfold(ical)
        var inTodo = false
        var uid = ""; var summary = ""; var description = ""
        var dueDate: LocalDate? = null; var completed = false

        for (line in lines) {
            val ci = line.indexOf(':')
            if (ci < 0) continue
            val rawProp = line.substring(0, ci)
            val value = line.substring(ci + 1)
            val prop = rawProp.substringBefore(';').uppercase()
            when {
                line == "BEGIN:VTODO" -> inTodo = true
                line == "END:VTODO" -> inTodo = false
                inTodo -> when (prop) {
                    "UID" -> uid = value.unescIcal()
                    "SUMMARY" -> summary = value.unescIcal()
                    "DESCRIPTION" -> description = value.unescIcal()
                    "STATUS" -> if (value.trim().uppercase() == "COMPLETED") completed = true
                    "DUE" -> dueDate = parseDue(rawProp, value)
                }
            }
        }
        if (uid.isBlank() || summary.isBlank()) return null
        return QuillTask(uid = uid, summary = summary, description = description, dueDate = dueDate, completed = completed, href = href)
    }

    private fun unfold(ical: String) =
        ical.replace(Regex("\r\n[ \t]"), "").replace(Regex("\n[ \t]"), "").lines()

    private fun parseDue(rawProp: String, value: String): LocalDate? = try {
        val v = value.trim()
        if (rawProp.uppercase().contains("VALUE=DATE") || v.length == 8) {
            LocalDate.parse(v, DateTimeFormatter.BASIC_ISO_DATE)
        } else {
            LocalDate.parse(v.take(8), DateTimeFormatter.BASIC_ISO_DATE)
        }
    } catch (_: DateTimeParseException) { null }

    private fun String.escIcal() = replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")
    private fun String.unescIcal() = replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    companion object {
        private val XML_TYPE = "application/xml".toMediaType()
        private val ICAL_TYPE = "text/calendar; charset=utf-8".toMediaType()
    }
}
