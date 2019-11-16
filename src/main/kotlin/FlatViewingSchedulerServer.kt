package com.kiko.flatviewingscheduler

import com.kiko.flatviewingscheduler.Method.*
import java.time.LocalDateTime
import java.time.temporal.ChronoField.DAY_OF_WEEK
import java.time.temporal.ChronoUnit.MINUTES

fun main() {
    FlatViewingSchedulerServer.ensureStarted()
}

object FlatViewingSchedulerServer {

    private var started = false

    @Synchronized
    fun ensureStarted() {
        if (started) {
            return
        }

        RestServer.registerContext("/tenants", GET) {
            it.append("[").append(TenantDao.findAll().joinToString {
                """{"id": ${it.id}, "name": ${it.name.quote()}}"""
            }).append("]")
        }.registerContext("/tenants", POST) {
            it.apply {
                append(
                    TenantDao.insert(bodyParams["name"]!! as String).id
                )
            }
        }.registerContext("/flats", GET) {
            it.append("[").append(FlatDao.findAll().joinToString {
                """{"id": ${it.id}, "address": ${it.address.quote()}, "currentTenantId": ${it.currentTenant.id}}"""
            }).append("]")
        }.registerContext("/flats", POST) {
            it.apply {
                append(
                    FlatDao.insert(bodyParams["address"]!! as String, (bodyParams["currentTenantId"]!! as Number).toLong()).id
                )
            }
        }.registerContext("/flats/(\\d+)", GET) {
            it.apply {
                val flat = FlatDao.findById(pathParams[0])
                flat?.run {
                    append("""{"id": $id, "address": ${address.quote()}, "currentTenantId": ${currentTenant.id}}""")
                }
                if (flat == null) {
                    responseCode = 404
                }
            }
        }.registerContext("/reservations", GET) {
            it.apply {
                val from = LocalDateTime.parse(queryParams["from"])
                val to = LocalDateTime.parse(queryParams["to"])
                append("[").append(ReservationDao.findByFlatAndDateTimeRange(queryParams["flatId"]!!.toLong(), from..to).joinToString {
                    """{"id": ${it.id}, "flatId": ${it.flat.id}, "dateTime": "${it.dateTime}", "prospectiveTenantId": ${it.prospectiveTenant.id}, "approved": ${it.approved}}"""
                }).append("]")
            }
        }.registerContext("/reservations", POST) {
            it.apply {
                val dateTime = LocalDateTime.parse(bodyParams["dateTime"] as String)
                require(dateTime in upcomingWeekRange())
                require(dateTime.minute in 0..40 step 20 && dateTime.second == 0 && dateTime.nano == 0) // Check date format is correct
                require(LocalDateTime.now().plusDays(1) < dateTime) // Current Tenant should be notified about reservation in at least 24 hours

                val flat = FlatDao.findById(
                    (bodyParams["flatId"] as Number).toLong()
                )!!
                append(
                    ReservationDao.insert(flat.id, dateTime, (bodyParams["prospectiveTenantId"] as Number).toLong()).id
                )

                notifyTenant(flat.currentTenant, """Please, approve or reject reservation at "${flat.address}" on $dateTime.""")
            }
        }.registerContext("/reservations/(\\d+)", PUT) {
            it.apply {
                val reservation = ReservationDao.findById(pathParams[0])!!
                val approved = bodyParams["approved"] as Boolean
                ReservationDao.setApproved(reservation.id, approved)
                responseCode = 204
                notifyTenant(
                    reservation.prospectiveTenant,
                    """Your reservation at ${reservation.flat.address} on ${reservation.dateTime} has been ${if (approved) "approved" else "rejected"}."""
                )
            }
        }.registerContext("/reservations/(\\d+)", DELETE) {
            it.apply {
                val reservation = ReservationDao.findById(pathParams[0])!!
                require(reservation.approved != false) // Once rejected this slot can not be reserved by anyone else at any point
                ReservationDao.delete(reservation.id)
                responseCode = 204
            }
        }.start()

        started = true
    }
}

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
