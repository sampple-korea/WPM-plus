package com.sampple.wifivaultrestore.data.restore

import com.sampple.wifivaultrestore.data.WifiCredential

data class RestoreBatch(
    val id: Long,
    val items: List<WifiCredential>,
)

data class RestoreSession(
    val id: String,
    val queue: List<WifiCredential>,
    val cursor: Int = 0,
    val submitted: Int = 0,
    val success: Int = 0,
    val alreadyExists: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val startedAtMillis: Long = System.currentTimeMillis(),
    val activeBatch: RestoreBatch? = null,
) {
    val total: Int = queue.size
    val done: Boolean = cursor >= queue.size && activeBatch == null
    val remaining: Int = (queue.size - cursor).coerceAtLeast(0)

    fun nextBatch(batchSize: Int = 5): RestoreSession {
        if (activeBatch != null || cursor >= queue.size) return this
        val batch = queue.drop(cursor).take(batchSize)
        return copy(
            cursor = cursor + batch.size,
            activeBatch = RestoreBatch(
                id = System.nanoTime(),
                items = batch,
            ),
        )
    }

    fun completeActiveBatch(
        success: Int,
        alreadyExists: Int,
        failed: Int,
    ): RestoreSession {
        val batchSize = activeBatch?.items?.size ?: 0
        return copy(
            submitted = submitted + batchSize,
            success = this.success + success,
            alreadyExists = this.alreadyExists + alreadyExists,
            failed = this.failed + failed,
            activeBatch = null,
        )
    }
}
