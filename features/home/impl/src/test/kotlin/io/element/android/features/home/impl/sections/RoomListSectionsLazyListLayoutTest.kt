/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.sections

import com.google.common.truth.Truth.assertWithMessage
import io.element.android.features.home.impl.model.aRoomListRoomSummary
import org.junit.Test

class RoomListSectionsLazyListLayoutTest {
    private fun aSectionWithRooms(section: RoomListSection, numberOfRooms: Int, isCollapsed: Boolean = false) = aRoomListSectionContent(
        section = section,
        isCollapsed = isCollapsed,
        summaries = List(numberOfRooms) { roomIndex -> aRoomListRoomSummary(id = "!${section.persistenceKey}$roomIndex:domain") },
    )

    @Test
    fun `visible lazy list items are turned into the visible rooms of each section, skipping the banner and the section headers`() {
        val layout = RoomListSectionsLazyListLayout(
            numberOfLazyListItemsBeforeTheSections = 1,
            sections = listOf(
                aSectionWithRooms(RoomListSection.Favourites, numberOfRooms = 3),
                aSectionWithRooms(RoomListSection.Rooms(), numberOfRooms = 10),
            ),
            showSectionHeaders = true,
        )

        // Lazy list: 0 banner, 1 Favourites header, 2..4 favourites, 5 Rooms header, 6..15 rooms.
        val visibleRoomRangesBySection = layout.visibleRoomRangesBySection(visibleLazyListIndices = 3..8)

        assertWithMessage(
            "lazy items 3 to 8 show favourites 1 and 2, the Rooms header, then rooms 0 to 2, " +
                "so pagination and room subscriptions must be driven by those rooms of each section",
        ).that(visibleRoomRangesBySection).containsExactly(
            RoomListSection.Favourites,
            1..2,
            RoomListSection.Rooms(),
            0..2,
        )
    }

    @Test
    fun `a collapsed section shows no room, so only its header shifts the rooms of the sections after it`() {
        val layout = RoomListSectionsLazyListLayout(
            numberOfLazyListItemsBeforeTheSections = 0,
            sections = listOf(
                aSectionWithRooms(RoomListSection.Favourites, numberOfRooms = 50, isCollapsed = true),
                aSectionWithRooms(RoomListSection.Rooms(), numberOfRooms = 10),
            ),
            showSectionHeaders = true,
        )

        // Lazy list: 0 Favourites header, 1 Rooms header, 2..11 rooms.
        val visibleRoomRangesBySection = layout.visibleRoomRangesBySection(visibleLazyListIndices = 0..5)

        assertWithMessage(
            "the 50 favourites are collapsed, so they must not count as visible nor push the rooms of the Rooms section down",
        ).that(visibleRoomRangesBySection).containsExactly(RoomListSection.Rooms(), 0..3)
    }

    @Test
    fun `without section headers the rooms of a section start right after the items placed before the sections`() {
        val layout = RoomListSectionsLazyListLayout(
            numberOfLazyListItemsBeforeTheSections = 0,
            sections = listOf(aSectionWithRooms(RoomListSection.Rooms(), numberOfRooms = 30)),
            showSectionHeaders = false,
        )

        val visibleRoomRangesBySection = layout.visibleRoomRangesBySection(visibleLazyListIndices = 10..20)

        assertWithMessage(
            "when Rooms is the only section it is drawn without header, so lazy index and room index must be the same",
        ).that(visibleRoomRangesBySection).containsExactly(RoomListSection.Rooms(), 10..20)
    }
}
