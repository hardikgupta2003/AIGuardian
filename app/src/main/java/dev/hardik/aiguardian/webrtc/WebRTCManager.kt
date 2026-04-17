package dev.hardik.aiguardian.webrtc

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import org.webrtc.*
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import android.media.AudioRecord
import android.media.MediaRecorder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRTCManager @Inject constructor(
    private val context: Context
) {
    private val TAG = "VoIPManager"
    private val db = FirebaseDatabase.getInstance().reference
    
    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    
    private val audioExecutor = Executors.newSingleThreadExecutor()
    private val recordingExecutor = Executors.newSingleThreadExecutor()
    
    private var onAudioDataReceived: ((ByteArray) -> Unit)? = null
    private val notifiedCalls = mutableSetOf<String>()

    fun listenForIncomingCalls(myPin: String, onIncomingCall: (String) -> Unit) {
        db.child("users").child(myPin).child("incoming_calls").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { callSnapshot ->
                    val callId = callSnapshot.key ?: return@forEach
                    if (!notifiedCalls.contains(callId)) {
                        notifiedCalls.add(callId)
                        onIncomingCall(callId)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase error: ${error.message}")
            }
        })
    }

    fun initiateCall(myPin: String, targetPin: String): String {
        val callId = "call_${System.currentTimeMillis()}"
        db.child("users").child(targetPin).child("incoming_calls").child(callId).setValue(mapOf(
            "callerPin" to myPin,
            "timestamp" to System.currentTimeMillis()
        ))
        return callId
    }

    fun startCallAudio(callId: String, isCaller: Boolean, onAudioData: (ByteArray) -> Unit) {
        this.onAudioDataReceived = onAudioData
        
        val myAudioPath = if (isCaller) "audio_caller" else "audio_receiver"
        val peerAudioPath = if (isCaller) "audio_receiver" else "audio_caller"

        // Setup AudioTrack (Speaker)
        val minBufferSizeOut = AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(minBufferSizeOut)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            
        audioTrack?.play()

        // Setup AudioRecord (Microphone)
        val minBufferSizeIn = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        try {
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufferSizeIn * 2)
            audioRecord?.startRecording()
            isRecording = true
            
            recordingExecutor.execute {
                val buffer = ByteArray(4096)
                while (isRecording) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readSize > 0) {
                        val activeBuffer = buffer.copyOfRange(0, readSize)
                        val b64 = android.util.Base64.encodeToString(activeBuffer, android.util.Base64.NO_WRAP)
                        db.child("calls").child(callId).child(myAudioPath).push().setValue(b64)
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission denied for VoIP")
        }

        // Listen to Peer Audio
        db.child("calls").child(callId).child(peerAudioPath).addChildEventListener(object : com.google.firebase.database.ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val b64 = snapshot.value as? String ?: return
                snapshot.ref.removeValue() 
                try {
                    val data = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    audioExecutor.execute {
                        try {
                            audioTrack?.write(data, 0, data.size)
                            onAudioDataReceived?.invoke(data) // Feed OTHER person's voice into STT
                        } catch (e: Exception) {}
                    }
                } catch(e: Exception) {}
            }
            override fun onChildChanged(s: DataSnapshot, p: String?) {}
            override fun onChildRemoved(s: DataSnapshot) {}
            override fun onChildMoved(s: DataSnapshot, p: String?) {}
            override fun onCancelled(e: DatabaseError) {}
        })
    }

    fun endCall(myPin: String, callId: String) {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        
        db.child("users").child(myPin).child("incoming_calls").child(callId).removeValue()
        db.child("calls").child(callId).removeValue()
    }

    fun sendLiveTranscription(callId: String, text: String) {
        db.child("calls").child(callId).child("transcriptions").push().setValue(mapOf(
            "text" to text,
            "timestamp" to System.currentTimeMillis()
        ))
    }
}
