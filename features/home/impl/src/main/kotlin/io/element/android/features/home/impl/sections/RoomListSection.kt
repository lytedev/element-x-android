/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.sections

import androidx.compose.runtime.Immutable
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.model.RoomSummaryDisplayType
import io.element.android.features.home.impl.sections.custom.ElementWebRoomListSettings
import io.element.android.features.home.impl.sections.custom.ReorderableRoomListSectionKey
import io.element.android.libraries.matrix.api.room.tags.RoomTagName

/**
 * A section of the room list. The order of the sections and which section each room belongs to are decided by [RoomListSectionsLayout].
 */
@Immutable
sealed interface RoomListSection {
    /**
     * Identifies the section in local storage, such as the collapsed sections.
     */
    val persistenceKey: String

    /**
     * Null for Invites, Favourites and Low priority, which always stay at the top or the bottom of the list.
     */
    val reorderableKey: ReorderableRoomListSectionKey?

    data object Invites : RoomListSection {
        override val persistenceKey = "invites"
        override val reorderableKey = null
    }

    data object Favourites : RoomListSection {
        override val persistenceKey = RoomTagName.FAVOURITE.value
        override val reorderableKey = null
    }

    data object People : RoomListSection {
        override val persistenceKey = "m.dm"
        override val reorderableKey = ReorderableRoomListSectionKey.People
    }

    /**
     * Titled Chats instead of Rooms when the People section is hidden, as it then also holds the direct messages.
     */
    data class Rooms(val alsoHoldsDirectMessages: Boolean = false) : RoomListSection {
        override val persistenceKey = ElementWebRoomListSettings.CHATS_SECTION_ORDER_KEY
        override val reorderableKey = ReorderableRoomListSectionKey.Chats
    }

    data object LowPriority : RoomListSection {
        override val persistenceKey = RoomTagName.LOW_PRIORITY.value
        override val reorderableKey = null
    }

    data class Custom(val tag: RoomTagName, val name: String) : RoomListSection {
        override val persistenceKey = tag.value
        override val reorderableKey = ReorderableRoomListSectionKey.Custom(tag)
    }

    /**
     * The SDK has no filter excluding invites alone, and excluding them with Joined would also drop knocked rooms,
     * so the invites matched by the filters of the other sections are left out here instead.
     */
    fun containsRoom(roomSummary: RoomListRoomSummary): Boolean {
        val isPendingInvite = roomSummary.displayType == RoomSummaryDisplayType.INVITE
        return if (this == Invites) isPendingInvite else !isPendingInvite
    }
}
