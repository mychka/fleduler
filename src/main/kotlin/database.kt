/**
 * A number of common general database related reinvented wheels.
 */

package com.kiko.flatviewingscheduler

import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * For our needs transaction just holds locks.
 */
private typealias Transaction = MutableList<Lock>

/**
 * We implement simple thread local transactions.
 */
private val currentTransaction = ThreadLocal<Transaction>()

/**
 * Run [block] within transaction, starting transaction if needed.
 */
fun <R> transactional(block: () -> R): R {
    currentTransaction.get()?.run {
        return block()
    }

    mutableListOf<Lock>().run {
        currentTransaction.set(this)
        try {
            return block()
        } finally {
            forEach {
                it.unlock()
            }
            currentTransaction.set(null)
        }
    }
}

class UniqueConstraintException : Exception()

class ForeignConstraintException : Exception()

open class Dao<E> {

    protected val entities = mutableListOf<E>()

    private var sequence = 1L

    private val lock = ReentrantReadWriteLock()

    /**
     * Must be called inside write lock.
     */
    val nextSequenceValue: Long get() = sequence++

    fun <R> read(block: () -> R): R {
        return transactional {
            currentTransaction.get() += lock.readLock().apply { lock() }
            block()
        }
    }

    fun <R> write(block: () -> R): R {
        return transactional {
            currentTransaction.get() += lock.writeLock().apply { lock() }
            block()
        }
    }
}
