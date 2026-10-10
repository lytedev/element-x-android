/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.sections.custom

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.roomlist.RoomListService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The room list sections settings shared with Element Web through the `im.vector.web.settings` account data.
 *
 * Element Web overwrites the whole event on each change, so every edit here re-reads the stored content and only replaces the section keys.
 * Edits wait for the first sync: before it, the account data may not be loaded yet, and writing then would erase every setting of
 * Element Web, which is the bug element-hq/element-web#34936 fixed on their side.
 */
@Inject
@SingleIn(SessionScope::class)
class RoomListSectionsSettingsStore(
    private val matrixClient: MatrixClient,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) {
    private val editLock = Mutex()
    private val _settingsFlow = MutableStateFlow(RoomListSectionsSettings.DEFAULT)
    val settingsFlow: StateFlow<RoomListSectionsSettings> = _settingsFlow

    private val isFollowingTheStoredSettings = AtomicBoolean(false)

    fun startFollowingTheStoredSettingsIfNeeded() {
        if (!isFollowingTheStoredSettings.compareAndSet(false, true)) return
        matrixClient.accountDataChangesFlow(ElementWebRoomListSettings.ACCOUNT_DATA_EVENT_TYPE)
            .onEach { rawContent -> _settingsFlow.value = ElementWebRoomListSettingsCodec.decode(ElementWebRoomListSettingsCodec.parseContent(rawContent)) }
            .launchIn(sessionCoroutineScope)
        sessionCoroutineScope.launch {
            readStoredSettings()
            awaitFirstSync()
            readStoredSettings()
        }
    }

    private suspend fun awaitFirstSync() {
        matrixClient.roomListService.state.first { state -> state == RoomListService.State.Running }
    }

    private suspend fun readStoredSettings() {
        matrixClient.getAccountData(ElementWebRoomListSettings.ACCOUNT_DATA_EVENT_TYPE)
            .onSuccess { rawContent -> _settingsFlow.value = ElementWebRoomListSettingsCodec.decode(ElementWebRoomListSettingsCodec.parseContent(rawContent)) }
            .onFailure { Timber.w(it, "Could not read the room list sections settings") }
    }

    suspend fun edit(transform: (RoomListSectionsSettings) -> RoomListSectionsSettings): Result<Unit> = editLock.withLock {
        awaitFirstSync()
        val storedRawContent = matrixClient.getAccountData(ElementWebRoomListSettings.ACCOUNT_DATA_EVENT_TYPE).getOrElse { return@withLock Result.failure(it) }
        val storedContent = ElementWebRoomListSettingsCodec.parseContent(storedRawContent)
        val storedSettings = ElementWebRoomListSettingsCodec.decode(storedContent)
        val editedSettings = transform(storedSettings)
        if (editedSettings == storedSettings) return@withLock Result.success(Unit)
        val editedContent = ElementWebRoomListSettingsCodec.withSettings(storedContent, editedSettings)
        matrixClient.setAccountData(ElementWebRoomListSettings.ACCOUNT_DATA_EVENT_TYPE, ElementWebRoomListSettingsCodec.encodeContent(editedContent))
            .onSuccess { _settingsFlow.value = editedSettings }
    }
}
