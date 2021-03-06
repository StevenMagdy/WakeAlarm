package com.steven.wakealarm

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.preference.PreferenceManager
import android.support.v4.app.NotificationCompat
import android.util.Log
import com.steven.wakealarm.utils.NOTIFICATION_CHANNEL_ID
import com.steven.wakealarm.utils.is21OrLater
import com.steven.wakealarm.utils.setVolume
import java.lang.Exception
import kotlin.math.log

class AlarmService : Service() {

	private val TAG: String = "AlarmService"

	override fun onBind(intent: Intent?): IBinder? = null

	private lateinit var mp: MediaPlayer
	private var currentStep = 0.0
	private val maxStep = 50.0
	private val VOLUME_STEP_DELAY = 1000L
	private var originalVolume: Int = 0
	private val handler = Handler()
	private val am: AudioManager by lazy {
		getSystemService(Context.AUDIO_SERVICE) as AudioManager
	}
	private val sharedPreferences: SharedPreferences by lazy {
		PreferenceManager.getDefaultSharedPreferences(this)
	}
	private val fadeInRunnable = object : Runnable {
		override fun run() {
			am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager
					.STREAM_ALARM), 0)
			if (mp.isPlaying && currentStep < maxStep) {
				currentStep += 1
				val vol = getVolumeForStep().toFloat()
				mp.setVolume(vol)
				Log.d(TAG, vol.toString())
			}
			handler.postDelayed(this, VOLUME_STEP_DELAY)
		}
	}

	override fun onCreate() {
		super.onCreate()
		Log.d(TAG, "Service Created")
		originalVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		startForegroundService()
		val i = Intent(this, AlarmActivity::class.java)
		i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
				Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
				Intent.FLAG_ACTIVITY_SINGLE_TOP
		startActivity(i)
		playAlarm()
		return START_STICKY
	}

	override fun onDestroy() {
		handler.removeCallbacksAndMessages(null)
		mp.stop()
		mp.release()
		am.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
		super.onDestroy()
		Log.d(TAG, "Service Destroyed")
	}

	private fun playAlarm() {
		val ringuri = sharedPreferences.getString("ringtone", "")
		Log.d(TAG, ringuri)


		mp = MediaPlayer()
		try {
			mp.setDataSource(this, Uri.parse(ringuri))
		} catch (e: Exception) {
			stopSelf()
			return
		}
		mp.isLooping = true

		if (is21OrLater()) {
			mp.setAudioAttributes(AudioAttributes.Builder()
					.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
					.setUsage(AudioAttributes.USAGE_ALARM)
					.build())
		} else {
			mp.setAudioStreamType(AudioManager.STREAM_ALARM)
		}
		mp.setVolume(0f)
		mp.prepare()
		mp.start()
		am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager
				.STREAM_ALARM), 0)
		handler.postDelayed(fadeInRunnable, VOLUME_STEP_DELAY)
	}

	private fun startForegroundService() {

		val intent = Intent(this, AlarmActivity::class.java)
		intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
				Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
				Intent.FLAG_ACTIVITY_SINGLE_TOP
		val notificationPendingIntent = PendingIntent.getActivity(this, 11,
				intent, 0)

		val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
				.setContentTitle("Wake Up")
				.setSmallIcon(R.mipmap.ic_launcher)
				.setPriority(NotificationCompat.PRIORITY_MAX)
				.setContentText("Tap to Dismiss Alarm")
				.setContentIntent(notificationPendingIntent)
				.setAutoCancel(false)
				.setOngoing(true) as NotificationCompat.Builder

		startForeground(1111, notificationBuilder.build())
	}

	fun getVolumeForStep() = Math.pow(10.0, log((currentStep / maxStep), 4.0))
	//fun getVolumeForStep() = (Math.exp(currentVolume / maxVolume) - 1) / (Math.E - 1)
}

