package com.kingmc.flexmusic.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MediaNotificationReceiver : BroadcastReceiver() {

    @Inject
    lateinit var playerController: Media3PlayerController

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("FlexMusic.NotificationReceiver", "Received action: ${intent.action}")
        when (intent.action) {
            "com.kingmc.flexmusic.PLAY_PAUSE" -> {
                playerController.playOrPause()
            }
            "com.kingmc.flexmusic.SKIP_NEXT" -> {
                playerController.skipNext()
            }
            "com.kingmc.flexmusic.SKIP_PREVIOUS" -> {
                playerController.skipPrevious()
            }
            "com.kingmc.flexmusic.STOP" -> {
                playerController.getPlayer().stop()
                playerController.getPlayer().clearMediaItems()
            }
        }
    }
}
