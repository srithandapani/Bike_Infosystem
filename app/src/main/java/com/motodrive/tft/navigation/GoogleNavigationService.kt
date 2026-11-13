package com.motodrive.tft.navigation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.core.app.NotificationCompat
import com.google.android.libraries.mapsplatform.turnbyturn.TurnByTurnManager
import com.google.android.libraries.mapsplatform.turnbyturn.model.Maneuver
import com.google.android.libraries.mapsplatform.turnbyturn.model.NavInfo
import com.google.android.libraries.mapsplatform.turnbyturn.model.NavState
import com.motodrive.tft.MainActivity
import com.motodrive.tft.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NavStepUpdate(
    val instruction: String = "",
    val roadName: String = "",
    val maneuverDirection: TurnDirection = TurnDirection.STRAIGHT,
    val distanceToTurnMeters: Int = 0,
    val remainingDistanceMeters: Int = 0,
    val remainingTimeSeconds: Int = 0,
    val isNavigating: Boolean = false
)

class GoogleNavigationService : Service() {

    private lateinit var messenger: Messenger
    private val turnByTurnManager: TurnByTurnManager by lazy { TurnByTurnManager.createInstance() }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                if (msg.what == TurnByTurnManager.MSG_NAV_INFO) {
                    val navInfo: NavInfo? = turnByTurnManager.readNavInfoFromBundle(msg.data)
                    navInfo?.let { processNavInfo(it) }
                }
            }
        }
        messenger = Messenger(handler)
        startForegroundWithNotification("TVS Ronin Navigation", "Active guidance ready")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            _navUpdates.value = NavStepUpdate()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return messenger.binder
    }

    private fun processNavInfo(navInfo: NavInfo) {
        val step = navInfo.currentStep
        val distanceToStep = navInfo.distanceToCurrentStepMeters ?: 0
        val remainingDist = navInfo.distanceToDestinationMeters ?: 0
        val remainingSec = navInfo.timeToDestinationSeconds ?: 0
        val instruction = step?.fullInstructionText ?: ""
        val road = step?.fullRoadName ?: ""

        val direction = mapManeuverToDirection(step?.maneuver)

        val update = NavStepUpdate(
            instruction = instruction,
            roadName = road,
            maneuverDirection = direction,
            distanceToTurnMeters = distanceToStep,
            remainingDistanceMeters = remainingDist,
            remainingTimeSeconds = remainingSec,
            isNavigating = navInfo.navState == NavState.ENROUTE
        )
        _navUpdates.value = update

        // Update ongoing foreground notification
        updateNotification(instruction, "${distanceToStep}m ahead - $road")
    }

    private fun mapManeuverToDirection(maneuver: Int?): TurnDirection {
        if (maneuver == null) return TurnDirection.STRAIGHT
        return when (maneuver) {
            Maneuver.TURN_LEFT -> TurnDirection.TURN_LEFT
            Maneuver.TURN_SHARP_LEFT -> TurnDirection.SHARP_LEFT
            Maneuver.TURN_SLIGHT_LEFT -> TurnDirection.SLIGHT_LEFT
            Maneuver.TURN_RIGHT -> TurnDirection.TURN_RIGHT
            Maneuver.TURN_SHARP_RIGHT -> TurnDirection.SHARP_RIGHT
            Maneuver.TURN_SLIGHT_RIGHT -> TurnDirection.SLIGHT_RIGHT
            Maneuver.ROUNDABOUT_CLOCKWISE,
            Maneuver.ROUNDABOUT_COUNTERCLOCKWISE,
            Maneuver.ROUNDABOUT_EXIT_CLOCKWISE,
            Maneuver.ROUNDABOUT_EXIT_COUNTERCLOCKWISE,
            Maneuver.ROUNDABOUT_STRAIGHT_CLOCKWISE,
            Maneuver.ROUNDABOUT_STRAIGHT_COUNTERCLOCKWISE,
            Maneuver.ROUNDABOUT_LEFT_CLOCKWISE,
            Maneuver.ROUNDABOUT_LEFT_COUNTERCLOCKWISE,
            Maneuver.ROUNDABOUT_RIGHT_CLOCKWISE,
            Maneuver.ROUNDABOUT_RIGHT_COUNTERCLOCKWISE,
            Maneuver.ROUNDABOUT_SLIGHT_LEFT_CLOCKWISE,
            Maneuver.ROUNDABOUT_SLIGHT_LEFT_COUNTERCLOCKWISE,
            Maneuver.ROUNDABOUT_SLIGHT_RIGHT_CLOCKWISE,
            Maneuver.ROUNDABOUT_SLIGHT_RIGHT_COUNTERCLOCKWISE,
            Maneuver.ROUNDABOUT_SHARP_LEFT_CLOCKWISE,
            Maneuver.ROUNDABOUT_SHARP_LEFT_COUNTERCLOCKWISE,
            Maneuver.ROUNDABOUT_SHARP_RIGHT_CLOCKWISE,
            Maneuver.ROUNDABOUT_SHARP_RIGHT_COUNTERCLOCKWISE,
            Maneuver.ROUNDABOUT_U_TURN_CLOCKWISE,
            Maneuver.ROUNDABOUT_U_TURN_COUNTERCLOCKWISE -> TurnDirection.ROUNDABOUT
            Maneuver.TURN_U_TURN_CLOCKWISE,
            Maneuver.TURN_U_TURN_COUNTERCLOCKWISE -> TurnDirection.U_TURN
            Maneuver.DESTINATION,
            Maneuver.DESTINATION_LEFT,
            Maneuver.DESTINATION_RIGHT -> TurnDirection.DESTINATION
            else -> TurnDirection.STRAIGHT
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Motorcycle Navigation Guidance",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live turn-by-turn guidance on TVS Ronin cluster"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification(title: String, text: String) {
        val notification = buildNotification(title, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(title: String, text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun buildNotification(title: String, text: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_motodrive_logo)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "motodrive_navigation_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP = "com.motodrive.tft.navigation.STOP_GUIDANCE"

        private val _navUpdates = MutableStateFlow(NavStepUpdate())
        val navUpdates: StateFlow<NavStepUpdate> = _navUpdates.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, GoogleNavigationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, GoogleNavigationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
