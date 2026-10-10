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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.seconds

private const val PAGE_SIZE = 20
private const val PAGINATION_THRESHOLD = 3 * PAGE_SIZE

/**
 * Provides the rooms of the room list, either as one flat list or as one list per [RoomListSection].
 *
 * The flat list always exists, as it also gives the [loadingState] of the whole room list.
 * The lists of the sections are only created the first time sections are used, so they cost nothing while sections are disabled.
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

    @Volatile
    private var userSelectedFilter: RoomListFilter = RoomListFilter.all()

    @Volatile
    private var sectionSummariesSourcesIfCreated: Map<RoomListSection, DynamicRoomListSummariesSource>? = null

    private val sectionSummariesSources: Map<RoomListSection, DynamicRoomListSummariesSource> by lazy {
        RoomListSection.entries
            .associateWith(::createSectionSummariesSource)
            .also { sectionSummariesSourcesIfCreated = it }
    }

    private fun createSectionSummariesSource(section: RoomListSection): DynamicRoomListSummariesSource {
        return DynamicRoomListSummariesSource(
            dynamicRoomList = roomListService.createRoomList(
                pageSize = PAGE_SIZE,
                source = RoomList.Source.All,
                coroutineScope = sessionCoroutineScope,
                initialFilter = section.roomsOfThisSectionAmong(userSelectedFilter),
            ),
            paginationThreshold = PAGINATION_THRESHOLD,
            roomListService = roomListService,
            roomListRoomSummaryFactory = roomListRoomSummaryFactory,
            coroutineDispatchers = coroutineDispatchers,
            analyticsService = analyticsService,
            sdkFilterNarrowingTheUserSelectedFilter = section::roomsOfThisSectionAmong,
            isRoomKeptAmongRoomsMatchingTheSdkFilter = section::containsRoom,
        )
    }

    val roomSummariesFlow: Flow<ImmutableList<RoomListRoomSummary>> = flatRoomListSummariesSource.roomSummariesFlow

    val loadingState = flatRoomListSummariesSource.loadingState

    fun launchIn(coroutineScope: CoroutineScope): Job {
        return flatRoomListSummariesSource.launchIn(coroutineScope)
    }

    fun sectionRoomSummariesFlow(section: RoomListSection): Flow<ImmutableList<RoomListRoomSummary>> {
        return sectionSummariesSources.getValue(section).roomSummariesFlow
    }

    fun launchSectionsIn(coroutineScope: CoroutineScope): List<Job> {
        return sectionSummariesSources.values.map { sectionSummariesSource -> sectionSummariesSource.launchIn(coroutineScope) }
    }

    suspend fun updateFilter(filter: RoomListFilter) {
        userSelectedFilter = filter
        flatRoomListSummariesSource.updateFilter(filter)
        sectionSummariesSourcesIfCreated?.values?.forEach { sectionSummariesSource -> sectionSummariesSource.updateFilter(filter) }
    }

    suspend fun updateVisibleRange(visibleRange: IntRange) {
        flatRoomListSummariesSource.updateVisibleRange(visibleRange)
    }

    suspend fun updateSectionVisibleRange(section: RoomListSection, visibleRangeInSection: IntRange) {
        sectionSummariesSources.getValue(section).updateVisibleRange(visibleRangeInSection)
    }

    private fun allCreatedSummariesSources(): List<DynamicRoomListSummariesSource> {
        return listOf(flatRoomListSummariesSource) + sectionSummariesSourcesIfCreated?.values.orEmpty()
    }

    @OptIn(FlowPreview::class)
    private fun observeNotificationSettings() {
        notificationSettingsService.notificationSettingsChangeFlow
            .debounce(0.5.seconds)
            .onEach {
                allCreatedSummariesSources().forEach { summariesSource -> summariesSource.rebuildSummaries() }
            }
            .launchIn(sessionCoroutineScope)
    }

    private fun observeDateTimeChanges() {
        dateTimeObserver.changes
            .onEach { event ->
                when (event) {
                    is DateTimeObserver.Event.TimeZoneChanged,
                    is DateTimeObserver.Event.DateChanged -> allCreatedSummariesSources().forEach { summariesSource ->
                        summariesSource.rebuildAllRoomSummaries()
                    }
                }
            }
            .launchIn(sessionCoroutineScope)
    }
}
