package com.example.demoaudiorecord

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.google.firebase.database.getValue
import com.google.firebase.messaging.FirebaseMessaging
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private val audioData = ShortArray(bufferSize)
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var playbackThread: Thread? = null

    private var visualizerView: PlayerVisualizerView? = null

    private val recordRef by lazy {
        Firebase.database("https://fir-audio-record-3bd06-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("record")
    }
    private val playbackRef by lazy {
        Firebase.database("https://fir-audio-record-3bd06-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("record")
    }
    private val listener by lazy {
        object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.getValue<String>()?.let {
                    val shortArray = decodeStringToShortArray(it)
                    audioTrack?.write(shortArray, 0, shortArray.size)
                }
            }

            override fun onCancelled(error: DatabaseError) {

            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        createNotificationChannel()
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.wtf("TRUNGLE", "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result
            Log.wtf("TRUNGLE", token)
            Toast.makeText(baseContext, token, Toast.LENGTH_SHORT).show()
        })

        visualizerView = findViewById(R.id.visualizerView)
        val startStopButton: Button = findViewById(R.id.startStopButton)
        val playStopButton: Button = findViewById(R.id.playStopButton)

        startStopButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
                startStopButton.text = "Start Recording"
            } else {
                if (checkPermissions()) {
                    startRecording()
                    startPlayback()
                    startStopButton.text = "Stop Recording"
                }
            }
        }

        playStopButton.setOnClickListener {
            if (isPlaying) {
                stopPlayback()
                playStopButton.text = "Start Listening"
                playbackRef.removeEventListener(listener)
            } else {
                startPlayback()
                playStopButton.text = "Stop Listening"
                playbackRef.addValueEventListener(listener)
            }
        }
    }

    private fun checkPermissions(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS),
                1
            )
            return false
        }
        return true
    }

    private fun startRecording() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
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
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )


        val file = File(cacheDir, "recording.pcm")

        audioRecord?.startRecording()
        isRecording = true

        recordingThread = Thread {
            try {
                val outputStream = file.outputStream()

                while (isRecording) {
                    val bytesRead = audioRecord?.read(audioData, 0, bufferSize) ?: 0
                    if (bytesRead > 0) {
                        // Convert short array to byte array
//                        val byteData = ShortArrayToByteArray(audioData)
//                        outputStream.write(byteData, 0, byteData.size)
//                        audioTrack?.write(audioData, 0, bytesRead)
//                        visualizerView?.updateVisualizer(byteData)
                        recordRef.setValue(encodeShortArrayToString(audioData))
                    }
                }

                outputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        recordingThread?.start()
    }

    private fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recordingThread?.join()
    }

    private fun startPlayback() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            audioFormat
        )

        audioTrack = AudioTrack(
            android.media.AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            audioFormat,
            minBufferSize,
            AudioTrack.MODE_STREAM
        )

        audioTrack?.play()

        isPlaying = true
    }

    private fun stopPlayback() {
        isPlaying = false
        audioTrack?.stop()
        audioTrack?.release()
        playbackThread?.join()
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("VNPAY Test", "VNPAY Test", importance).apply {
                description = "VNPAY Test"
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

fun encodeShortArrayToString(shortArray: ShortArray, delimiter: String = ","): String {
    return shortArray.joinToString(delimiter)
}

fun decodeStringToShortArray(encodedString: String, delimiter: String = ","): ShortArray {
    val stringArray = encodedString.split(delimiter)
    return ShortArray(stringArray.size) { stringArray[it].toShort() }
}
