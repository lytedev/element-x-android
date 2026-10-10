/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.sections

import com.google.common.truth.Truth.assertWithMessage
import io.element.android.features.home.impl.FakeDateTimeObserver
import io.element.android.features.home.impl.datasource.RoomListDataSource
import io.element.android.features.home.impl.datasource.aRoomListRoomSummaryFactory
import io.element.android.features.home.impl.sections.custom.ElementWebRoomListSettings
import io.element.android.features.home.impl.sections.custom.ElementWebRoomListSettingsCodec
import io.element.android.features.home.impl.sections.custom.RoomListSectionsSettingsStore
import io.element.android.features.home.impl.sections.custom.RoomSectionMover
import io.element.android.features.home.impl.sections.custom.RoomSectionTagsIndex
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.matrix.api.room.tags.RoomTagInfo
import io.element.android.libraries.matrix.api.room.tags.RoomTagName
import io.element.android.libraries.matrix.api.roomlist.RoomListService
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.roomlist.FakeRoomListService
import io.element.android.libraries.preferences.test.InMemorySessionPreferencesStore
import io.element.android.services.analytics.test.FakeAnalyticsService
import io.element.android.tests.testutils.awaitLastSequentialItem
import io.element.android.tests.testutils.test
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomListSectionsPresenterTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `creating a section from a room menu stores it for Element Web, keeps its other settings, and moves the room into it`() = runTest {
        var storedWebSettings = """{"SomeOtherWebSetting": 42}"""
        val tagsOfTheRoom = mutableSetOf<RoomTagName>()
        val roomListService = FakeRoomListService().apply { postState(RoomListService.State.Running) }
        val matrixClient = FakeMatrixClient(
            roomListService = roomListService,
            getAccountDataLambda = { eventType ->
                Result.success(storedWebSettings.takeIf { eventType == ElementWebRoomListSettings.ACCOUNT_DATA_EVENT_TYPE })
            },
            setAccountDataLambda = { _, content ->
                storedWebSettings = content
                Result.success(Unit)
            },
        ).apply {
            givenGetRoomResult(
                A_ROOM_ID,
                FakeBaseRoom(
                    tagsResult = { Result.success(tagsOfTheRoom.associateWith { RoomTagInfo() }) },
                    setTagResult = { tag, _ ->
                        tagsOfTheRoom += tag
                        Result.success(Unit)
                    },
                ),
            )
        }
        val roomSectionTagsIndex = RoomSectionTagsIndex(matrixClient, backgroundScope)
        val sectionsPresenter = RoomListSectionsPresenter(
            featureFlagService = FakeFeatureFlagService(initialState = mapOf(FeatureFlags.RoomListSections.key to true)),
            sessionPreferencesStore = InMemorySessionPreferencesStore(),
            roomListSectionsSettingsStore = RoomListSectionsSettingsStore(matrixClient, backgroundScope),
            roomSectionTagsIndex = roomSectionTagsIndex,
            roomSectionMover = RoomSectionMover(matrixClient, roomSectionTagsIndex),
            roomListDataSource = RoomListDataSource(
                roomListService = roomListService,
                roomListRoomSummaryFactory = aRoomListRoomSummaryFactory(),
                coroutineDispatchers = testCoroutineDispatchers(),
                notificationSettingsService = matrixClient.notificationSettingsService,
                sessionCoroutineScope = backgroundScope,
                dateTimeObserver = FakeDateTimeObserver(),
                analyticsService = FakeAnalyticsService(),
            ),
        )

        sectionsPresenter.test {
            val stateOnceEnabled = awaitLastSequentialItem()
            stateOnceEnabled.eventSink(RoomListSectionsEvent.CreateSection(
                    name = "Work",
                    roomToMoveIntoTheNewSection = A_ROOM_ID,
                    roomIdOfTheCurrentSpaceOrHomeSpaceKey = ElementWebRoomListSettings.HOME_SPACE_KEY,
                ))
            advanceUntilIdle()

            val storedContent = ElementWebRoomListSettingsCodec.parseContent(storedWebSettings)
            val storedSections = ElementWebRoomListSettingsCodec.decode(storedContent)
            val createdSection = storedSections.customSectionsInOrder.single()
            assertWithMessage("the new section must be stored with its name and the space it was created in")
                .that(createdSection.name to createdSection.roomIdOfTheSpaceItWasCreatedInOrHomeSpaceKey)
                .isEqualTo("Work" to ElementWebRoomListSettings.HOME_SPACE_KEY)
            assertWithMessage("the tag of a section must use the prefix Element Web recognises, followed by a UUID")
                .that(createdSection.tag.value)
                .matches("element\\.io\\.section\\.[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
            assertWithMessage("the settings of Element Web that this app does not know about must survive the write")
                .that(storedContent["SomeOtherWebSetting"].toString())
                .isEqualTo("42")
            assertWithMessage("the room the section was created from must be moved into it")
                .that(tagsOfTheRoom)
                .containsExactly(createdSection.tag)
            val stateWithTheSection = awaitLastSequentialItem()
            assertWithMessage("the new section must be part of the sections shown, between People and Rooms")
                .that(stateWithTheSection.layout?.sectionsInDisplayOrder)
                .containsExactly(
                    RoomListSection.Invites,
                    RoomListSection.Favourites,
                    RoomListSection.People,
                    RoomListSection.Custom(createdSection.tag, "Work"),
                    RoomListSection.Rooms(),
                    RoomListSection.LowPriority,
                )
                .inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
