/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.sections

import androidx.annotation.StringRes
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.model.RoomSummaryDisplayType
import io.element.android.libraries.matrix.api.roomlist.RoomListFilter

/**
 * The sections of the room list, in display order, mirroring the default sections of Element Web.
 *
 * Every room belongs to exactly one section: a pending invite belongs to [Invites], whatever its tags.
 * Otherwise a room belongs to the first section whose tag it carries, so a room tagged both favourite and low priority is a favourite.
 * Untagged rooms belong to [People] when they are direct messages, and to [Rooms] otherwise.
 */
enum class RoomListSection(
    val persistenceKey: String,
    @StringRes val titleResource: Int,
    private val roomsOfThisSectionFilter: RoomListFilter,
) {
    Invites(
        persistenceKey = "invites",
        titleResource = R.string.screen_roomlist_filter_invites,
        roomsOfThisSectionFilter = RoomListFilter.Invite,
    ),
    Favourites(
        persistenceKey = "m.favourite",
        titleResource = R.string.screen_roomlist_filter_favourites,
        roomsOfThisSectionFilter = RoomListFilter.Favorite,
    ),
    People(
        persistenceKey = "m.dm",
        titleResource = R.string.screen_roomlist_filter_people,
        roomsOfThisSectionFilter = RoomListFilter.all(
            RoomListFilter.Category.People,
            RoomListFilter.NonFavorite,
            RoomListFilter.NonLowPriority,
        ),
    ),
    Rooms(
        persistenceKey = "chats",
        titleResource = R.string.screen_roomlist_filter_rooms,
        roomsOfThisSectionFilter = RoomListFilter.all(
            RoomListFilter.Category.Group,
            RoomListFilter.NonFavorite,
            RoomListFilter.NonLowPriority,
        ),
    ),
    LowPriority(
        persistenceKey = "m.lowpriority",
        titleResource = R.string.screen_roomlist_section_low_priority,
        roomsOfThisSectionFilter = RoomListFilter.all(
            RoomListFilter.LowPriority,
            RoomListFilter.NonFavorite,
        ),
    );

    /**
     * The rooms that the user selected with the filter chips and the space filter, narrowed down to the rooms of this section.
     */
    fun roomsOfThisSectionAmong(userSelectedFilter: RoomListFilter): RoomListFilter {
        return RoomListFilter.all(userSelectedFilter, roomsOfThisSectionFilter)
    }

    /**
     * The SDK has no filter excluding invites alone, and excluding them with [RoomListFilter.Joined] would also drop knocked rooms,
     * so the invites matched by the tag and category filters are left out here instead.
     */
    fun containsRoom(roomSummary: RoomListRoomSummary): Boolean {
        val isPendingInvite = roomSummary.displayType == RoomSummaryDisplayType.INVITE
        return when (this) {
            Invites -> isPendingInvite
            Favourites, People, Rooms, LowPriority -> !isPendingInvite
        }
    }

    companion object {
        /**
         * When the only section with rooms is [Rooms], Element Web draws the list without any header, as if sections were disabled.
         */
        val sectionThatIsDrawnWithoutHeaderWhenItIsTheOnlyOneWithRooms = Rooms
    }
}
