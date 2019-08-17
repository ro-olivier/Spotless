package fr.hqdu.spotless

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.content.*
import android.media.AudioManager
import android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import java.util.*
import android.content.Intent


class MainActivity : AppCompatActivity() {

    companion object {

        private const val TAG:String = "MainActivity"

        private const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"

        private const val MUTED_COUNT:String = "fr.hqdu.spotless.muted_count"
        private const val MUTED_TIME_COUNT:String = "fr.hqdu.spotless.muted_time_count"

        private const val spotifyPackageName:String = "com.spotify.music"

    }

    private var enableNotificationListenerAlertDialog: AlertDialog? = null
    private var notificationBroadcastReceiver: NotificationBroadcastReceiver? = NotificationBroadcastReceiver()

    private var imageViewMuteStatus: ImageView? = null
    private var textViewMuteStatus: TextView? = null
    private var imageButtonToSpotify: ImageButton? = null
    private var textViewServiceStatus: TextView? = null
    private var textViewCounter: TextView? = null

    private var audioManager: AudioManager? = null

    private var sharedPreferences: SharedPreferences? = null

    private var muted:Boolean = false
    private var mutedSince:Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences(packageName, Context.MODE_PRIVATE)

        imageViewMuteStatus = findViewById(R.id.imageView_muteStatus)
        textViewMuteStatus = findViewById(R.id.textView_muteStatus)
        imageButtonToSpotify = findViewById(R.id.imageButtonToSpotify)
        textViewServiceStatus = findViewById(R.id.textView_serviceStatus)
        textViewCounter = findViewById(R.id.textView_counter)

        val currentCounter = sharedPreferences!!.getInt(MUTED_COUNT, 0)
        val currentTimeCounter = sharedPreferences!!.getLong(MUTED_TIME_COUNT, 0)

        Log.d(TAG, "open app and read shared pref, currentCounter = $currentCounter")
        Log.d(TAG, "open app and read shared pref, currentTimeCounter = $currentTimeCounter")

        refreshCounterTextView(currentCounter, currentTimeCounter)

        if(!isNotificationServiceEnabled()){
            enableNotificationListenerAlertDialog = buildNotificationServiceAlertDialog()
            enableNotificationListenerAlertDialog!!.show()
        }

        notificationBroadcastReceiver = NotificationBroadcastReceiver()
        val intentFilter = IntentFilter()
        intentFilter.addAction("fr.hqdu.spotless")
        registerReceiver(notificationBroadcastReceiver,intentFilter)

        audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager?

        if(audioManager!!.isStreamMute(AudioManager.STREAM_MUSIC)) {
            imageViewMuteStatus!!.setImageResource(R.drawable.ic_volume_off_black_240dp)
        }else{
            imageViewMuteStatus!!.setImageResource(R.drawable.ic_volume_up_black_240dp)
        }
        
        textViewServiceStatus!!.text = getString(R.string.service_enabled)

