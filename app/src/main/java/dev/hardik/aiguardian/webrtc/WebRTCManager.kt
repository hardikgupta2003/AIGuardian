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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRTCManager @Inject constructor(
    private val context: Context
) {
    private val TAG = "WebRTCManager"
    private val db = FirebaseDatabase.getInstance().getReference("calls")
    
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var audioTrack: AudioTrack? = null
    private val audioExecutor = Executors.newSingleThreadExecutor()
    
    private var onAudioDataReceived: ((ByteArray) -> Unit)? = null
    private var onCallStateChanged: ((String) -> Unit)? = null

    private val notifiedCalls = mutableSetOf<String>()

    init {
        initPeerConnectionFactory()
    }

    private fun initPeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val factoryOptions = PeerConnectionFactory.Options()
        val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(null, true, true)
        val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(null)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(factoryOptions)
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .createPeerConnectionFactory()
    }

    fun listenForIncomingCalls(onIncomingCall: (String) -> Unit) {
        db.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { callSnapshot ->
                    val offer = callSnapshot.child("offer").value
                    val answer = callSnapshot.child("answer").value
                    val callId = callSnapshot.key ?: return@forEach
                    
                    if (offer != null && answer == null && !notifiedCalls.contains(callId)) {
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

    fun answerCall(callId: String, onAudioData: (ByteArray) -> Unit) {
        this.onAudioDataReceived = onAudioData
        
        // Initialize AudioTrack for playing caller audio aloud
        val minBufferSize = AudioTrack.getMinBufferSize(
            16000, 
            AudioFormat.CHANNEL_OUT_MONO, 
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(16000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            
        audioTrack?.play()

        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        )

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE Connection State: $state")
                onCallStateChanged?.invoke(state.toString())
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    val candidateMap = mapOf(
                        "sdp" to it.sdp,
                        "sdpMid" to it.sdpMid,
                        "sdpMLineIndex" to it.sdpMLineIndex
                    )
                    db.child(callId).child("answerCandidates").push().setValue(candidateMap)
                }
            }
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(dc: DataChannel?) {
                Log.d(TAG, "Remote DataChannel received")
                dc?.registerObserver(object : DataChannel.Observer {
                    override fun onBufferedAmountChange(p0: Long) {}
                    override fun onStateChange() {
                        Log.d(TAG, "DataChannel State: ${dc.state()}")
                    }
                    override fun onMessage(buffer: DataChannel.Buffer?) {
                        buffer?.let {
                            if (it.data.remaining() == 0) return@let
                            val data = ByteArray(it.data.remaining())
                            it.data.get(data)
                            
                            // Offload blocking AudioTrack operations from the WebRTC signaling thread
                            audioExecutor.execute {
                                try {
                                    audioTrack?.write(data, 0, data.size)
                                    onAudioDataReceived?.invoke(data)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Audio write error", e)
                                }
                            }
                        }
                    }
                })
                dataChannel = dc
            }
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })

        // Fetch the offer
        db.child(callId).child("offer").get().addOnSuccessListener { snapshot ->
            val sdp = snapshot.child("sdp").value as? String ?: return@addOnSuccessListener
            val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdp)
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onSetSuccess() {
                    createAnswer(callId)
                }
                override fun onCreateFailure(p0: String?) {}
                override fun onSetFailure(p0: String?) {}
            }, sessionDescription)
        }

        // Listen for remote ICE candidates
        db.child(callId).child("offerCandidates").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { candidateSnapshot ->
                    val sdp = candidateSnapshot.child("sdp").value as? String ?: return@forEach
                    val sdpMid = candidateSnapshot.child("sdpMid").value as? String ?: return@forEach
                    val sdpMLineIndex = candidateSnapshot.child("sdpMLineIndex").value?.toString()?.toIntOrNull() ?: return@forEach
                    val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                    peerConnection?.addIceCandidate(iceCandidate)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // FIREBASE AUDIO RELAY (GUARANTEED FALLBACK FOR WEBRTC FIREWALL FAILURES)
        db.child(callId).child("audio").addChildEventListener(object : com.google.firebase.database.ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val b64 = snapshot.value as? String ?: return
                snapshot.ref.removeValue() // Delete instantly to prevent Firebase quota explosion
                
                try {
                    val data = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    audioExecutor.execute {
                        try {
                            audioTrack?.write(data, 0, data.size)
                            onAudioDataReceived?.invoke(data)
                        } catch (e: Exception) {
                            Log.e(TAG, "Audio write error", e)
                        }
                    }
                } catch(e: Exception) {
                    Log.e(TAG, "Base64 decode error", e)
                }
            }
            override fun onChildChanged(s: DataSnapshot, p: String?) {}
            override fun onChildRemoved(s: DataSnapshot) {}
            override fun onChildMoved(s: DataSnapshot, p: String?) {}
            override fun onCancelled(e: DatabaseError) {}
        })
    }

    private fun createAnswer(callId: String) {
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        val answerMap = mapOf("type" to "answer", "sdp" to desc?.description)
                        db.child(callId).child("answer").setValue(answerMap)
                    }
                    override fun onSetFailure(p0: String?) {}
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, desc)
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    fun endCall(callId: String) {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        
        dataChannel?.close()
        peerConnection?.close()
        peerConnection = null
        db.child(callId).removeValue()
    }

    fun sendLiveTranscription(callId: String, text: String) {
        db.child(callId).child("transcriptions").push().setValue(mapOf(
            "text" to text,
            "timestamp" to System.currentTimeMillis()
        ))
    }
}
