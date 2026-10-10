/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.datasource

import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.libraries.androidutils.diff.DiffCacheUpdater
import io.element.android.libraries.androidutils.diff.MutableListDiffCache
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.roomlist.DynamicRoomList
import io.element.android.libraries.matrix.api.roomlist.RoomListFilter
import io.element.android.libraries.matrix.api.roomlist.RoomListService
import io.element.android.libraries.matrix.api.roomlist.RoomSummary
import io.element.android.libraries.matrix.api.roomlist.updateVisibleRange
import io.element.android.services.analytics.api.AnalyticsService
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val EXTENDED_VISIBILITY_RANGE_SIZE = 40
private const val SUBSCRIBE_TO_VISIBLE_ROOMS_DEBOUNCE_IN_MILLIS = 300L

/**
 * Turns the [RoomSummary] list of one [DynamicRoomList] into [RoomListRoomSummary] items, paginates it and subscribes to its visible rooms.
 */
class DynamicRoomListSummariesSource(
    private val dynamicRoomList: DynamicRoomList,
    private val paginationThreshold: Int,
    private val roomListService: RoomListService,
    private val roomListRoomSummaryFactory: RoomListRoomSummaryFactory,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val analyticsService: AnalyticsService,
    private val sdkFilterNarrowingTheUserSelectedFilter: (RoomListFilter) -> RoomListFilter = { userSelectedFilter -> userSelectedFilter },
    private val isRoomKeptAmongRoomsMatchingTheSdkFilter: (RoomListRoomSummary) -> Boolean = { true },
) {
    private val _roomSummariesFlow = MutableSharedFlow<ImmutableList<RoomListRoomSummary>>(replay = 1)

    private val lock = Mutex()
    private val diffCache = MutableListDiffCache<RoomListRoomSummary>()
    private val diffCacheUpdater = DiffCacheUpdater<RoomSummary, RoomListRoomSummary>(diffCache = diffCache, detectMoves = true) { old, new ->
        old?.roomId == new?.roomId
    }

    val roomSummariesFlow: Flow<ImmutableList<RoomListRoomSummary>> = _roomSummariesFlow

    val loadingState = dynamicRoomList.loadingState

    fun launchIn(coroutineScope: CoroutineScope): Job {
        return dynamicRoomList
            .summaries
            .onEach { roomSummaries ->
                replaceWith(roomSummaries)
            }
            .launchIn(coroutineScope)
    }

    suspend fun updateFilter(userSelectedFilter: RoomListFilter) {
        dynamicRoomList.updateFilter(sdkFilterNarrowingTheUserSelectedFilter(userSelectedFilter))
    }

    suspend fun updateVisibleRange(visibleRange: IntRange) = coroutineScope {
        launch {
            dynamicRoomList.updateVisibleRange(visibleRange, paginationThreshold)
        }
        launch {
            subscribeToVisibleRoomsIfNeeded(visibleRange)
        }
    }

    suspend fun rebuildSummaries() {
        dynamicRoomList.rebuildSummaries()
    }

    private var currentSubscribeToVisibleRoomsJob: Job? = null
    private fun CoroutineScope.subscribeToVisibleRoomsIfNeeded(range: IntRange) {
        currentSubscribeToVisibleRoomsJob?.cancel()
        currentSubscribeToVisibleRoomsJob = launch {
            // Debounce the subscription to avoid subscribing to too many rooms
            delay(SUBSCRIBE_TO_VISIBLE_ROOMS_DEBOUNCE_IN_MILLIS)

            if (range.isEmpty()) return@launch
            val currentRoomList = roomSummariesFlow.first()
            // Use extended range to 'prefetch' the next rooms info
            val midExtendedRangeSize = EXTENDED_VISIBILITY_RANGE_SIZE / 2
            val extendedRange = range.first until range.last + midExtendedRangeSize
            val roomIds = extendedRange.mapNotNull { index ->
                currentRoomList.getOrNull(index)?.roomId
            }
            roomListService.subscribeToVisibleRooms(roomIds)
        }
    }

    private suspend fun replaceWith(roomSummaries: List<RoomSummary>) = withContext(coroutineDispatchers.computation) {
        lock.withLock {
            diffCacheUpdater.updateWith(roomSummaries)
            buildAndEmitAllRooms(roomSummaries)
        }
    }

    private suspend fun buildAndEmitAllRooms(roomSummaries: List<RoomSummary>, useCache: Boolean = true) {
        // Used to detect duplicates in the room list summaries - see comment below
        data class CacheResult(val index: Int, val fromCache: Boolean)

        val cachingResults = mutableMapOf<RoomId, MutableList<CacheResult>>()

        val roomListRoomSummaries = diffCache.indices().mapNotNull { index ->
            if (useCache) {
                diffCache.get(index)?.let { cachedItem ->
                    // Add the cached item to the caching results
                    val pairs = cachingResults.getOrDefault(cachedItem.roomId, mutableListOf())
                    pairs.add(CacheResult(index, fromCache = true))
                    cachingResults[cachedItem.roomId] = pairs
                    cachedItem
                } ?: run {
                    roomSummaries.getOrNull(index)?.roomId?.let {
                        // Add the non-cached item to the caching results
                        val pairs = cachingResults.getOrDefault(it, mutableListOf())
                        pairs.add(CacheResult(index, fromCache = false))
                        cachingResults[it] = pairs
                    }
                    buildAndCacheItem(roomSummaries, index)
                }
            } else {
                roomSummaries.getOrNull(index)?.roomId?.let {
                    // Add the non-cached item to the caching results
                    val pairs = cachingResults.getOrDefault(it, mutableListOf())
                    pairs.add(CacheResult(index, fromCache = false))
                    cachingResults[it] = pairs
                }
                buildAndCacheItem(roomSummaries, index)
            }
        }

        // TODO remove once https://github.com/element-hq/element-x-android/issues/5031 has been confirmed as fixed
        val duplicates = cachingResults.filter { (_, operations) -> operations.size > 1 }
        val roomListRoomSummariesWithoutDuplicates = if (duplicates.isNotEmpty()) {
            analyticsService.trackError(
                IllegalStateException(
                    "Found duplicates in room summaries after a local UI update: $duplicates. " +
                        "This could be a race condition/caching issue of some kind"
                )
            )
            roomListRoomSummaries.distinctBy { it.roomId }
        } else {
            roomListRoomSummaries
        }
        _roomSummariesFlow.emit(roomListRoomSummariesWithoutDuplicates.filter(isRoomKeptAmongRoomsMatchingTheSdkFilter).toImmutableList())
    }

    private fun buildAndCacheItem(roomSummaries: List<RoomSummary>, index: Int): RoomListRoomSummary? {
        val roomListSummary = roomSummaries.getOrNull(index)?.let { roomListRoomSummaryFactory.create(it) }
        diffCache[index] = roomListSummary
        return roomListSummary
    }

    suspend fun rebuildAllRoomSummaries() {
        lock.withLock {
            dynamicRoomList.summaries.replayCache.firstOrNull()?.let { roomSummaries ->
                diffCacheUpdater.updateWith(roomSummaries)
                buildAndEmitAllRooms(roomSummaries, useCache = false)
            }
        }
    }
}
