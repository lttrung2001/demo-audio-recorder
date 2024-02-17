package com.example.demoaudiorecord

import android.Manifest
import android.app.Notification
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson

class MessengerService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.wtf("TRUNGLE", Gson().toJson(message))
        Notification.Builder(this, "VNPAY Test")
            .setContentTitle("VNPAY")
            .setContentText("Handle ")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .also {
                with(NotificationManagerCompat.from(applicationContext)) {
                    // notificationId is a unique int for each notification that you must define
                    if (ActivityCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return
                    }
                    notify(0, it.build())
                }
            }
    }
}