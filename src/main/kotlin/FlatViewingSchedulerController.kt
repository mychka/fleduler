package com.kiko.flatviewingscheduler

import com.kiko.flatviewingscheduler.Method.*
import java.time.LocalDateTime
import java.time.temporal.ChronoField.DAY_OF_WEEK
import java.time.temporal.ChronoUnit.MINUTES

fun main() {
    FlatViewingSchedulerController.ensureStarted()
}

class FlatViewingSchedulerController(context: HandleContext) : Controller(context) {

    @Mapping("/tenants")
    fun getTenants() {
        append("[").append(TenantDao.findAll().joinToString {
            """{"id": ${it.id}, "name": ${it.name.quote()}}"""
        }).append("]")
    }

    @Mapping(value = "/tenants", method = POST)
    fun createTenant() {
        append(
            TenantDao.insert(bodyParams["name"]!! as String).id
        )
    }

    @Mapping("/flats")
    fun getFlats() {
        append("[").append(FlatDao.findAll().joinToString {
            """{"id": ${it.id}, "address": ${it.address.quote()}, "currentTenantId": ${it.currentTenant.id}}"""
        }).append("]")
    }

    @Mapping("/flats/(\\d+)")
    fun getFlat() {
        val flat = FlatDao.findById(pathParams[0])
        flat?.run {
            append("""{"id": $id, "address": ${address.quote()}, "currentTenantId": ${currentTenant.id}}""")
        }
        if (flat == null) {
            responseCode = 404
        }
    }

    @Mapping(value = "/flats", method = POST)
    fun createFlat() {
        append(
            FlatDao.insert(bodyParams["address"]!! as String, (bodyParams["currentTenantId"]!! as Number).toLong()).id
        )
    }

    @Mapping("/reservations")
    fun getReservations() {
        val from = LocalDateTime.parse(queryParams["from"])
        val to = LocalDateTime.parse(queryParams["to"])
        append("[").append(ReservationDao.findByFlatAndDateTimeRange(queryParams["flatId"]!!.toLong(), from..to).joinToString {
            """{"id": ${it.id}, "flatId": ${it.flat.id}, "dateTime": "${it.dateTime}", "prospectiveTenantId": ${it.prospectiveTenant.id}, "approved": ${it.approved}}"""
        }).append("]")
    }

    @Mapping(value = "/reservations", method = POST)
    fun createReservation() {
        println(bodyParams["dateTime"])
        val dateTime = LocalDateTime.parse(bodyParams["dateTime"] as String)
        require(dateTime in upcomingWeekRange())
        require(dateTime.hour in 10..19 && dateTime.minute in 0..40 step 20 && dateTime.second == 0 && dateTime.nano == 0) // Check date format is correct
        require(LocalDateTime.now().plusDays(1) < dateTime) // Current Tenant should be notified about reservation in at least 24 hours

        val flat = FlatDao.findById(
            (bodyParams["flatId"] as Number).toLong()
        )!!
        append(
            ReservationDao.insert(flat.id, dateTime, (bodyParams["prospectiveTenantId"] as Number).toLong()).id
        )

        notifyTenant(flat.currentTenant, """Please, approve or reject reservation at "${flat.address}" on $dateTime.""")
    }

    @Mapping(value = "/reservations/(\\d+)", method = PUT)
    fun approveOrRejectReservation() {
        val reservation = ReservationDao.findById(pathParams[0])!!
        val approved = bodyParams["approved"] as Boolean
        require(reservation.approved != approved)
        ReservationDao.setApproved(reservation.id, approved)
        responseCode = 204
        notifyTenant(
            reservation.prospectiveTenant,
            """Your reservation at ${reservation.flat.address} on ${reservation.dateTime} has been ${if (approved) "approved" else "rejected"}."""
        )
    }

    @Mapping(value = "/reservations/(\\d+)", method = DELETE)
    fun cancelReservation() {
        val reservation = ReservationDao.findById(pathParams[0])!!
        require(reservation.approved != false) // Once rejected this slot can not be reserved by anyone else at any point
        ReservationDao.delete(reservation.id)
        responseCode = 204
    }

    companion object {

        private var started = false

        @Synchronized
        fun ensureStarted() {
            if (!started) {
                RestServer.registerController(FlatViewingSchedulerController::class).start()
                started = true
            }
        }
    }
}

/**
 * A stub for notifications.
 */
private fun notifyTenant(tenant: Tenant, text: String) {
    println("Dear ${tenant.name},\n$text")
}

fun upcomingWeekRange() = LocalDateTime.now().plusWeeks(1).truncatedTo(MINUTES).run {
    with(DAY_OF_WEEK, 1).withHour(10).withMinute(0)..with(DAY_OF_WEEK, 7).withHour(19).withMinute(40)
}

/**
 * Returns JSON string quoted.
 */
private fun String.quote() = StringBuilder().append('"').also {
    for (c in this) {
        it.append(
            when (c) {
                '\\', '"' -> "\\$c"
                '\b' -> "\\b"
                '\t' -> "\\t"
                '\n' -> "\\n"
                '\u000C' -> "\\f"
                '\r' -> "\\r"
                in '\u0000'..'\u001f', in '\u0080'..'\u00a0', in '\u2000'..'\u2100' ->
                    "\\u${c.toInt().toString(16).padStart(4, '0')}"
                else -> c
            }
        )
    }
}.append('"').toString()