        imageButtonToSpotify!!.setOnClickListener { openSpotify() }

    }

    fun openSpotify() {

        val pm = applicationContext.packageManager
        val appStartIntent = pm.getLaunchIntentForPackage(spotifyPackageName)
        appStartIntent!!.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        applicationContext.startActivities(arrayOf(appStartIntent))

    }

    private fun refreshCounterTextView(currentCounter:Int, currentTimeCounter:Long) {

        val localCurrentCounter:Int = currentCounter
        val localTimeCounter:Long = currentTimeCounter

        if(localCurrentCounter > 0 && localTimeCounter > 0){

            val niceTimeCounter:String = millisecondsToNiceString(localTimeCounter)

            Log.d(TAG, "Received order to update the counterTextView with $localCurrentCounter and $currentTimeCounter milliseconds ($niceTimeCounter).")
            textViewCounter!!.text = getString(R.string.counter_ads_blocked).format(localCurrentCounter, niceTimeCounter)
        }else{
            Log.d(TAG, "Initializing textView")
            textViewCounter!!.text = getString(R.string.counter_no_ads_blocked)
        }
    }

    private fun millisecondsToNiceString(localTimeCounter: Long): String {

        if (localTimeCounter >= 1000) {
            val seconds = (localTimeCounter / 1000) % 60
            val minutes = ((localTimeCounter / (1000 * 60)) % 60)
            val hours = ((localTimeCounter / (1000 * 60 * 60)) % 24)
            val days = (localTimeCounter / (1000 * 60 * 60 * 24))
            return if ((days == 0L) && (hours != 0L)) {
                String.format("%d hours %d minutes %d seconds", hours, minutes, seconds)
            } else if ((hours == 0L) && (minutes != 0L)) {
                String.format("%d minutes %d seconds", minutes, seconds)
            } else if ((days == 0L) && (hours == 0L) && (minutes == 0L)) {
                String.format("%d seconds", seconds)
            } else {
                String.format("%d days %d hours %d minutes %d seconds", days, hours, minutes, seconds)
            }
        } else {
            return "less than a second"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(notificationBroadcastReceiver)
    }

    @SuppressLint("SetTextI18n")
    private fun reactToInterception(isAd:Boolean) {

        // Here the actual logic will be implemented
        if(isAd){
            imageViewMuteStatus!!.setImageResource(R.drawable.ic_volume_off_black_240dp)

            audioManager!!.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)

            if(!muted){
                Toast.makeText(this, getString(R.string.toast_ad_detected_muting), Toast.LENGTH_SHORT).show()
            }
            textViewMuteStatus!!.text = getString(R.string.textView_ad_detected_muting)

            muted = true
            mutedSince = Date().time
            Log.d(TAG, "Notification was for an ad: muting. Current timestamp: $mutedSince")

        }else{
            textViewMuteStatus!!.text = getString(R.string.textView_song_detected_unmuting)
            if(muted){
                imageViewMuteStatus!!.setImageResource(R.drawable.ic_volume_up_black_240dp)

                audioManager!!.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)

                muted = false
                Log.d(TAG, "Notification was for a song: unmuting.")

                val counterNewValue:Int = sharedPreferences!!.getInt(MUTED_COUNT, 0) + 1
                sharedPreferences!!.edit().putInt(MUTED_COUNT, counterNewValue).apply()
                Log.d(TAG, "Read current counter from preferences and added 1: $counterNewValue")

                val additionalTimeCounter:Long = Date().time - mutedSince
                val timeCounterNewValue:Long = sharedPreferences!!.getLong(MUTED_TIME_COUNT, 0) + additionalTimeCounter
                sharedPreferences!!.edit().putLong(MUTED_TIME_COUNT, timeCounterNewValue).apply()
                Log.d(TAG, "Read current time counter from preferences and added the additionalTimeCounter ($additionalTimeCounter): $timeCounterNewValue")

                refreshCounterTextView(counterNewValue, timeCounterNewValue)

                Toast.makeText(this, getString(R.string.toast_song_detected_unmuting), Toast.LENGTH_SHORT).show()

            }
        }

    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(
            contentResolver,
            ENABLED_NOTIFICATION_LISTENERS
        )

        Log.d(TAG, "Enabled listeners: $flat")

        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (i in names.indices) {
                val cn = ComponentName.unflattenFromString(names[i])
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.packageName)) {
                        return true
                    }
                }
            }
        }
        return false
    }


    private fun buildNotificationServiceAlertDialog(): AlertDialog {
        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.setTitle(getString(R.string.app_name))
        alertDialogBuilder.setMessage(getString(R.string.permissions_request_explanation))
        alertDialogBuilder.setPositiveButton("Yes"
        ) { _, _ -> startActivity(Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        alertDialogBuilder.setNegativeButton("No"
        ) { _, _ ->
            updateStatusTextViewNoPermission()
        }
        return alertDialogBuilder.create()
    }

    private fun updateStatusTextViewNoPermission() {
        textViewServiceStatus!!.text = getString(R.string.permissions_not_given)
    }


    inner class NotificationBroadcastReceiver : BroadcastReceiver() {

        private val TAG:String = "BroadcastReceiver"

        init {
            Log.d(TAG, "Broadcast receiver was created.")
        }

        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Received broadcast notice that a notification was intercepted.")
            reactToInterception(intent.getBooleanExtra("isAd", false))
        }

    }
}
