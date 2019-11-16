package com.kiko.flatviewingscheduler

import com.kiko.flatviewingscheduler.Method.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.testng.annotations.BeforeMethod
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class FlatViewingSchedulerServerTest {

    private val client = HttpClient.newBuilder().build()!!

    private lateinit var johnDow: Tenant
    private lateinit var annaLee: Tenant
    private lateinit var jerryMaguire: Tenant
    private lateinit var stephenKing: Tenant
    private lateinit var louisArmstrong: Tenant

    private lateinit var downingFlat: Flat
    private lateinit var bakerFlat: Flat

    private lateinit var reserv1: Reservation
    private lateinit var reserv2: Reservation
    private lateinit var reserv3: Reservation
    private lateinit var reserv4: Reservation

    private lateinit var weekRange: ClosedRange<LocalDateTime>
    private lateinit var mon: LocalDate
    private lateinit var fri: LocalDate
    private lateinit var sun: LocalDate

    /**
     * Put database into an initial state before every test method.
     */
    @BeforeMethod
    fun setupEnvironment() {
        ReservationDao.deleteAll()
        FlatDao.deleteAll()
        TenantDao.deleteAll()

        johnDow = TenantDao.insert("John Dow")
        annaLee = TenantDao.insert("Anna Lee")
        jerryMaguire = TenantDao.insert("Jerry Maguire")
        stephenKing = TenantDao.insert("Stephen King")
        louisArmstrong = TenantDao.insert("Louis Armstrong")

        downingFlat = FlatDao.insert("10 Downing Street", annaLee.id)
        bakerFlat = FlatDao.insert("221B Baker Street", johnDow.id)

        weekRange = upcomingWeekRange()
        mon = weekRange.start.toLocalDate()
        fri = weekRange.start.plusDays(4).toLocalDate()
        sun = weekRange.endInclusive.toLocalDate()

        reserv1 = ReservationDao.insert(bakerFlat.id, weekRange.start, johnDow.id)
        reserv2 = ReservationDao.insert(bakerFlat.id, weekRange.endInclusive, johnDow.id)
        reserv3 = ReservationDao.insert(downingFlat.id, weekRange.start.plusMinutes(20), annaLee.id)
        reserv4 = ReservationDao.insert(downingFlat.id, weekRange.endInclusive.minusDays(2), annaLee.id)

        FlatViewingSchedulerServer.ensureStarted()
    }

    private fun sendGet(path: String) = sendRequest(path, GET, "")
    private fun sendPost(path: String, body: String) = sendRequest(path, POST, body)

    private fun sendRequest(path: String, method: Method, body: String): HttpResponse<String> =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${RestServer.PORT}$path"))
            .apply {
                when (method) {
                    GET -> GET()
                    POST -> POST(BodyPublishers.ofString(body))
                    PUT -> PUT(BodyPublishers.ofString(body))
                    DELETE -> DELETE()
                }
            }
            .build().run {
                client.send(this, BodyHandlers.ofString())
            }

    @Test
    fun `get tenants`() = assertEquals(
        """[{"id": ${johnDow.id}, "name": "John Dow"}, {"id": ${annaLee.id}, "name": "Anna Lee"}, {"id": ${jerryMaguire.id}, "name": "Jerry Maguire"}, {"id": ${stephenKing.id}, "name": "Stephen King"}, {"id": ${louisArmstrong.id}, "name": "Louis Armstrong"}]""",
        sendGet("/tenants").body()
    )

    @Test
    fun `post tenant`() {
        assertEquals(
            "${louisArmstrong.id + 1}",
            sendPost("/tenants", """{"name": "Agatha Christie"}""").body()
        )

        assertEquals(
            """[{"id": ${johnDow.id}, "name": "John Dow"}, {"id": ${annaLee.id}, "name": "Anna Lee"}, {"id": ${jerryMaguire.id}, "name": "Jerry Maguire"}, {"id": ${stephenKing.id}, "name": "Stephen King"}, {"id": ${louisArmstrong.id}, "name": "Louis Armstrong"}, {"id": ${louisArmstrong.id + 1}, "name": "Agatha Christie"}]""",
            sendGet("/tenants").body()
        )
    }

    @Test
    fun `get flats`() = assertEquals(
        """[{"id": ${downingFlat.id}, "address": "10 Downing Street", "currentTenantId": ${annaLee.id}}, {"id": ${bakerFlat.id}, "address": "221B Baker Street", "currentTenantId": ${johnDow.id}}]""",
        sendGet("/flats").body()
    )

    @Test
    fun `get flat`() {
        assertEquals(
            """{"id": ${downingFlat.id}, "address": "10 Downing Street", "currentTenantId": ${annaLee.id}}""",
            sendGet("/flats/${downingFlat.id}").body()
        )

        assertEquals(404, sendGet("/flats/100500").statusCode())
    }

    @Test
    fun `post flat`() {
        assertEquals(
            "${bakerFlat.id + 1}",
            sendPost("/flats", """{"address": "32 Windsor Gardens", "currentTenantId": ${johnDow.id}}""").body()
        )

        assertEquals(
            """[{"id": ${downingFlat.id}, "address": "10 Downing Street", "currentTenantId": ${annaLee.id}}, {"id": ${bakerFlat.id}, "address": "221B Baker Street", "currentTenantId": ${johnDow.id}}, {"id": ${bakerFlat.id + 1}, "address": "32 Windsor Gardens", "currentTenantId": ${johnDow.id}}]""",
            sendGet("/flats").body()
        )
    }

    @Test
    fun `get reservations`() {
        assertEquals(
            """[{"id": ${reserv3.id}, "flatId": ${downingFlat.id}, "dateTime": "${mon}T10:20", "prospectiveTenantId": ${annaLee.id}, "approved": null}, {"id": ${reserv4.id}, "flatId": ${downingFlat.id}, "dateTime": "${fri}T19:40", "prospectiveTenantId": ${annaLee.id}, "approved": null}]""",
            sendGet("/reservations?flatId=${downingFlat.id}&from=${mon}T10:00&to=${fri}T19:40").body()
        )

        assertEquals(
            """[{"id": ${reserv1.id}, "flatId": ${bakerFlat.id}, "dateTime": "${mon}T10:00", "prospectiveTenantId": ${johnDow.id}, "approved": null}, {"id": ${reserv2.id}, "flatId": ${bakerFlat.id}, "dateTime": "${sun}T19:40", "prospectiveTenantId": ${johnDow.id}, "approved": null}]""",
            sendGet("/reservations?flatId=${bakerFlat.id}&from=${mon}T10:00&to=${sun}T19:40").body()
        )

        assertEquals(
            """[]""",
            sendGet("/reservations?flatId=100500&from=${mon}T10:00&to=${sun}T19:40").body()
        )
    }

    @Test
    fun `post reservation`() {
        assertEquals(
            200,
            sendPost(
                "/reservations",
                """{"flatId": ${downingFlat.id}, "dateTime": "${weekRange.start.plusDays(4)}", "prospectiveTenantId": ${louisArmstrong.id}}"""
            ).statusCode()
        )

        assertEquals(
            """[{"id": ${reserv3.id}, "flatId": ${downingFlat.id}, "dateTime": "${mon}T10:20", "prospectiveTenantId": ${annaLee.id}, "approved": null}, {"id": ${reserv4.id}, "flatId": ${downingFlat.id}, "dateTime": "${fri}T19:40", "prospectiveTenantId": ${annaLee.id}, "approved": null}, {"id": ${reserv4.id + 1}, "flatId": ${downingFlat.id}, "dateTime": "${fri}T10:00", "prospectiveTenantId": ${louisArmstrong.id}, "approved": null}]""",
            sendGet("/reservations?flatId=${downingFlat.id}&from=${mon}T10:00&to=${fri}T19:40").body()
        )
    }

    @Test
    fun `post reservations concurrently`() = runBlocking {
        val count200 = AtomicInteger()
        val count400 = AtomicInteger()

        val body = """{"flatId": ${downingFlat.id}, "dateTime": "${weekRange.start.plusDays(4)}", "prospectiveTenantId": ${louisArmstrong.id}}"""

        (1..100).map {
            GlobalScope.launch {
                when (sendPost("/reservations", body).statusCode()) {
                    200 -> count200.getAndIncrement()
                    400 -> count400.getAndIncrement()
                }
            }
        }.forEach { it.join() }

        assertEquals(1, count200.get())
        assertEquals(99, count400.get())
    }

    @Test
    fun `update reservation`() {
        assertEquals(
            204,
            sendRequest(
                "/reservations/${reserv1.id}",
                PUT,
                """{"approved": true}"""
            ).statusCode()
        )

        assertEquals(
            204,
            sendRequest(
                "/reservations/${reserv2.id}",
                PUT,
                """{"approved": false}"""
            ).statusCode()
        )

        assertEquals(
            """[{"id": ${reserv1.id}, "flatId": ${bakerFlat.id}, "dateTime": "${mon}T10:00", "prospectiveTenantId": ${johnDow.id}, "approved": true}, {"id": ${reserv2.id}, "flatId": ${bakerFlat.id}, "dateTime": "${sun}T19:40", "prospectiveTenantId": ${johnDow.id}, "approved": false}]""",
            sendGet("/reservations?flatId=${bakerFlat.id}&from=${mon}T10:00&to=${sun}T19:40").body()
        )
    }

    @Test
    fun `delete reservation`() {
        assertEquals(
            204,
            sendRequest(
                "/reservations/${reserv1.id}",
                DELETE,
                ""
            ).statusCode()
        )

        assertEquals(
            204,
            sendRequest(
                "/reservations/${reserv2.id}",
                DELETE,
                ""
            ).statusCode()
        )

        assertEquals(
            "[]",
            sendGet("/reservations?flatId=${bakerFlat.id}&from=${mon}T10:00&to=${sun}T19:40").body()
        )
    }
}