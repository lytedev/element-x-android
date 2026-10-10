/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.sections

/**
 * Knows where the header and the rooms of each section are placed in the lazy list of the room list,
 * so that the visible items of the lazy list can be turned back into visible rooms of each section.
 */
internal class RoomListSectionsLazyListLayout(
    numberOfLazyListItemsBeforeTheSections: Int,
    sections: List<RoomListSectionContent>,
    showSectionHeaders: Boolean,
) {
    private data class SectionRoomsPlacement(
        val section: RoomListSection,
        val lazyListIndexOfFirstRoom: Int,
        val numberOfShownRooms: Int,
    ) {
        val lazyListIndexOfLastRoom = lazyListIndexOfFirstRoom + numberOfShownRooms - 1
    }

    private val sectionRoomsPlacements: List<SectionRoomsPlacement> = buildList {
        var lazyListIndexOfNextItem = numberOfLazyListItemsBeforeTheSections
        sections.forEach { sectionContent ->
            if (showSectionHeaders) {
                lazyListIndexOfNextItem += 1
            }
            val placement = SectionRoomsPlacement(
                section = sectionContent.section,
                lazyListIndexOfFirstRoom = lazyListIndexOfNextItem,
                numberOfShownRooms = sectionContent.visibleSummaries.size,
            )
            add(placement)
            lazyListIndexOfNextItem += placement.numberOfShownRooms
        }
    }

    /**
     * @return for each section with at least one visible room, the indices of its visible rooms among the rooms of that section.
     */
    fun visibleRoomRangesBySection(visibleLazyListIndices: IntRange): Map<RoomListSection, IntRange> {
        return sectionRoomsPlacements.mapNotNull { placement ->
            val firstVisibleLazyListIndexOfSection = maxOf(visibleLazyListIndices.first, placement.lazyListIndexOfFirstRoom)
            val lastVisibleLazyListIndexOfSection = minOf(visibleLazyListIndices.last, placement.lazyListIndexOfLastRoom)
            if (firstVisibleLazyListIndexOfSection > lastVisibleLazyListIndexOfSection) return@mapNotNull null
            val firstVisibleRoomOfSection = firstVisibleLazyListIndexOfSection - placement.lazyListIndexOfFirstRoom
            val lastVisibleRoomOfSection = lastVisibleLazyListIndexOfSection - placement.lazyListIndexOfFirstRoom
            placement.section to firstVisibleRoomOfSection..lastVisibleRoomOfSection
        }.toMap()
    }
}
