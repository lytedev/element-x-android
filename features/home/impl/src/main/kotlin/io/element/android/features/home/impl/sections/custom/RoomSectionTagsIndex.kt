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
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.tags.RoomTagName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The tags of every room that is in at least one custom section, as the SDK room lists can only filter on favourite and low priority tags.
 *
 * The SDK only reports tag changes, so each room is also read once from the local store the first time the room list shows it.
 */
@Inject
@SingleIn(SessionScope::class)
class RoomSectionTagsIndex(
    private val matrixClient: MatrixClient,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) {
    private val _tagsOfRoomsInCustomSections = MutableStateFlow<Map<RoomId, Set<RoomTagName>>>(emptyMap())
    val tagsOfRoomsInCustomSections: StateFlow<Map<RoomId, Set<RoomTagName>>> = _tagsOfRoomsInCustomSections

    private val isIndexing = AtomicBoolean(false)
    private val roomIdsReadFromStore = mutableSetOf<RoomId>()

    fun startIndexingIfNeeded() {
        if (!isIndexing.compareAndSet(false, true)) return
        matrixClient.roomTagsChangesFlow
            .onEach { change -> recordTags(change.roomId, change.tags.keys) }
            .launchIn(sessionCoroutineScope)
        matrixClient.roomListService.allRooms.summaries
            .onEach { roomSummaries ->
                val roomIdsNotReadYet = roomSummaries.map { it.roomId }.filter { roomIdsReadFromStore.add(it) }
                roomIdsNotReadYet.forEach { roomId -> readTagsFromStore(roomId) }
            }
            .launchIn(sessionCoroutineScope)
    }

    private suspend fun readTagsFromStore(roomId: RoomId) {
        matrixClient.getRoom(roomId)?.use { room ->
            room.tags().onSuccess { tags -> recordTags(roomId, tags.keys) }
        }
    }

    /**
     * Lets the list update right away when this device moves a room, instead of waiting for the sync to echo the change.
     */
    fun recordTags(roomId: RoomId, tags: Set<RoomTagName>) {
        val isInACustomSection = tags.any(ElementWebRoomListSettings::isCustomSectionTag)
        _tagsOfRoomsInCustomSections.update { tagsByRoom ->
            if (isInACustomSection) tagsByRoom + (roomId to tags) else tagsByRoom - roomId
        }
    }

    fun tagsOf(roomId: RoomId): Set<RoomTagName>? = _tagsOfRoomsInCustomSections.value[roomId]
}
