/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.sections

import com.google.common.truth.Truth.assertWithMessage
import io.element.android.features.home.impl.model.RoomSummaryDisplayType
import io.element.android.features.home.impl.model.aRoomListRoomSummary
import io.element.android.libraries.matrix.api.roomlist.RoomListFilter
import org.junit.Test

class RoomListSectionTest {
    @Test
    fun `sections are displayed in the order of Element Web, with Invites first and Low priority last`() {
        val sectionsInDisplayOrder = RoomListSection.entries

        assertWithMessage("the declaration order of RoomListSection is the display order of the sections")
            .that(sectionsInDisplayOrder)
            .containsExactly(
                RoomListSection.Invites,
                RoomListSection.Favourites,
                RoomListSection.People,
                RoomListSection.Rooms,
                RoomListSection.LowPriority,
            )
            .inOrder()
    }

    @Test
    fun `the persistence keys of the sections never change, so the sections a user collapsed stay collapsed after an update`() {
        val persistenceKeyBySection = RoomListSection.entries.associateWith { section -> section.persistenceKey }

        assertWithMessage(
            "the collapsed sections are stored by persistence key; changing a key silently expands that section for every user. " +
                "The keys reuse the section tags of Element Web where one exists",
        ).that(persistenceKeyBySection)
            .containsExactly(
                RoomListSection.Invites,
                "invites",
                RoomListSection.Favourites,
                "m.favourite",
                RoomListSection.People,
                "m.dm",
                RoomListSection.Rooms,
                "chats",
                RoomListSection.LowPriority,
                "m.lowpriority",
            )
    }

    @Test
    fun `a favourite is never repeated in the People, Rooms or Low priority sections`() {
        val sectionsAfterFavourites = listOf(RoomListSection.People, RoomListSection.Rooms, RoomListSection.LowPriority)

        sectionsAfterFavourites.forEach { section ->
            val roomsOfSection = section.roomsOfThisSectionAmong(RoomListFilter.all())
            assertWithMessage(
                "a room tagged favourite belongs to the Favourites section only, so the filter of $section must exclude favourites, " +
                    "but it was $roomsOfSection",
            ).that(roomsOfSection.containsFilter(RoomListFilter.NonFavorite)).isTrue()
        }
    }

    @Test
    fun `a low priority room is never repeated in the People or Rooms sections`() {
        listOf(RoomListSection.People, RoomListSection.Rooms).forEach { section ->
            val roomsOfSection = section.roomsOfThisSectionAmong(RoomListFilter.all())
            assertWithMessage(
                "a room tagged low priority belongs to the Low priority section, so the filter of $section must exclude it, " +
                    "but it was $roomsOfSection",
            ).that(roomsOfSection.containsFilter(RoomListFilter.NonLowPriority)).isTrue()
        }
    }

    @Test
    fun `the filter selected by the user still applies inside each section`() {
        RoomListSection.entries.forEach { section ->
            val roomsOfSection = section.roomsOfThisSectionAmong(RoomListFilter.Unread)
            assertWithMessage(
                "with the Unread filter selected, $section must only show its unread rooms, but its filter was $roomsOfSection",
            ).that(roomsOfSection.containsFilter(RoomListFilter.Unread)).isTrue()
        }
    }

    @Test
    fun `a pending invite only belongs to the Invites section, even when it matches the filters of another section`() {
        val pendingInvite = aRoomListRoomSummary(displayType = RoomSummaryDisplayType.INVITE, isDm = true)

        val sectionsContainingTheInvite = RoomListSection.entries.filter { section -> section.containsRoom(pendingInvite) }

        assertWithMessage("an invite to a direct message must be listed under Invites, not People")
            .that(sectionsContainingTheInvite)
            .containsExactly(RoomListSection.Invites)
    }

    @Test
    fun `a knocked room is kept in the sections, as only pending invites are moved to the Invites section`() {
        val knockedRoom = aRoomListRoomSummary(displayType = RoomSummaryDisplayType.KNOCKED)

        val sectionsContainingTheKnockedRoom = RoomListSection.entries.filter { section -> section.containsRoom(knockedRoom) }

        assertWithMessage("a knocked room must still be shown, so every section but Invites must accept it")
            .that(sectionsContainingTheKnockedRoom)
            .containsExactly(RoomListSection.Favourites, RoomListSection.People, RoomListSection.Rooms, RoomListSection.LowPriority)
    }

    private fun RoomListFilter.containsFilter(searchedFilter: RoomListFilter): Boolean {
        return this == searchedFilter || this is RoomListFilter.All && filters.any { it.containsFilter(searchedFilter) }
    }
}
