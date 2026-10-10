/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.datasource

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.sections.RoomListSection
import io.element.android.libraries.androidutils.system.DateTimeObserver
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.notificationsettings.NotificationSettingsService
import io.element.android.libraries.matrix.api.roomlist.RoomList
import io.element.android.libraries.matrix.api.roomlist.RoomListFilter
import io.element.android.libraries.matrix.api.roomlist.RoomListService
import io.element.android.services.analytics.api.AnalyticsService
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

private const val PAGE_SIZE = 20
private const val PAGINATION_THRESHOLD = 3 * PAGE_SIZE

/**
 * A section to show, with the SDK filter that narrows the filter selected by the user down to the rooms of that section.
 */
data class RoomListSectionWithItsRooms(
    val section: RoomListSection,
    val roomsOfSectionAmong: (userSelectedFilter: RoomListFilter) -> RoomListFilter,
)

/**
 * Provides the rooms of the room list, either as one flat list or as one list per [RoomListSection].
 *
 * The flat list always exists, as it also gives the [loadingState] of the whole room list.
 * Each section gets its own SDK room list, created when the section first appears and destroyed when it goes away,
 * so sections cost nothing while they are disabled.
 */
@Inject
@SingleIn(SessionScope::class)
class RoomListDataSource(
    private val roomListService: RoomListService,
    private val roomListRoomSummaryFactory: RoomListRoomSummaryFactory,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val notificationSettingsService: NotificationSettingsService,
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
    private val dateTimeObserver: DateTimeObserver,
    private val analyticsService: AnalyticsService,
) {
    init {
        observeNotificationSettings()
        observeDateTimeChanges()
    }

    private val flatRoomListSummariesSource = DynamicRoomListSummariesSource(
        dynamicRoomList = roomListService.createRoomList(
            pageSize = PAGE_SIZE,
            source = RoomList.Source.All,
            coroutineScope = sessionCoroutineScope,
        ),
        paginationThreshold = PAGINATION_THRESHOLD,
        roomListService = roomListService,
        roomListRoomSummaryFactory = roomListRoomSummaryFactory,
        coroutineDispatchers = coroutineDispatchers,
        analyticsService = analyticsService,
    )

    private data class SectionRoomList(
        val section: RoomListSection,
        val roomsOfSectionAmong: (RoomListFilter) -> RoomListFilter,
        val appliedSdkFilter: RoomListFilter,
        val summariesSource: DynamicRoomListSummariesSource,
        val coroutineScopeDestroyingTheSdkListWhenCancelled: CoroutineScope,
    )

    private val sectionsLock = Mutex()

    @Volatile
    private var userSelectedFilter: RoomListFilter = RoomListFilter.all()

    private val sectionRoomListsInDisplayOrder = MutableStateFlow<List<SectionRoomList>>(emptyList())

    val roomSummariesFlow: Flow<ImmutableList<RoomListRoomSummary>> = flatRoomListSummariesSource.roomSummariesFlow

    val loadingState = flatRoomListSummariesSource.loadingState

    fun launchIn(coroutineScope: CoroutineScope): Job {
        return flatRoomListSummariesSource.launchIn(coroutineScope)
    }

    /**
     * The rooms of every section, in display order, once each section has received its first rooms from the SDK.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val sectionRoomSummariesFlow: Flow<List<Pair<RoomListSection, ImmutableList<RoomListRoomSummary>>>> = sectionRoomListsInDisplayOrder
        .flatMapLatest { sectionRoomLists ->
            if (sectionRoomLists.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    sectionRoomLists.map { sectionRoomList ->
                        sectionRoomList.summariesSource.roomSummariesFlow.map { summaries -> sectionRoomList.section to summaries }
                    }
                ) { sectionsAndSummaries -> sectionsAndSummaries.toList() }
            }
        }

    /**
     * Shows exactly [sections], in this order. A section already shown keeps its SDK room list and pagination,
     * and only gets a new filter when its rooms changed.
     */
    suspend fun updateSections(sections: List<RoomListSectionWithItsRooms>) = sectionsLock.withLock {
        val currentSectionRoomListsByKey = sectionRoomListsInDisplayOrder.value.associateBy { it.section.persistenceKey }
        val updatedSectionRoomLists = sections.map { sectionWithItsRooms ->
            val sdkFilter = sectionWithItsRooms.roomsOfSectionAmong(userSelectedFilter)
            val existingSectionRoomList = currentSectionRoomListsByKey[sectionWithItsRooms.section.persistenceKey]
            if (existingSectionRoomList == null) {
                createSectionRoomList(sectionWithItsRooms, sdkFilter)
            } else {
                if (existingSectionRoomList.appliedSdkFilter != sdkFilter) {
                    existingSectionRoomList.summariesSource.updateFilter(sdkFilter)
                }
                SectionRoomList(
                    section = sectionWithItsRooms.section,
                    roomsOfSectionAmong = sectionWithItsRooms.roomsOfSectionAmong,
                    appliedSdkFilter = sdkFilter,
                    summariesSource = existingSectionRoomList.summariesSource,
                    coroutineScopeDestroyingTheSdkListWhenCancelled = existingSectionRoomList.coroutineScopeDestroyingTheSdkListWhenCancelled,
                )
            }
        }
        val keysOfSectionsStillShown = updatedSectionRoomLists.map { it.section.persistenceKey }.toSet()
        currentSectionRoomListsByKey
            .filterKeys { key -> key !in keysOfSectionsStillShown }
            .values
            .forEach { removedSectionRoomList -> removedSectionRoomList.coroutineScopeDestroyingTheSdkListWhenCancelled.cancel() }
        sectionRoomListsInDisplayOrder.value = updatedSectionRoomLists
    }

    private fun createSectionRoomList(sectionWithItsRooms: RoomListSectionWithItsRooms, sdkFilter: RoomListFilter): SectionRoomList {
        val sectionCoroutineScope = CoroutineScope(sessionCoroutineScope.coroutineContext + SupervisorJob(sessionCoroutineScope.coroutineContext[Job]))
        val summariesSource = DynamicRoomListSummariesSource(
            dynamicRoomList = roomListService.createRoomList(
                pageSize = PAGE_SIZE,
                source = RoomList.Source.All,
                coroutineScope = sectionCoroutineScope,
                initialFilter = sdkFilter,
            ),
            paginationThreshold = PAGINATION_THRESHOLD,
            roomListService = roomListService,
            roomListRoomSummaryFactory = roomListRoomSummaryFactory,
            coroutineDispatchers = coroutineDispatchers,
            analyticsService = analyticsService,
            isRoomKeptAmongRoomsMatchingTheSdkFilter = sectionWithItsRooms.section::containsRoom,
        )
        summariesSource.launchIn(sectionCoroutineScope)
        return SectionRoomList(
            section = sectionWithItsRooms.section,
            roomsOfSectionAmong = sectionWithItsRooms.roomsOfSectionAmong,
            appliedSdkFilter = sdkFilter,
            summariesSource = summariesSource,
            coroutineScopeDestroyingTheSdkListWhenCancelled = sectionCoroutineScope,
        )
    }

    suspend fun updateFilter(filter: RoomListFilter) {
        userSelectedFilter = filter
        flatRoomListSummariesSource.updateFilter(filter)
        sectionsLock.withLock {
            sectionRoomListsInDisplayOrder.value = sectionRoomListsInDisplayOrder.value.map { sectionRoomList ->
                val sdkFilter = sectionRoomList.roomsOfSectionAmong(filter)
                sectionRoomList.summariesSource.updateFilter(sdkFilter)
                SectionRoomList(
                    section = sectionRoomList.section,
                    roomsOfSectionAmong = sectionRoomList.roomsOfSectionAmong,
                    appliedSdkFilter = sdkFilter,
                    summariesSource = sectionRoomList.summariesSource,
                    coroutineScopeDestroyingTheSdkListWhenCancelled = sectionRoomList.coroutineScopeDestroyingTheSdkListWhenCancelled,
                )
            }
        }
    }

    suspend fun updateVisibleRange(visibleRange: IntRange) {
        flatRoomListSummariesSource.updateVisibleRange(visibleRange)
    }

    suspend fun updateSectionVisibleRange(section: RoomListSection, visibleRangeInSection: IntRange) {
        sectionRoomListsInDisplayOrder.value
            .firstOrNull { it.section.persistenceKey == section.persistenceKey }
            ?.summariesSource
            ?.updateVisibleRange(visibleRangeInSection)
    }

    /**
     * The rooms of [section] loaded so far; as the room lists are sorted by recency, rooms past the loaded pages have no recent activity.
     */
    suspend fun loadedRoomSummariesOf(section: RoomListSection): List<RoomListRoomSummary> {
        val sectionRoomList = sectionRoomListsInDisplayOrder.value.firstOrNull { it.section.persistenceKey == section.persistenceKey }
        return sectionRoomList?.summariesSource?.roomSummariesFlow?.first().orEmpty()
    }

    private fun allSummariesSources(): List<DynamicRoomListSummariesSource> {
        return listOf(flatRoomListSummariesSource) + sectionRoomListsInDisplayOrder.value.map { it.summariesSource }
    }

    @OptIn(FlowPreview::class)
    private fun observeNotificationSettings() {
        notificationSettingsService.notificationSettingsChangeFlow
            .debounce(0.5.seconds)
            .onEach {
                allSummariesSources().forEach { summariesSource -> summariesSource.rebuildSummaries() }
            }
            .launchIn(sessionCoroutineScope)
    }

    private fun observeDateTimeChanges() {
        dateTimeObserver.changes
            .onEach { event ->
                when (event) {
                    is DateTimeObserver.Event.TimeZoneChanged,
                    is DateTimeObserver.Event.DateChanged -> allSummariesSources().forEach { summariesSource ->
                        summariesSource.rebuildAllRoomSummaries()
                    }
                }
            }
            .launchIn(sessionCoroutineScope)
    }
}
