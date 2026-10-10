/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.home.impl.datasource.RoomListDataSource
import io.element.android.features.home.impl.datasource.RoomListSectionWithItsRooms
import io.element.android.features.home.impl.sections.custom.CustomRoomListSectionDefinition
import io.element.android.features.home.impl.sections.custom.ElementWebRoomListSettings
import io.element.android.features.home.impl.sections.custom.RoomListSectionsSettings
import io.element.android.features.home.impl.sections.custom.RoomListSectionsSettingsStore
import io.element.android.features.home.impl.sections.custom.RoomSectionMover
import io.element.android.features.home.impl.sections.custom.RoomSectionTagsIndex
import io.element.android.features.home.impl.sections.custom.SectionMoveDirection
import io.element.android.features.home.impl.sections.custom.canMove
import io.element.android.features.home.impl.sections.custom.withNewCustomSection
import io.element.android.features.home.impl.sections.custom.withRenamedCustomSection
import io.element.android.features.home.impl.sections.custom.withSectionMoved
import io.element.android.features.home.impl.sections.custom.withoutCustomSection
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages the sections of the room list: which ones exist and in which order, which are collapsed,
 * and the dialogs to create, rename, reorder and delete custom sections and to move rooms between them.
 */
@Inject
class RoomListSectionsPresenter(
    private val featureFlagService: FeatureFlagService,
    private val sessionPreferencesStore: SessionPreferencesStore,
    private val roomListSectionsSettingsStore: RoomListSectionsSettingsStore,
    private val roomSectionTagsIndex: RoomSectionTagsIndex,
    private val roomSectionMover: RoomSectionMover,
    private val roomListDataSource: RoomListDataSource,
) : Presenter<RoomListSectionsState> {
    @Composable
    override fun present(): RoomListSectionsState {
        val coroutineScope = rememberCoroutineScope()
        val isEnabled by produceState(initialValue = false) {
            featureFlagService.isFeatureEnabledFlow(FeatureFlags.RoomListSections).collect { value = it }
        }
        LaunchedEffect(isEnabled) {
            if (isEnabled) {
                roomListSectionsSettingsStore.startFollowingTheStoredSettingsIfNeeded()
                roomSectionTagsIndex.startIndexingIfNeeded()
            }
        }
        val settings by roomListSectionsSettingsStore.settingsFlow.collectAsState()
        val tagsOfRoomsInCustomSections by roomSectionTagsIndex.tagsOfRoomsInCustomSections.collectAsState()
        val layout = remember(isEnabled, settings, tagsOfRoomsInCustomSections) {
            if (isEnabled) RoomListSectionsLayout(settings, tagsOfRoomsInCustomSections) else null
        }
        LaunchedEffect(layout) {
            val currentLayout = layout
            val sectionsToShow = currentLayout?.let { nonNullLayout ->
                nonNullLayout.sectionsInDisplayOrder.map { section ->
                    RoomListSectionWithItsRooms(
                        section = section,
                        roomsOfSectionAmong = { userSelectedFilter -> nonNullLayout.roomsOfSectionAmong(section, userSelectedFilter) },
                    )
                }
            }.orEmpty()
            roomListDataSource.updateSections(sectionsToShow)
        }
        val collapsedSectionKeys by remember {
            sessionPreferencesStore.getCollapsedRoomListSectionKeys().map { it.toImmutableSet() }
        }.collectAsState(persistentSetOf())
        var dialog by remember { mutableStateOf<RoomListSectionsDialog>(RoomListSectionsDialog.None) }

        fun editSettingsInBackground(edit: (RoomListSectionsSettings) -> RoomListSectionsSettings) {
            coroutineScope.launch {
                roomListSectionsSettingsStore.edit(edit).onFailure { Timber.e(it, "Could not save the room list sections") }
            }
        }

        fun handleEvent(event: RoomListSectionsEvent) {
            when (event) {
                is RoomListSectionsEvent.ToggleSectionCollapsed -> coroutineScope.launch {
                    val sectionKey = event.section.persistenceKey
                    val collapsedSectionKeysAfterToggle = if (sectionKey in collapsedSectionKeys) {
                        collapsedSectionKeys - sectionKey
                    } else {
                        collapsedSectionKeys + sectionKey
                    }
                    sessionPreferencesStore.setCollapsedRoomListSectionKeys(collapsedSectionKeysAfterToggle)
                }
                is RoomListSectionsEvent.ShowSectionOptions -> {
                    val reorderableKey = event.section.reorderableKey
                    dialog = RoomListSectionsDialog.SectionOptions(
                        section = event.section,
                        canMoveTowardsTop = reorderableKey != null && settings.canMove(reorderableKey, SectionMoveDirection.TowardsTop),
                        canMoveTowardsBottom = reorderableKey != null && settings.canMove(reorderableKey, SectionMoveDirection.TowardsBottom),
                    )
                }
                is RoomListSectionsEvent.MoveSection -> {
                    dialog = RoomListSectionsDialog.None
                    val reorderableKey = event.section.reorderableKey ?: return
                    editSettingsInBackground { it.withSectionMoved(reorderableKey, event.direction) }
                }
                is RoomListSectionsEvent.SetShowPeopleSection -> {
                    dialog = RoomListSectionsDialog.None
                    editSettingsInBackground { it.copy(showPeopleSection = event.showPeopleSection) }
                }
                is RoomListSectionsEvent.StartCreatingSection -> {
                    dialog = RoomListSectionsDialog.CreateSection(roomToMoveIntoTheNewSection = event.roomToMoveIntoTheNewSection)
                }
                is RoomListSectionsEvent.CreateSection -> {
                    dialog = RoomListSectionsDialog.None
                    coroutineScope.launch {
                        val newSection = CustomRoomListSectionDefinition(
                            tag = ElementWebRoomListSettings.newCustomSectionTag(),
                            name = event.name,
                            roomIdOfTheSpaceItWasCreatedInOrHomeSpaceKey = event.roomIdOfTheCurrentSpaceOrHomeSpaceKey,
                        )
                        roomListSectionsSettingsStore.edit { it.withNewCustomSection(newSection) }
                            .onFailure { Timber.e(it, "Could not create the room list section") }
                            .onSuccess {
                                val roomToMove = event.roomToMoveIntoTheNewSection ?: return@onSuccess
                                roomSectionMover.moveRoom(roomToMove, newSection.tag, roomListSectionsSettingsStore.settingsFlow.value)
                                    .onFailure { Timber.e(it, "Could not move room $roomToMove into the new section") }
                            }
                    }
                }
                is RoomListSectionsEvent.StartRenamingSection -> dialog = RoomListSectionsDialog.RenameSection(event.section)
                is RoomListSectionsEvent.RenameSection -> {
                    dialog = RoomListSectionsDialog.None
                    editSettingsInBackground { it.withRenamedCustomSection(event.section.tag, event.newName) }
                }
                is RoomListSectionsEvent.StartDeletingSection -> dialog = RoomListSectionsDialog.ConfirmSectionDeletion(event.section)
                is RoomListSectionsEvent.DeleteSection -> {
                    dialog = RoomListSectionsDialog.None
                    editSettingsInBackground { it.withoutCustomSection(event.section.tag) }
                }
                is RoomListSectionsEvent.ShowMoveRoomToSection -> {
                    val sectionTagOfTheRoom = roomSectionTagsIndex.tagsOf(event.roomId)?.let { tags ->
                        RoomSectionMover.sectionTagDecidingTheSectionOf(tags, settings)
                    }
                    val customSections = settings.customSectionsInOrder.map { RoomListSection.Custom(tag = it.tag, name = it.name) }
                    dialog = RoomListSectionsDialog.MoveRoomToSection(
                        roomId = event.roomId,
                        roomName = event.roomName,
                        sectionOfTheRoom = customSections.firstOrNull { it.tag == sectionTagOfTheRoom },
                        customSections = customSections.toImmutableList(),
                    )
                }
                is RoomListSectionsEvent.MoveRoomToSection -> {
                    dialog = RoomListSectionsDialog.None
                    coroutineScope.launch {
                        roomSectionMover.moveRoom(event.roomId, event.targetSectionOrNullToLeaveTheSection?.tag, settings)
                            .onFailure { Timber.e(it, "Could not move room ${event.roomId} to another section") }
                    }
                }
                RoomListSectionsEvent.DismissDialog -> dialog = RoomListSectionsDialog.None
            }
        }

        return RoomListSectionsState(
            isEnabled = isEnabled,
            layout = layout,
            collapsedSectionKeys = collapsedSectionKeys,
            dialog = dialog,
            eventSink = ::handleEvent,
        )
    }
}
