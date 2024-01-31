package com.example.demoaudiorecord

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.google.firebase.database.getValue
import java.io.File
import java.io.IOException
import kotlin.experimental.and

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
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
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

    private fun ShortArrayToByteArray(data: ShortArray): ByteArray {
        val byteArray = ByteArray(data.size * 2)
        var index = 0

        for (value in data) {
            byteArray[index++] = (value and 0xFF).toByte()
            byteArray[index++] = ((value.toInt() shr 8) and 0xFF).toByte()
        }

        return byteArray
    }

    private fun ByteArrayToShortArray(data: ByteArray, bytesRead: Int): ShortArray {
        val shortArray = ShortArray(bytesRead / 2)
        var index = 0

        for (i in 0 until bytesRead step 2) {
            val value = (data[i].toInt() and 0xFF) or ((data[i + 1].toInt() and 0xFF) shl 8)
            shortArray[index++] = value.toShort()
        }

        return shortArray
    }
}

fun encodeShortArrayToString(shortArray: ShortArray, delimiter: String = ","): String {
    return shortArray.joinToString(delimiter)
}

fun decodeStringToShortArray(encodedString: String, delimiter: String = ","): ShortArray {
    val stringArray = encodedString.split(delimiter)
    return ShortArray(stringArray.size) { stringArray[it].toShort() }
}
