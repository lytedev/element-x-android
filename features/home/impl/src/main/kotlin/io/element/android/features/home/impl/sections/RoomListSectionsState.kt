/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.sections

import androidx.compose.runtime.Immutable
import io.element.android.features.home.impl.sections.custom.SectionMoveDirection
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@Immutable
data class RoomListSectionsState(
    val isEnabled: Boolean,
    val layout: RoomListSectionsLayout?,
    val collapsedSectionKeys: ImmutableSet<String>,
    val dialog: RoomListSectionsDialog,
    val eventSink: (RoomListSectionsEvent) -> Unit,
)

/**
 * At most one section management dialog or sheet is shown at a time.
 */
@Immutable
sealed interface RoomListSectionsDialog {
    data object None : RoomListSectionsDialog

    data class SectionOptions(
        val section: RoomListSection,
        val canMoveTowardsTop: Boolean,
        val canMoveTowardsBottom: Boolean,
    ) : RoomListSectionsDialog

    /**
     * @property roomToMoveIntoTheNewSection set when the section is created from the room context menu, so the room moves into it.
     */
    data class CreateSection(val roomToMoveIntoTheNewSection: RoomId?) : RoomListSectionsDialog

    data class RenameSection(val section: RoomListSection.Custom) : RoomListSectionsDialog

    data class ConfirmSectionDeletion(val section: RoomListSection.Custom) : RoomListSectionsDialog

    data class MoveRoomToSection(
        val roomId: RoomId,
        val roomName: String?,
        val sectionOfTheRoom: RoomListSection?,
        val customSections: ImmutableList<RoomListSection.Custom> = persistentListOf(),
    ) : RoomListSectionsDialog
}

sealed interface RoomListSectionsEvent {
    data class ToggleSectionCollapsed(val section: RoomListSection) : RoomListSectionsEvent
    data class ShowSectionOptions(val section: RoomListSection) : RoomListSectionsEvent
    data class MarkSectionAsRead(val section: RoomListSection) : RoomListSectionsEvent
    data class MoveSection(val section: RoomListSection, val direction: SectionMoveDirection) : RoomListSectionsEvent
    data class SetShowPeopleSection(val showPeopleSection: Boolean) : RoomListSectionsEvent
    data class StartCreatingSection(val roomToMoveIntoTheNewSection: RoomId?) : RoomListSectionsEvent
    data class CreateSection(
        val name: String,
        val roomToMoveIntoTheNewSection: RoomId?,
        val roomIdOfTheCurrentSpaceOrHomeSpaceKey: String,
    ) : RoomListSectionsEvent
    data class StartRenamingSection(val section: RoomListSection.Custom) : RoomListSectionsEvent
    data class RenameSection(val section: RoomListSection.Custom, val newName: String) : RoomListSectionsEvent
    data class StartDeletingSection(val section: RoomListSection.Custom) : RoomListSectionsEvent
    data class DeleteSection(val section: RoomListSection.Custom) : RoomListSectionsEvent
    data class ShowMoveRoomToSection(val roomId: RoomId, val roomName: String?) : RoomListSectionsEvent

    data class MoveRoomToSection(val roomId: RoomId, val targetSectionOrNullToLeaveTheSection: RoomListSection.Custom?) : RoomListSectionsEvent
    data object DismissDialog : RoomListSectionsEvent
}

fun aDisabledRoomListSectionsState() = RoomListSectionsState(
    isEnabled = false,
    layout = null,
    collapsedSectionKeys = persistentSetOf(),
    dialog = RoomListSectionsDialog.None,
    eventSink = {},
)
