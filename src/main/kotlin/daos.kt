/**
 * App's database model and data access objects.
 */

package com.kiko.flatviewingscheduler

import java.time.LocalDateTime

/**
 * A person who lives in a flat or who is moving in.
 */
data class Tenant(val id: Long, val name: String)

data class Flat(val id: Long, val address: String, val currentTenant: Tenant)

/**
 * Null [approved] means that this slot is neither approved nor rejected so far.
 */
data class Reservation(val id: Long, val flat: Flat, val dateTime: LocalDateTime, val prospectiveTenant: Tenant, val approved: Boolean?)

object TenantDao : Dao<Tenant>() {

    fun insert(name: String): Tenant = write {
        // Ensure unique name
        entities.find { it.name == name }?.run { throw UniqueConstraintException() }

        Tenant(nextSequenceValue, name).apply {
            entities += this
        }
    }

    fun findById(id: Long): Tenant? = read { entities.find { it.id == id } }

    fun findAll() = read { entities.toList() }

    fun deleteAll() = write {
        FlatDao.read {
            if (FlatDao.findAll().isNotEmpty()) {
                throw ForeignConstraintException()
            }
            entities.clear()
        }
    }
}

object FlatDao : Dao<Flat>() {

    fun insert(address: String, currentTenantId: Long): Flat = write {
        TenantDao.read {
            val currentTenant = TenantDao.findAll().find { it.id == currentTenantId } ?: throw ForeignConstraintException()

            Flat(nextSequenceValue, address, currentTenant).apply {
                entities += this
            }
        }
    }

    fun findById(id: Long): Flat? = read { entities.find { it.id == id } }

    fun findAll(): List<Flat> = read { entities.toList() }

    fun deleteAll() = write {
        ReservationDao.read {
            if (ReservationDao.findAll().isNotEmpty()) {
                throw ForeignConstraintException()
            }
            entities.clear()
        }
    }
}

object ReservationDao : Dao<Reservation>() {

    fun insert(flatId: Long, dateTime: LocalDateTime, prospectiveTenantId: Long): Reservation = write {
        FlatDao.read {
            val flat = FlatDao.findById(flatId) ?: throw ForeignConstraintException()

            entities.find { it.flat == flat && it.dateTime == dateTime }?.run { throw UniqueConstraintException() }

            TenantDao.read {
                val prospectiveTenant = prospectiveTenantId.run { TenantDao.findById(this) ?: throw ForeignConstraintException() }

                Reservation(nextSequenceValue, flat, dateTime, prospectiveTenant, null).apply {
                    entities += this
                }
            }
        }
    }

    fun findById(reservationId: Long): Reservation? = read {
        entities.find { it.id == reservationId }
    }

    fun findByFlatAndDateTimeRange(flatId: Long, range: ClosedRange<LocalDateTime>): List<Reservation> = read {
        entities.filter { it.flat.id == flatId && it.dateTime in range }
    }

    fun findAll(): List<Reservation> = read { entities.toList() }

    fun setApproved(reservationId: Long, approved: Boolean): Reservation = write {
        val index = entities.indexOfFirst { it.id == reservationId }
        require(index > -1)

        TenantDao.read {
            entities.get(index).copy(
                approved = approved
            ).apply {
                entities.set(index, this)
            }
        }
    }

    fun delete(reservationId: Long): Boolean = write {
        entities.removeIf { it.id == reservationId }
    }

    fun deleteAll() = write {
        entities.clear()
    }
}
