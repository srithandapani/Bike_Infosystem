package com.motodrive.tft.connectivity

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.IntentCompat
import com.motodrive.tft.MainActivity

class BluetoothAutoLaunchReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
            val device: BluetoothDevice? = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            val deviceName = try {
                device?.name ?: "Unknown Bluetooth Device"
            } catch (e: SecurityException) {
                "Motorcycle Device"
            }

            Log.d("MotoDriveTFT", "Bluetooth Connected: $deviceName")

            // Check if auto-launch is enabled and start MainActivity in landscape
            try {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("AUTO_LAUNCHED_BY_BT", true)
                    putExtra("BT_DEVICE_NAME", deviceName)
                }
                context.startActivity(launchIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

