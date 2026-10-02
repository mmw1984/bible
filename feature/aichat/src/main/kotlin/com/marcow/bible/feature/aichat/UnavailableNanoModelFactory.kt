package com.marcow.bible.feature.aichat

import com.marcow.bible.core.network.aicore.NanoAvailability
import com.marcow.bible.core.network.aicore.NanoException
import com.marcow.bible.core.network.aicore.NanoGate
import com.marcow.bible.core.network.aicore.NanoModel
import com.marcow.bible.core.network.aicore.NanoModelFactory
import javax.inject.Inject

/**
 * The [NanoModelFactory] the chat is bound to until the Play services for AI Edge client lands.
 *
 * It reports the on-device model as not downloaded and refuses to open one, which is the honest
 * answer for a build with no AICore client on its classpath: there is nothing that could have put
 * weights on the phone, so they are by definition not there. The gate is [NanoGate.ModelNotDownloaded]
 * rather than a permanent refusal so the settings row draws the transient "downloadable" state —
 * the same state `AiAvailability` in `legacy/flutter/lib/ai_service.dart` already names — instead of
 * presenting the phone as too old or too small.
 *
 * Deliberately free of Android calls (`Build.VERSION`, `PackageManager`) so it stays constructible
 * in a JVM unit test: the real factory will need both to tell [NanoGate.UnsupportedOsVersion] and
 * [NanoGate.ServiceMissing] apart, and when it arrives this binding is the one line that changes.
 */
class UnavailableNanoModelFactory @Inject constructor() : NanoModelFactory {
    override suspend fun availability(): NanoAvailability = NanoAvailability.Gated(GATE)

    override suspend fun open(): NanoModel = throw NanoException.Gated(GATE)

    private companion object {
        val GATE = NanoGate.ModelNotDownloaded
    }
}
