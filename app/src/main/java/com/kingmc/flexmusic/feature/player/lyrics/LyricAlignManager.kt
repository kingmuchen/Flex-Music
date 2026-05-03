package com.kingmc.flexmusic.feature.player.lyrics

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricAlignManager @Inject constructor() {

    private var _userFixOffset: Long = 0L
    
    val userFixOffset: Long get() = _userFixOffset
    val totalOffset: Long get() = _userFixOffset

    fun updateUserFixOffset(offset: Long) {
        _userFixOffset = offset
        Log.d(TAG, "User fix offset updated: ${offset}ms")
    }

    fun adjustUserFixOffset(delta: Long) {
        _userFixOffset += delta
        Log.d(TAG, "User fix offset adjusted by ${delta}ms, now: ${_userFixOffset}ms")
    }

    fun resetAllOffsets() {
        _userFixOffset = 0L
        Log.d(TAG, "All offsets reset")
    }

    fun getAdjustedPosition(currentPos: Long): Long {
        return currentPos + totalOffset
    }

    fun getOffsetInfo(): OffsetInfo {
        return OffsetInfo(
            userFixOffset = _userFixOffset,
            totalOffset = totalOffset
        )
    }

    companion object {
        private const val TAG = "LyricAlignManager"
    }
}

data class OffsetInfo(
    val userFixOffset: Long,
    val totalOffset: Long
)
