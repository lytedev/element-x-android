/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import im.vector.app.features.analytics.plan.Interaction
import io.element.android.features.announcement.api.Announcement
import io.element.android.features.announcement.api.AnnouncementService
import io.element.android.features.announcement.test.FakeAnnouncementService
import io.element.android.features.home.impl.FakeDateTimeObserver
import io.element.android.features.home.impl.datasource.RoomListDataSource
import io.element.android.features.home.impl.datasource.aRoomListRoomSummaryFactory
import io.element.android.features.home.impl.filters.RoomListFiltersState
import io.element.android.features.home.impl.filters.aRoomListFiltersState
import io.element.android.features.home.impl.model.aRoomListRoomSummary
import io.element.android.features.home.impl.model.createRoomListRoomSummary
import io.element.android.features.home.impl.search.RoomListSearchEvent
import io.element.android.features.home.impl.search.RoomListSearchState
import io.element.android.features.home.impl.search.aGlobalSearchState
import io.element.android.features.home.impl.search.aRoomListSearchState
import io.element.android.features.home.impl.sections.RoomListSection
import io.element.android.features.home.impl.sections.RoomListSectionsEvent
import io.element.android.features.home.impl.sections.RoomListSectionsLayout
import io.element.android.features.home.impl.sections.RoomListSectionsPresenter
import io.element.android.features.home.impl.sections.custom.RoomListSectionsSettings
import io.element.android.features.home.impl.sections.custom.RoomListSectionsSettingsStore
import io.element.android.features.home.impl.sections.custom.RoomSectionMover
import io.element.android.features.home.impl.sections.custom.RoomSectionTagsIndex
import io.element.android.features.home.impl.spacefilters.SpaceFiltersState
import io.element.android.features.home.impl.spacefilters.aDisabledSpaceFiltersState
import io.element.android.features.invite.api.SeenInvitesStore
import io.element.android.features.invite.api.acceptdecline.AcceptDeclineInviteEvent
import io.element.android.features.invite.api.acceptdecline.AcceptDeclineInviteState
import io.element.android.features.invite.api.acceptdecline.anAcceptDeclineInviteState
import io.element.android.features.invite.test.InMemorySeenInvitesStore
import io.element.android.features.leaveroom.api.LeaveRoomEvent
import io.element.android.features.leaveroom.api.LeaveRoomState
import io.element.android.features.preferences.impl.tasks.MarkRoomAsRead
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.dateformatter.test.FakeDateFormatter
import io.element.android.libraries.eventformatter.api.RoomLatestEventFormatter
import io.element.android.libraries.eventformatter.test.FakeRoomLatestEventFormatter
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.api.ShowAllActivityInRoomListFeature
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.fullscreenintent.api.aFullScreenIntentPermissionsState
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.room.RoomNotificationMode
import io.element.android.libraries.matrix.api.roomlist.RoomList
import io.element.android.libraries.matrix.api.roomlist.RoomListFilter
import io.element.android.libraries.matrix.api.sync.SyncState
import io.element.android.libraries.matrix.api.timeline.ReceiptType
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID_2
import io.element.android.libraries.matrix.test.A_ROOM_ID_3
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.encryption.FakeEncryptionService
import io.element.android.libraries.matrix.test.notificationsettings.FakeNotificationSettingsService
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.libraries.matrix.test.room.aRoomMember
import io.element.android.libraries.matrix.test.room.aRoomSummary
import io.element.android.libraries.matrix.test.roomlist.FakeDynamicRoomList
import io.element.android.libraries.matrix.test.roomlist.FakeRoomListService
import io.element.android.libraries.matrix.test.sync.FakeSyncService
import io.element.android.libraries.matrix.test.verification.FakeSessionVerificationService
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import io.element.android.libraries.preferences.test.InMemorySessionPreferencesStore
import io.element.android.libraries.push.api.battery.aBatteryOptimizationState
import io.element.android.libraries.push.api.notifications.NotificationCleaner
import io.element.android.libraries.push.test.notifications.FakeNotificationCleaner
import io.element.android.services.analytics.api.AnalyticsService
import io.element.android.services.analytics.test.FakeAnalyticsService
import io.element.android.services.analytics.test.watchers.FakeAnalyticsColdStartWatcher
import io.element.android.tests.testutils.EventsRecorder
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.consumeItemsUntilTimeout
import io.element.android.tests.testutils.lambda.assert
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import io.element.android.tests.testutils.test
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class RoomListPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - load 1 room with success`() = runTest {
        val roomList = FakeDynamicRoomList()
        val roomListService = FakeRoomListService(
            createRoomListLambda = { roomList }
        )
        val matrixClient = FakeMatrixClient(
            roomListService = roomListService
        )
        val presenter = createRoomListPresenter(
            client = matrixClient,
            seenInvitesStore = InMemorySeenInvitesStore(setOf(A_ROOM_ID, A_ROOM_ID_2, A_ROOM_ID_3)),
        )
        presenter.test {
            val initialState = consumeItemsUntilPredicate { state -> state.contentState is RoomListContentState.Skeleton }.last()
            assertThat(initialState.contentState).isInstanceOf(RoomListContentState.Skeleton::class.java)
            roomList.loadingState.emit(RoomList.LoadingState.Loaded(1))
            roomList.summaries.emit(
                listOf(
                    aRoomSummary(
                        numUnreadMentions = 1,
                        numUnreadMessages = 2,
                    )
                )
            )
            val withRoomsState =
                consumeItemsUntilPredicate { state -> state.contentState is RoomListContentState.Rooms && state.contentAsRooms().summaries.isNotEmpty() }.last()
            assertThat(withRoomsState.contentAsRooms().summaries).hasSize(1)
            assertThat(withRoomsState.contentAsRooms().summaries.first()).isEqualTo(
                createRoomListRoomSummary(
                    numberOfUnreadMentions = 1,
                    numberOfUnreadMessages = 2,
                    timestamp = "0 TimeOrDate true",
                )
            )
            assertThat(withRoomsState.contentAsRooms().seenRoomInvites).containsExactly(A_ROOM_ID, A_ROOM_ID_2, A_ROOM_ID_3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - handle DismissRequestVerificationPrompt`() = runTest {
        val roomList = FakeDynamicRoomList(
            loadingState = MutableStateFlow(RoomList.LoadingState.Loaded(1))
        )
        val roomListService = FakeRoomListService(
            createRoomListLambda = { roomList }
        )
        val encryptionService = FakeEncryptionService().apply {
            emitRecoveryState(RecoveryState.INCOMPLETE)
        }
        val syncService = FakeSyncService(initialSyncState = SyncState.Running)
        val presenter = createRoomListPresenter(
            client = FakeMatrixClient(roomListService = roomListService, encryptionService = encryptionService, syncService = syncService),
        )
        presenter.test {
            val eventWithContentAsRooms = consumeItemsUntilPredicate {
                it.contentState is RoomListContentState.Rooms
            }.last()
            val eventSink = eventWithContentAsRooms.eventSink
            assertThat(eventWithContentAsRooms.contentAsRooms().securityBannerState).isEqualTo(SecurityBannerState.RecoveryKeyConfirmation)
            eventSink(RoomListEvent.DismissRequestVerificationPrompt)
            assertThat(awaitItem().contentAsRooms().securityBannerState).isEqualTo(SecurityBannerState.None)
        }
    }

    @Test
    fun `present - handle DismissRecoveryKeyPrompt`() = runTest {
        val encryptionService = FakeEncryptionService().apply {
            recoveryStateStateFlow.emit(RecoveryState.DISABLED)
        }
        val roomList = FakeDynamicRoomList(
            loadingState = MutableStateFlow(RoomList.LoadingState.Loaded(1))
        )
        val roomListService = FakeRoomListService(
            createRoomListLambda = { roomList }
        )
        val matrixClient = FakeMatrixClient(
            roomListService = roomListService,
            encryptionService = encryptionService,
            sessionVerificationService = FakeSessionVerificationService().apply {
                emitNeedsSessionVerification(false)
            },
            syncService = FakeSyncService(initialSyncState = SyncState.Running),
        )
        val presenter = createRoomListPresenter(
            client = matrixClient,
        )
        presenter.test {
            val initialState = consumeItemsUntilPredicate {
                it.contentState is RoomListContentState.Rooms
            }.last()
            assertThat(initialState.contentAsRooms().securityBannerState).isEqualTo(SecurityBannerState.SetUpRecovery)
            encryptionService.emitRecoveryState(RecoveryState.INCOMPLETE)
            val nextState = awaitItem()
            assertThat(nextState.contentAsRooms().securityBannerState).isEqualTo(SecurityBannerState.RecoveryKeyConfirmation)
            // Also check other states
            encryptionService.emitRecoveryState(RecoveryState.DISABLED)
            assertThat(awaitItem().contentAsRooms().securityBannerState).isEqualTo(SecurityBannerState.SetUpRecovery)
            encryptionService.emitRecoveryState(RecoveryState.WAITING_FOR_SYNC)
            assertThat(awaitItem().contentAsRooms().securityBannerState).isEqualTo(SecurityBannerState.None)
            encryptionService.emitRecoveryState(RecoveryState.DISABLED)
            assertThat(awaitItem().contentAsRooms().securityBannerState).isEqualTo(SecurityBannerState.SetUpRecovery)
            encryptionService.emitRecoveryState(RecoveryState.ENABLED)
            assertThat(awaitItem().contentAsRooms().securityBannerState).isEqualTo(SecurityBannerState.None)
            encryptionService.emitRecoveryState(RecoveryState.DISABLED)
            assertThat(awaitItem().contentAsRooms().securityBannerState).isEqualTo(SecurityBannerState.SetUpRecovery)
            nextState.eventSink(RoomListEvent.DismissBanner)
            val finalState = awaitItem()
            assertThat(finalState.contentAsRooms().securityBannerState).isEqualTo(SecurityBannerState.None)
        }
    }

    @Test
    fun `present - show context menu`() = runTest {
        val room = FakeBaseRoom()
        val client = FakeMatrixClient().apply {
            givenGetRoomResult(A_ROOM_ID, room)
        }
        val presenter = createRoomListPresenter(client = client)
        presenter.test {
            val initialState = awaitItem()

            // Skip intermediate event for loading the value of the feature flags
            skipItems(1)

            val summary = createRoomListRoomSummary()
            initialState.eventSink(RoomListEvent.ShowContextMenu(summary))

            awaitItem().also { state ->
                assertThat(state.contextMenu)
                    .isEqualTo(
                        RoomListState.ContextMenu.Shown(
                            roomId = summary.roomId,
                            roomName = summary.name,
                            isDm = false,
                            isFavorite = false,
                            hasNewContent = false,
                        )
                    )
            }

            room.givenRoomInfo(
                aRoomInfo(isFavorite = true)
            )
            awaitItem().also { state ->
                assertThat(state.contextMenu)
                    .isEqualTo(
                        RoomListState.ContextMenu.Shown(
                            roomId = summary.roomId,
                            roomName = summary.name,
                            isDm = false,
                            isFavorite = true,
                            hasNewContent = false,
                        )
                    )
            }
        }
    }

    @Test
    fun `present - hide context menu`() = runTest {
        val room = FakeBaseRoom()
        val client = FakeMatrixClient().apply {
            givenGetRoomResult(A_ROOM_ID, room)
        }
        val presenter = createRoomListPresenter(client = client)
        presenter.test {
            val initialState = awaitItem()

            // Skip intermediate event for loading the value of the feature flags
            skipItems(1)

            val summary = createRoomListRoomSummary()
            initialState.eventSink(RoomListEvent.ShowContextMenu(summary))

            val shownState = awaitItem()
            assertThat(shownState.contextMenu)
                .isEqualTo(
                    RoomListState.ContextMenu.Shown(
                        roomId = summary.roomId,
                        roomName = summary.name,
                        isDm = false,
                        isFavorite = false,
                        hasNewContent = false,
                    )
                )

            shownState.eventSink(RoomListEvent.HideContextMenu)

            val hiddenState = awaitItem()
            assertThat(hiddenState.contextMenu).isEqualTo(RoomListState.ContextMenu.Hidden)
        }
    }

    @Test
    fun `present - leave room calls into leave room presenter`() = runTest {
        val leaveRoomEventsRecorder = EventsRecorder<LeaveRoomEvent>()
        val presenter = createRoomListPresenter(
            leaveRoomState = aLeaveRoomState(eventSink = leaveRoomEventsRecorder),
        )
        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(RoomListEvent.LeaveRoom(A_ROOM_ID, needsConfirmation = true))
            leaveRoomEventsRecorder.assertSingle(LeaveRoomEvent.LeaveRoom(A_ROOM_ID, needsConfirmation = true))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - toggle search menu`() = runTest {
        val eventRecorder = EventsRecorder<RoomListSearchEvent>()
        val searchPresenter: Presenter<RoomListSearchState> = Presenter {
            aRoomListSearchState(
                eventSink = eventRecorder
            )
        }
        val presenter = createRoomListPresenter(
            searchPresenter = searchPresenter,
        )
        presenter.test {
            val initialState = awaitItem()

            // Skip intermediate event for loading the value of the feature flags
            skipItems(1)

            eventRecorder.assertEmpty()
            initialState.eventSink(RoomListEvent.ToggleSearchResults)
            eventRecorder.assertSingle(
                RoomListSearchEvent.ToggleSearchVisibility
            )
            initialState.eventSink(RoomListEvent.ToggleSearchResults)
            eventRecorder.assertList(
                listOf(
                    RoomListSearchEvent.ToggleSearchVisibility,
                    RoomListSearchEvent.ToggleSearchVisibility
                )
            )
        }
    }

    @Test
    fun `present - change in notification settings updates the summary for decorations`() = runTest {
        val userDefinedMode = RoomNotificationMode.MENTIONS_AND_KEYWORDS_ONLY
        val notificationSettingsService = FakeNotificationSettingsService()
        val roomList = FakeDynamicRoomList(
            summaries = MutableStateFlow(listOf(aRoomSummary(userDefinedNotificationMode = userDefinedMode))),
            loadingState = MutableStateFlow(RoomList.LoadingState.Loaded(1))
        )
        val roomListService = FakeRoomListService(
            createRoomListLambda = { roomList }
        )
        val matrixClient = FakeMatrixClient(
            roomListService = roomListService,
            notificationSettingsService = notificationSettingsService
        )
        val presenter = createRoomListPresenter(client = matrixClient)
        presenter.test {
            notificationSettingsService.setRoomNotificationMode(A_ROOM_ID, userDefinedMode)
            val updatedState = consumeItemsUntilPredicate { state ->
                (state.contentState as? RoomListContentState.Rooms)?.summaries.orEmpty().any { summary ->
                    summary.id == A_ROOM_ID.value && summary.userDefinedNotificationMode == userDefinedMode
                }
            }.last()

            val room = updatedState.contentAsRooms().summaries.find { it.id == A_ROOM_ID.value }
            assertThat(room?.userDefinedNotificationMode).isEqualTo(userDefinedMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - when set is favorite event is emitted, then the action is called`() = runTest {
        val setIsFavoriteResult = lambdaRecorder { _: Boolean -> Result.success(Unit) }
        val room = FakeBaseRoom(
            setIsFavoriteResult = setIsFavoriteResult
        )
        val analyticsService = FakeAnalyticsService()
        val client = FakeMatrixClient().apply {
            givenGetRoomResult(A_ROOM_ID, room)
        }
        val presenter = createRoomListPresenter(client = client, analyticsService = analyticsService)
        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(RoomListEvent.SetRoomIsFavorite(A_ROOM_ID, true))
            setIsFavoriteResult.assertions().isCalledOnce().with(value(true))
            initialState.eventSink(RoomListEvent.SetRoomIsFavorite(A_ROOM_ID, false))
            setIsFavoriteResult.assertions().isCalledExactly(2)
                .withSequence(
                    listOf(value(true)),
                    listOf(value(false)),
                )
            assertThat(analyticsService.capturedEvents).containsExactly(
                Interaction(name = Interaction.Name.MobileRoomListRoomContextMenuFavouriteToggle),
                Interaction(name = Interaction.Name.MobileRoomListRoomContextMenuFavouriteToggle)
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling low priority from the context menu sets and then clears the low priority tag of the room`() = runTest {
        val setIsLowPriorityResult = lambdaRecorder { _: Boolean -> Result.success(Unit) }
        val client = FakeMatrixClient(getAccountDataLambda = { Result.success(null) }).apply {
            givenGetRoomResult(A_ROOM_ID, FakeBaseRoom(setIsLowPriorityResult = setIsLowPriorityResult))
        }
        val presenter = createRoomListPresenter(client = client, featureFlagService = aSectionsEnabledFeatureFlagService())
        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(RoomListEvent.SetRoomIsLowPriority(A_ROOM_ID, true))
            initialState.eventSink(RoomListEvent.SetRoomIsLowPriority(A_ROOM_ID, false))
            setIsLowPriorityResult.assertions().isCalledExactly(2)
                .withSequence(
                    listOf(value(true)),
                    listOf(value(false)),
                )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the context menu only offers the low priority toggle while sections are enabled, as only the Low priority section uses it`() = runTest {
        val roomSummary = aRoomListRoomSummary()
        listOf(true, false).forEach { isRoomListSectionsEnabled ->
            val presenter = createRoomListPresenter(
                client = FakeMatrixClient(getAccountDataLambda = { Result.success(null) }),
                featureFlagService = FakeFeatureFlagService(initialState = mapOf(FeatureFlags.RoomListSections.key to isRoomListSectionsEnabled)),
            )
            presenter.test {
                consumeItemsUntilTimeout().last().eventSink(RoomListEvent.ShowContextMenu(roomSummary))
                val stateWithContextMenu = consumeItemsUntilPredicate { state -> state.contextMenu is RoomListState.ContextMenu.Shown }.last()
                val shownContextMenu = stateWithContextMenu.contextMenu as RoomListState.ContextMenu.Shown
                assertWithMessage("with sections enabled = $isRoomListSectionsEnabled, the low priority toggle visibility must match")
                    .that(shownContextMenu.showSectionActionsBecauseSectionsAreEnabled)
                    .isEqualTo(isRoomListSectionsEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `present - when room service returns no room, then contentState is Empty`() = runTest {
        val roomList = FakeDynamicRoomList(
            loadingState = MutableStateFlow(RoomList.LoadingState.Loaded(0))
        )
        val roomListService = FakeRoomListService(
            createRoomListLambda = { roomList }
        )
        val matrixClient = FakeMatrixClient(
            roomListService = roomListService,
        )
        val presenter = createRoomListPresenter(
            client = matrixClient,
        )
        presenter.test {
            skipItems(1) // Skip initial state
            assertThat(awaitItem().contentState).isInstanceOf(RoomListContentState.Empty::class.java)
        }
    }

    @Test
    fun `present - check that the room is marked as read with correct RR and as unread`() = runTest {
        val markAsReadResult = lambdaRecorder<ReceiptType, Result<Unit>> { Result.success(Unit) }
        val markAsReadResult3 = lambdaRecorder<ReceiptType, Result<Unit>> { Result.success(Unit) }
        val room = FakeBaseRoom(
            markAsReadResult = markAsReadResult,
        )
        val room2 = FakeBaseRoom(
            roomId = A_ROOM_ID_2,
        )
        val room3 = FakeBaseRoom(
            roomId = A_ROOM_ID_3,
            markAsReadResult = markAsReadResult3,
        )
        val allRooms = setOf(room, room2, room3)
        val sessionPreferencesStore = InMemorySessionPreferencesStore()
        val matrixClient = FakeMatrixClient().apply {
            givenGetRoomResult(A_ROOM_ID, room)
            givenGetRoomResult(A_ROOM_ID_2, room2)
            givenGetRoomResult(A_ROOM_ID_3, room3)
        }
        val analyticsService = FakeAnalyticsService()
        val clearMessagesForRoomLambda = lambdaRecorder<SessionId, RoomId, Unit> { _, _ -> }
        val notificationCleaner = FakeNotificationCleaner(
            clearMessagesForRoomLambda = clearMessagesForRoomLambda,
        )
        val markRoomAsRead = createTestMarkRoomAsRead(
            client = matrixClient,
            notificationCleaner = notificationCleaner,
            sessionPreferencesStore = sessionPreferencesStore,
        )
        val presenter = createRoomListPresenter(
            client = matrixClient,
            sessionPreferencesStore = sessionPreferencesStore,
            analyticsService = analyticsService,
            notificationCleaner = notificationCleaner,
            markRoomAsRead = markRoomAsRead,
        )
        presenter.test {
            val initialState = awaitItem()
            allRooms.forEach {
                assertThat(it.setUnreadFlagCalls).isEmpty()
            }
            initialState.eventSink.invoke(RoomListEvent.MarkAsRead(A_ROOM_ID))
            markAsReadResult.assertions().isCalledOnce().with(value(ReceiptType.READ))
            assertThat(room.setUnreadFlagCalls).isEqualTo(listOf(false))
            clearMessagesForRoomLambda.assertions().isCalledOnce()
                .with(value(A_SESSION_ID), value(A_ROOM_ID))
            initialState.eventSink.invoke(RoomListEvent.MarkAsUnread(A_ROOM_ID_2))
            assertThat(room2.setUnreadFlagCalls).isEqualTo(listOf(true))
            // Test again with private read receipts
            sessionPreferencesStore.setSendPublicReadReceipts(false)
            initialState.eventSink.invoke(RoomListEvent.MarkAsRead(A_ROOM_ID_3))
            markAsReadResult3.assertions().isCalledOnce().with(value(ReceiptType.READ_PRIVATE))
            assertThat(room3.setUnreadFlagCalls).isEqualTo(listOf(false))
            clearMessagesForRoomLambda.assertions().isCalledExactly(2)
                .withSequence(
                    listOf(value(A_SESSION_ID), value(A_ROOM_ID)),
                    listOf(value(A_SESSION_ID), value(A_ROOM_ID_3)),
                )
            assertThat(analyticsService.capturedEvents).containsExactly(
                Interaction(name = Interaction.Name.MobileRoomListRoomContextMenuUnreadToggle),
                Interaction(name = Interaction.Name.MobileRoomListRoomContextMenuUnreadToggle),
                Interaction(name = Interaction.Name.MobileRoomListRoomContextMenuUnreadToggle),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - when a room is invited then accept and decline events are sent to acceptDeclinePresenter`() = runTest {
        val eventSinkRecorder = lambdaRecorder { _: AcceptDeclineInviteEvent -> }
        val acceptDeclinePresenter = Presenter {
            anAcceptDeclineInviteState(eventSink = eventSinkRecorder)
        }

        val roomSummary = aRoomSummary(
            currentUserMembership = CurrentUserMembership.INVITED,
            inviter = aRoomMember(),
        )
        val roomList = FakeDynamicRoomList(
            summaries = MutableStateFlow(listOf(roomSummary)),
            loadingState = MutableStateFlow(RoomList.LoadingState.Loaded(1))
        )
        val roomListService = FakeRoomListService(
            createRoomListLambda = { roomList }
        )
        val matrixClient = FakeMatrixClient(
            roomListService = roomListService,
        )
        val presenter = createRoomListPresenter(
            client = matrixClient,
            acceptDeclineInvitePresenter = acceptDeclinePresenter
        )
        presenter.test {
            val state = consumeItemsUntilPredicate {
                it.contentState is RoomListContentState.Rooms
            }.last()

            val roomListRoomSummary = state.contentAsRooms().summaries.first {
                it.id == roomSummary.roomId.value
            }

            state.eventSink(RoomListEvent.AcceptInvite(roomListRoomSummary))
            state.eventSink(RoomListEvent.DeclineInvite(roomListRoomSummary, blockUser = false))

            val inviteData = roomListRoomSummary.toInviteData()
            assert(eventSinkRecorder)
                .isCalledExactly(2)
                .withSequence(
                    listOf(value(AcceptDeclineInviteEvent.AcceptInvite(inviteData))),
                    listOf(value(AcceptDeclineInviteEvent.DeclineInvite(inviteData, blockUser = false, shouldConfirm = false))),
                )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `present - UpdateVisibleRange will cancel the previous subscription if called too soon`() = runTest {
        val subscribeToVisibleRoomsLambda = lambdaRecorder { _: List<RoomId> -> }
        val roomSummary = aRoomSummary(
            currentUserMembership = CurrentUserMembership.INVITED
        )
        val roomList = FakeDynamicRoomList(
            summaries = MutableStateFlow(listOf(roomSummary)),
            loadingState = MutableStateFlow(RoomList.LoadingState.Loaded(1))
        )
        val roomListService = FakeRoomListService(
            createRoomListLambda = { roomList },
            subscribeToVisibleRoomsLambda = subscribeToVisibleRoomsLambda,
        )
        val matrixClient = FakeMatrixClient(
            roomListService = roomListService,
        )
        val presenter = createRoomListPresenter(
            client = matrixClient,
        )
        presenter.test {
            val state = consumeItemsUntilPredicate {
                it.contentState is RoomListContentState.Rooms
            }.last()

            state.eventSink(RoomListEvent.UpdateVisibleRange(IntRange(0, 10)))
            // If called again, it will cancel the current one, which should not result in a test failure
            state.eventSink(RoomListEvent.UpdateVisibleRange(IntRange(0, 11)))
            advanceTimeBy(1.seconds)
            subscribeToVisibleRoomsLambda.assertions().isCalledOnce()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `present - UpdateVisibleRange subscribes to rooms in visible range`() = runTest {
        val subscribeToVisibleRoomsLambda = lambdaRecorder { _: List<RoomId> -> }
        val roomSummary = aRoomSummary(
            currentUserMembership = CurrentUserMembership.INVITED
        )
        val roomList = FakeDynamicRoomList(
            summaries = MutableStateFlow(listOf(roomSummary)),
            loadingState = MutableStateFlow(RoomList.LoadingState.Loaded(1))
        )
        val roomListService = FakeRoomListService(
            createRoomListLambda = { roomList },
            subscribeToVisibleRoomsLambda = subscribeToVisibleRoomsLambda,
        )
        val matrixClient = FakeMatrixClient(
            roomListService = roomListService,
        )
        val presenter = createRoomListPresenter(
            client = matrixClient,
        )
        presenter.test {
            val state = consumeItemsUntilPredicate {
                it.contentState is RoomListContentState.Rooms
            }.last()

            state.eventSink(RoomListEvent.UpdateVisibleRange(IntRange(0, 10)))
            advanceTimeBy(1.seconds)
            subscribeToVisibleRoomsLambda.assertions().isCalledOnce()

            // If called again, it will subscribe to the next items
            state.eventSink(RoomListEvent.UpdateVisibleRange(IntRange(0, 11)))
            advanceTimeBy(1.seconds)
            subscribeToVisibleRoomsLambda.assertions().isCalledExactly(2)
        }
    }

    @Test
    fun `present - notification sound banner`() = runTest {
        val subscribeToVisibleRoomsLambda = lambdaRecorder { _: List<RoomId> -> }
        val roomSummary = aRoomSummary(
            currentUserMembership = CurrentUserMembership.INVITED
        )
        val roomList = FakeDynamicRoomList(
            summaries = MutableStateFlow(listOf(roomSummary)),
            loadingState = MutableStateFlow(RoomList.LoadingState.Loaded(1))
        )
        val roomListService = FakeRoomListService(
            createRoomListLambda = { roomList },
            subscribeToVisibleRoomsLambda = subscribeToVisibleRoomsLambda,
        )
        val matrixClient = FakeMatrixClient(
            roomListService = roomListService,
        )
        val onAnnouncementDismissedResult = lambdaRecorder<Announcement, Unit> { }
        val announcementService = FakeAnnouncementService(
            onAnnouncementDismissedResult = onAnnouncementDismissedResult,
        )
        val presenter = createRoomListPresenter(
            client = matrixClient,
            announcementService = announcementService,
        )
        presenter.test {
            assertThat(announcementService.announcementsToShowFlow().first()).isEmpty()
            skipItems(2) // Skip initial state and intermediate event for loading the value of the feature flags
            val state = awaitItem()
            assertThat(state.contentAsRooms().showNewNotificationSoundBanner).isFalse()
            announcementService.emitAnnouncementsToShow(listOf(Announcement.NewNotificationSound))
            assertThat(awaitItem().contentAsRooms().showNewNotificationSoundBanner).isTrue()
            state.eventSink(RoomListEvent.DismissNewNotificationSoundBanner)
            onAnnouncementDismissedResult.assertions().isCalledOnce()
                .with(value(Announcement.NewNotificationSound))
            // Simulate service updating the value
            announcementService.emitAnnouncementsToShow(emptyList())
            assertThat(awaitItem().contentAsRooms().showNewNotificationSoundBanner).isFalse()
        }
    }

    @Test
    fun `present - showAllActivity and showUnreadCount reflect the feature flag values`() = runTest {
        val roomList = FakeDynamicRoomList(
            summaries = MutableStateFlow(listOf(aRoomSummary())),
            loadingState = MutableStateFlow(RoomList.LoadingState.Loaded(1)),
        )
        val matrixClient = FakeMatrixClient(
            roomListService = FakeRoomListService(createRoomListLambda = { roomList }),
        )
        val featureFlagService = FakeFeatureFlagService(
            initialState = mapOf(
                ShowAllActivityInRoomListFeature.key to true,
                FeatureFlags.UnreadIndicatorCount.key to true,
            )
        )
        val presenter = createRoomListPresenter(
            client = matrixClient,
            featureFlagService = featureFlagService,
        )
        presenter.test {
            val enabledState = consumeItemsUntilPredicate { state ->
                state.contentState is RoomListContentState.Rooms &&
                    state.contentAsRooms().showAllActivity &&
                    state.contentAsRooms().showUnreadCount
            }.last()
            assertThat(enabledState.contentAsRooms().showAllActivity).isTrue()
            assertThat(enabledState.contentAsRooms().showUnreadCount).isTrue()

            featureFlagService.setFeatureEnabled(ShowAllActivityInRoomListFeature, false)
            val allActivityDisabledState = consumeItemsUntilPredicate { state ->
                state.contentState is RoomListContentState.Rooms && !state.contentAsRooms().showAllActivity
            }.last()
            assertThat(allActivityDisabledState.contentAsRooms().showAllActivity).isFalse()
            assertThat(allActivityDisabledState.contentAsRooms().showUnreadCount).isTrue()

            featureFlagService.setFeatureEnabled(FeatureFlags.UnreadIndicatorCount, false)
            val unreadCountDisabledState = consumeItemsUntilPredicate { state ->
                state.contentState is RoomListContentState.Rooms && !state.contentAsRooms().showUnreadCount
            }.last()
            assertThat(unreadCountDisabledState.contentAsRooms().showAllActivity).isFalse()
            assertThat(unreadCountDisabledState.contentAsRooms().showUnreadCount).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createTestMarkRoomAsRead(
        client: MatrixClient,
        notificationCleaner: NotificationCleaner,
        sessionPreferencesStore: SessionPreferencesStore,
    ): MarkRoomAsRead = FakeMarkRoomAsRead { roomId ->
        notificationCleaner.clearMessagesForRoom(client.sessionId, roomId)
        val room = client.getRoom(roomId) ?: return@FakeMarkRoomAsRead Result.failure(IllegalStateException("Room not found"))
        room.use {
            it.setUnreadFlag(isUnread = false)
            val receiptType = if (sessionPreferencesStore.isSendPublicReadReceiptsEnabled().first()) {
                ReceiptType.READ
            } else {
                ReceiptType.READ_PRIVATE
            }
            it.markAsRead(receiptType)
        }
    }

    /**
     * Gives the presenter one room list per section, recognised by the filter each section list is created with.
     */
    private class RoomListsOfEachSection {
        val flatRoomList = FakeDynamicRoomList(loadingState = MutableStateFlow(RoomList.LoadingState.Loaded(numberOfRooms = 3)))
        private val defaultLayout = RoomListSectionsLayout(RoomListSectionsSettings.DEFAULT, tagsOfRoomsInCustomSections = emptyMap())
        val roomListBySection: Map<RoomListSection, FakeDynamicRoomList> = defaultLayout.sectionsInDisplayOrder.associateWith { FakeDynamicRoomList() }
        val roomListService = FakeRoomListService(
            createRoomListWithInitialFilterLambda = { _, initialFilter ->
                defaultLayout.sectionsInDisplayOrder
                    .firstOrNull { section -> defaultLayout.roomsOfSectionAmong(section, RoomListFilter.all()) == initialFilter }
                    ?.let(roomListBySection::getValue)
                    ?: flatRoomList
            },
        )

        fun aMatrixClientWithoutStoredSectionSettings() = FakeMatrixClient(
            roomListService = roomListService,
            getAccountDataLambda = { Result.success(null) },
        )
    }

    private fun aSectionsEnabledFeatureFlagService() = FakeFeatureFlagService(initialState = mapOf(FeatureFlags.RoomListSections.key to true))

    @Test
    fun `with sections enabled, only the sections holding rooms are shown, in the section order`() = runTest {
        val roomListsOfEachSection = RoomListsOfEachSection()
        val presenter = createRoomListPresenter(
            client = roomListsOfEachSection.aMatrixClientWithoutStoredSectionSettings(),
            featureFlagService = aSectionsEnabledFeatureFlagService(),
        )
        presenter.test {
            roomListsOfEachSection.roomListBySection.getValue(RoomListSection.Rooms()).summaries.emit(listOf(aRoomSummary(roomId = A_ROOM_ID)))
            roomListsOfEachSection.roomListBySection.getValue(RoomListSection.Favourites).summaries.emit(
                listOf(aRoomSummary(roomId = A_ROOM_ID_2, isFavorite = true))
            )

            val stateWithBothSections = consumeItemsUntilPredicate { state ->
                (state.contentState as? RoomListContentState.Rooms)?.shownSectionsWhenSectionsAreEnabled?.size == 2
            }.last().contentAsRooms()

            assertWithMessage(
                "Favourites must come before Rooms, and the sections without any room (Invites, People, Low priority) must be hidden",
            ).that(stateWithBothSections.shownSectionsWhenSectionsAreEnabled.map { it.section })
                .containsExactly(RoomListSection.Favourites, RoomListSection.Rooms())
                .inOrder()
            assertWithMessage("with more than one section holding rooms, each section must be introduced by its header")
                .that(stateWithBothSections.showSectionHeaders)
                .isTrue()
            assertWithMessage("the rooms of all sections, in section order, must be listed so that the empty state is decided on all of them")
                .that(stateWithBothSections.summaries.map { it.roomId })
                .containsExactly(A_ROOM_ID_2, A_ROOM_ID)
                .inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `with sections enabled, an invite matched by the filters of the People section is only listed under Invites`() = runTest {
        val roomListsOfEachSection = RoomListsOfEachSection()
        val presenter = createRoomListPresenter(
            client = roomListsOfEachSection.aMatrixClientWithoutStoredSectionSettings(),
            featureFlagService = aSectionsEnabledFeatureFlagService(),
        )
        presenter.test {
            val invitedDirectMessage = aRoomSummary(roomId = A_ROOM_ID_3, isDirect = true, currentUserMembership = CurrentUserMembership.INVITED)
            roomListsOfEachSection.roomListBySection.getValue(RoomListSection.People).summaries.emit(listOf(invitedDirectMessage))
            roomListsOfEachSection.roomListBySection.getValue(RoomListSection.Invites).summaries.emit(listOf(invitedDirectMessage))

            val stateWithTheInvite = consumeItemsUntilPredicate { state ->
                (state.contentState as? RoomListContentState.Rooms)?.shownSectionsWhenSectionsAreEnabled?.isNotEmpty() == true
            }.last().contentAsRooms()

            assertWithMessage("the invited direct message must not be repeated in People, so Invites must be the only section shown")
                .that(stateWithTheInvite.shownSectionsWhenSectionsAreEnabled.map { it.section })
                .containsExactly(RoomListSection.Invites)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `collapsing a section hides its rooms and is remembered for the session`() = runTest {
        val roomListsOfEachSection = RoomListsOfEachSection()
        val sessionPreferencesStore = InMemorySessionPreferencesStore()
        val presenter = createRoomListPresenter(
            client = roomListsOfEachSection.aMatrixClientWithoutStoredSectionSettings(),
            featureFlagService = aSectionsEnabledFeatureFlagService(),
            sessionPreferencesStore = sessionPreferencesStore,
        )
        presenter.test {
            roomListsOfEachSection.roomListBySection.getValue(RoomListSection.Favourites).summaries.emit(
                listOf(aRoomSummary(roomId = A_ROOM_ID, isFavorite = true))
            )
            val expandedState = consumeItemsUntilPredicate { state ->
                (state.contentState as? RoomListContentState.Rooms)?.shownSectionsWhenSectionsAreEnabled?.isNotEmpty() == true
            }.last()

            expandedState.sectionsState.eventSink(RoomListSectionsEvent.ToggleSectionCollapsed(RoomListSection.Favourites))

            val collapsedFavourites = consumeItemsUntilPredicate { state ->
                (state.contentState as? RoomListContentState.Rooms)?.shownSectionsWhenSectionsAreEnabled?.firstOrNull()?.isCollapsed == true
            }.last().contentAsRooms().shownSectionsWhenSectionsAreEnabled.single()
            assertWithMessage("a collapsed section must hide its rooms").that(collapsedFavourites.visibleSummaries).isEmpty()
            assertWithMessage("the collapsed state must be stored, so that the section is still collapsed when the room list is opened again")
                .that(sessionPreferencesStore.getCollapsedRoomListSectionKeys().first())
                .containsExactly(RoomListSection.Favourites.persistenceKey)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun TestScope.createRoomListPresenter(
        client: MatrixClient = FakeMatrixClient(),
        leaveRoomState: LeaveRoomState = aLeaveRoomState(),
        dateFormatter: DateFormatter = FakeDateFormatter(),
        roomLatestEventFormatter: RoomLatestEventFormatter = FakeRoomLatestEventFormatter(),
        sessionPreferencesStore: SessionPreferencesStore = InMemorySessionPreferencesStore(),
        analyticsService: AnalyticsService = FakeAnalyticsService(),
        filtersPresenter: Presenter<RoomListFiltersState> = Presenter { aRoomListFiltersState() },
        searchPresenter: Presenter<RoomListSearchState> = Presenter { aRoomListSearchState() },
        spaceFiltersPresenter: Presenter<SpaceFiltersState> = Presenter { aDisabledSpaceFiltersState() },
        acceptDeclineInvitePresenter: Presenter<AcceptDeclineInviteState> = Presenter { anAcceptDeclineInviteState() },
        notificationCleaner: NotificationCleaner = FakeNotificationCleaner(),
        seenInvitesStore: SeenInvitesStore = InMemorySeenInvitesStore(),
        announcementService: AnnouncementService = FakeAnnouncementService(),
        featureFlagService: FeatureFlagService = FakeFeatureFlagService(),
        markRoomAsRead: MarkRoomAsRead? = null,
    ): RoomListPresenter {
        val roomListDataSource = RoomListDataSource(
            roomListService = client.roomListService,
            roomListRoomSummaryFactory = aRoomListRoomSummaryFactory(
                dateFormatter = dateFormatter,
                roomLatestEventFormatter = roomLatestEventFormatter,
            ),
            coroutineDispatchers = testCoroutineDispatchers(),
            notificationSettingsService = client.notificationSettingsService,
            sessionCoroutineScope = backgroundScope,
            dateTimeObserver = FakeDateTimeObserver(),
            analyticsService = FakeAnalyticsService(),
        )
        val roomSectionTagsIndex = RoomSectionTagsIndex(client, backgroundScope)
        val roomSectionMover = RoomSectionMover(client, roomSectionTagsIndex)
        return createRoomListPresenter(
            client = client,
            leaveRoomState = leaveRoomState,
            roomListDataSource = roomListDataSource,
            roomListSectionsPresenter = RoomListSectionsPresenter(
                featureFlagService = featureFlagService,
                sessionPreferencesStore = sessionPreferencesStore,
                roomListSectionsSettingsStore = RoomListSectionsSettingsStore(client, backgroundScope),
                roomSectionTagsIndex = roomSectionTagsIndex,
                roomSectionMover = roomSectionMover,
                roomListDataSource = roomListDataSource,
                markRoomAsRead = markRoomAsRead ?: FakeMarkRoomAsRead { Result.success(Unit) },
            ),
            roomSectionMover = roomSectionMover,
            sessionPreferencesStore = sessionPreferencesStore,
            analyticsService = analyticsService,
            filtersPresenter = filtersPresenter,
            searchPresenter = searchPresenter,
            spaceFiltersPresenter = spaceFiltersPresenter,
            acceptDeclineInvitePresenter = acceptDeclineInvitePresenter,
            notificationCleaner = notificationCleaner,
            seenInvitesStore = seenInvitesStore,
            announcementService = announcementService,
            featureFlagService = featureFlagService,
            markRoomAsRead = markRoomAsRead,
        )
    }

    private fun createRoomListPresenter(
        client: MatrixClient,
        leaveRoomState: LeaveRoomState,
        roomListDataSource: RoomListDataSource,
        roomListSectionsPresenter: RoomListSectionsPresenter,
        roomSectionMover: RoomSectionMover,
        sessionPreferencesStore: SessionPreferencesStore,
        analyticsService: AnalyticsService,
        filtersPresenter: Presenter<RoomListFiltersState>,
        searchPresenter: Presenter<RoomListSearchState>,
        spaceFiltersPresenter: Presenter<SpaceFiltersState>,
        acceptDeclineInvitePresenter: Presenter<AcceptDeclineInviteState>,
        notificationCleaner: NotificationCleaner,
        seenInvitesStore: SeenInvitesStore,
        announcementService: AnnouncementService,
        featureFlagService: FeatureFlagService,
        markRoomAsRead: MarkRoomAsRead?,
    ) = RoomListPresenter(
        client = client,
        leaveRoomPresenter = { leaveRoomState },
        roomListDataSource = roomListDataSource,
        searchPresenter = searchPresenter,
        globalSearchPresenter = { aGlobalSearchState() },
        filtersPresenter = filtersPresenter,
        spaceFiltersPresenter = spaceFiltersPresenter,
        analyticsService = analyticsService,
        acceptDeclineInvitePresenter = acceptDeclineInvitePresenter,
        fullScreenIntentPermissionsPresenter = { aFullScreenIntentPermissionsState() },
        batteryOptimizationPresenter = { aBatteryOptimizationState() },
        markRoomAsRead = markRoomAsRead ?: createTestMarkRoomAsRead(
            client = client,
            notificationCleaner = notificationCleaner,
            sessionPreferencesStore = sessionPreferencesStore,
        ),
        seenInvitesStore = seenInvitesStore,
        announcementService = announcementService,
        coldStartWatcher = FakeAnalyticsColdStartWatcher(),
        featureFlagService = featureFlagService,
        roomListSectionsPresenter = roomListSectionsPresenter,
        roomSectionMover = roomSectionMover,
    )
}
