package com.myapp.familycode.receiver

/**
 * Shared deduplication tracker between OtpReceiver (BroadcastReceiver) and
 * SmsObserverService (ContentObserver). Prevents the same OTP from being
 * processed twice when both detection paths succeed.
 */
object OtpDeduplicator {
    private val processedIds = mutableSetOf<String>()
    private val processedTimestamps = mutableMapOf<String, Long>()
    private const val EXPIRY_MS = 30_000L // 30 seconds

    /**
     * Tries to mark a message as processed. Returns true if this is a NEW message
     * (not yet processed), false if it was already handled.
     *
     * @param key A unique key for the message (e.g., "sender|otpCode" or SMS _id)
     */
    @Synchronized
    fun tryProcess(key: String): Boolean {
        cleanup()
        return if (processedIds.contains(key)) {
            false
        } else {
            processedIds.add(key)
            processedTimestamps[key] = System.currentTimeMillis()
            true
        }
    }

    @Synchronized
    private fun cleanup() {
        val now = System.currentTimeMillis()
        val expired = processedTimestamps.filter { (now - it.value) > EXPIRY_MS }.keys
        expired.forEach {
            processedIds.remove(it)
            processedTimestamps.remove(it)
        }
    }
}
