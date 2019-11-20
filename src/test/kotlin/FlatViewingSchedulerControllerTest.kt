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

class FlatViewingSchedulerControllerTest {

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

    private var currentReservationId: Long? = null

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

        currentReservationId = null

        FlatViewingSchedulerController.ensureStarted()
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
    fun `create tenant`() {
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
    fun `create flat`() {
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
    fun `create reservation`() {
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
    fun `create reservations concurrently`() = runBlocking {
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
    fun `approve or reject reservation`() {
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
    fun `cancel reservation`() {
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

    @Test
    fun `check reservation datetime range and format`() {
        // 2019-11-29T10:01
        assertEquals(
            400,
            sendPost(
                "/reservations",
                """{"flatId": ${downingFlat.id}, "dateTime": "${weekRange.start.plusDays(4).plusMinutes(1)}", "prospectiveTenantId": ${louisArmstrong.id}}"""
            ).statusCode()
        )

        // 2019-11-29T10:10
        assertEquals(
            400,
            sendPost(
                "/reservations",
                """{"flatId": ${downingFlat.id}, "dateTime": "${weekRange.start.plusDays(4).plusMinutes(10)}", "prospectiveTenantId": ${louisArmstrong.id}}"""
            ).statusCode()
        )

        // 2019-11-27T10:00:11
        assertEquals(
            400,
            sendPost(
                "/reservations",
                """{"flatId": ${downingFlat.id}, "dateTime": "${weekRange.start.plusDays(2).plusSeconds(11)}", "prospectiveTenantId": ${louisArmstrong.id}}"""
            ).statusCode()
        )

        // 2019-11-26T10:00:00.000000003
        assertEquals(
            400,
            sendPost(
                "/reservations",
                """{"flatId": ${downingFlat.id}, "dateTime": "${weekRange.start.plusDays(1).plusNanos(3)}", "prospectiveTenantId": ${louisArmstrong.id}}"""
            ).statusCode()
        )

        // 2019-11-25T09:40
        assertEquals(
            400,
            sendPost(
                "/reservations",
                """{"flatId": ${downingFlat.id}, "dateTime": "${weekRange.start.minusMinutes(20)}", "prospectiveTenantId": ${louisArmstrong.id}}"""
            ).statusCode()
        )

        // 2019-11-25T20:00
        assertEquals(
            400,
            sendPost(
                "/reservations",
                """{"flatId": ${downingFlat.id}, "dateTime": "${weekRange.start.plusHours(10)}", "prospectiveTenantId": ${louisArmstrong.id}}"""
            ).statusCode()
        )

        // 2019-12-01T20:00
        assertEquals(
            400,
            sendPost(
                "/reservations",
                """{"flatId": ${downingFlat.id}, "dateTime": "${weekRange.endInclusive.plusMinutes(20)}", "prospectiveTenantId": ${louisArmstrong.id}}"""
            ).statusCode()
        )
    }

    fun reserve(expectSuccess: Boolean = true) = sendPost(
        "/reservations",
        """{"flatId": ${downingFlat.id}, "dateTime": "${weekRange.start.plusDays(4)}", "prospectiveTenantId": ${louisArmstrong.id}}"""
    ).apply {
        if (expectSuccess) {
            assertEquals(200, statusCode())
            currentReservationId = body().toLong()
        } else {
            assertEquals(400, statusCode())
        }
    }


    fun cancel(expectSuccess: Boolean = true) = assertEquals(
        if (expectSuccess) 204 else 400,
        sendRequest(
            "/reservations/${currentReservationId!!}", DELETE, ""
        ).statusCode()
    )

    fun approve(expectSuccess: Boolean = true) = assertEquals(
        if (expectSuccess) 204 else 400,
        sendRequest(
            "/reservations/${currentReservationId!!}",
            PUT,
            """{"approved": true}"""
        ).statusCode()
    )

    fun reject(expectSuccess: Boolean = true) = assertEquals(
        if (expectSuccess) 204 else 400,
        sendRequest(
            "/reservations/${currentReservationId!!}",
            PUT,
            """{"approved": false}"""
        ).statusCode()
    )

    @Test
    fun `reserve - reserve`() {
        reserve()
        reserve(false)
    }

    @Test
    fun `reserve - cancel - reserve`() {
        reserve()
        cancel()
        reserve()
    }

    @Test
    fun `reserve - cancel - cancel`() {
        reserve()
        cancel()
        cancel(false)
    }

    @Test
    fun `reserve - cancel - approve`() {
        reserve()
        cancel()
        approve(false)
    }

    @Test
    fun `reserve - cancel - reject`() {
        reserve()
        cancel()
        reject(false)
    }

    @Test
    fun `reserve - approve - reserve`() {
        reserve()
        approve()
        reserve(false)
    }

    @Test
    fun `reserve - approve - cancel`() {
        reserve()
        approve()
        cancel()
    }

    @Test
    fun `reserve - approve - approve`() {
        reserve()
        approve()
        approve(false)
    }

    @Test
    fun `reserve - approve - reject`() {
        reserve()
        approve()
        reject()
    }

    @Test
    fun `reserve - reject - reserve`() {
        reserve()
        reject()
        reserve(false)
    }

    @Test
    fun `reserve - reject - cancel`() {
        reserve()
        reject()
        cancel(false)
    }

    @Test
    fun `reserve - reject - approve`() {
        reserve()
        reject()
        approve()
    }

    @Test
    fun `reserve - reject - reject`() {
        reserve()
        reject()
        reject(false)
    }
}