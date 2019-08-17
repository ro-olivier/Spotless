package fr.hqdu.spotless

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationListener : NotificationListenerService() {

    companion object {

        private const val TAG = "NotificationListener"
        private const val spotifyPackageName:String = "com.spotify.music"

    }

    override fun onNotificationPosted(p0: StatusBarNotification?) {

        Log.d(TAG, "I've seen a notification")
        Log.d(TAG, p0.toString())

        if(p0!!.packageName == spotifyPackageName){

            val intent = Intent("fr.hqdu.spotless")
            val notification = p0.notification

            if(notification.actions.size == 5){
                Log.d(TAG, "5 actions item detected, this is just a song change, not an ad")
                intent.putExtra("isAd", false)

            }else{
                Log.d(TAG, "Received a notification with less or more than 5 actions, so this is probably an ad!")
                intent.putExtra("isAd", true)
            }

        sendBroadcast(intent)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
    }

    override fun onCreate() {
        Log.d(TAG, "Service onCreate")
    }

}