package dev.hardik.aiguardian.stt

import dev.hardik.aiguardian.detection.TranscriptSegment
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class TranscriptHub @Inject constructor() {
    private val _transcripts = MutableSharedFlow<TranscriptSegment>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val transcripts: SharedFlow<TranscriptSegment> = _transcripts.asSharedFlow()

    fun tryEmit(segment: TranscriptSegment): Boolean = _transcripts.tryEmit(segment)
}
