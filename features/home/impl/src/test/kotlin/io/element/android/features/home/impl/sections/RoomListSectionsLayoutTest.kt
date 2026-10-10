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
import io.element.android.features.home.impl.sections.custom.CustomRoomListSectionDefinition
import io.element.android.features.home.impl.sections.custom.ElementWebRoomListSettings
import io.element.android.features.home.impl.sections.custom.ReorderableRoomListSectionKey
import io.element.android.features.home.impl.sections.custom.RoomListSectionsSettings
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.tags.RoomTagName
import io.element.android.libraries.matrix.api.roomlist.RoomListFilter
import org.junit.Test

class RoomListSectionsLayoutTest {
    private val workTag = RoomTagName("element.io.section.work")
    private val familyTag = RoomTagName("element.io.section.family")
    private val workRoomId = RoomId("!work:domain")
    private val familyAndWorkRoomId = RoomId("!familyAndWork:domain")
    private val favouriteWorkRoomId = RoomId("!favouriteWork:domain")
    private val deletedSectionRoomId = RoomId("!deletedSection:domain")

    private fun settingsWithFamilyThenWork(showPeopleSection: Boolean = true) = RoomListSectionsSettings(
        customSectionsByTag = mapOf(
            workTag to
                CustomRoomListSectionDefinition(
                    tag = workTag,
                    name = "Work",
                    roomIdOfTheSpaceItWasCreatedInOrHomeSpaceKey = ElementWebRoomListSettings.HOME_SPACE_KEY
                ),
            familyTag to
                CustomRoomListSectionDefinition(
                    tag = familyTag,
                    name = "Family",
                    roomIdOfTheSpaceItWasCreatedInOrHomeSpaceKey = ElementWebRoomListSettings.HOME_SPACE_KEY
                ),
        ),
        reorderableSectionsInOrder = listOf(
            ReorderableRoomListSectionKey.People,
            ReorderableRoomListSectionKey.Custom(familyTag),
            ReorderableRoomListSectionKey.Custom(workTag),
            ReorderableRoomListSectionKey.Chats,
        ),
        showPeopleSection = showPeopleSection,
    )

    private val tagsOfRoomsInCustomSections = mapOf(
        workRoomId to setOf(workTag),
        familyAndWorkRoomId to setOf(workTag, familyTag),
        favouriteWorkRoomId to setOf(workTag, RoomTagName.FAVOURITE),
        deletedSectionRoomId to setOf(RoomTagName("element.io.section.deleted")),
    )

    @Test
    fun `sections are shown with Invites and Favourites first, the user order in the middle, and Low priority last`() {
        val layout = RoomListSectionsLayout(settingsWithFamilyThenWork(), tagsOfRoomsInCustomSections)

        assertWithMessage("custom sections and People and Rooms follow the stored order, between the fixed sections of Element Web")
            .that(layout.sectionsInDisplayOrder)
            .containsExactly(
                RoomListSection.Invites,
                RoomListSection.Favourites,
                RoomListSection.People,
                RoomListSection.Custom(familyTag, "Family"),
                RoomListSection.Custom(workTag, "Work"),
                RoomListSection.Rooms(alsoHoldsDirectMessages = false),
                RoomListSection.LowPriority,
            )
            .inOrder()
    }

    @Test
    fun `hiding People removes its section and lets Rooms, then titled Chats, hold the direct messages too`() {
        val layout = RoomListSectionsLayout(settingsWithFamilyThenWork(showPeopleSection = false), tagsOfRoomsInCustomSections)

        assertWithMessage("with People hidden, there must be no People section")
            .that(layout.sectionsInDisplayOrder)
            .doesNotContain(RoomListSection.People)
        val roomsFilter = layout.roomsOfSectionAmong(RoomListSection.Rooms(alsoHoldsDirectMessages = true), RoomListFilter.all())
        assertWithMessage("with People hidden, Rooms must not be limited to group rooms, but its filter was $roomsFilter")
            .that(roomsFilter.containsFilter(RoomListFilter.Category.Group))
            .isFalse()
    }

    @Test
    fun `a room in two custom sections only shows in the first one, in the order the user chose`() {
        val layout = RoomListSectionsLayout(settingsWithFamilyThenWork(), tagsOfRoomsInCustomSections)

        val familyRoomIds = layout.roomIdsOf(RoomListSection.Custom(familyTag, "Family"))
        val workRoomIds = layout.roomIdsOf(RoomListSection.Custom(workTag, "Work"))

        assertWithMessage("Family comes before Work, so the room tagged with both must be in Family only")
            .that(familyRoomIds)
            .containsExactly(familyAndWorkRoomId)
        assertWithMessage("Work must keep its own room but neither the room taken by Family nor the favourite one")
            .that(workRoomIds)
            .containsExactly(workRoomId)
    }

    @Test
    fun `a favourite stays in Favourites even when it also has the tag of a custom section, as Element Web shows Favourites first`() {
        val layout = RoomListSectionsLayout(settingsWithFamilyThenWork(), tagsOfRoomsInCustomSections)

        val workRoomIds = layout.roomIdsOf(RoomListSection.Custom(workTag, "Work"))

        assertWithMessage("the favourite room tagged Work must not also be listed in Work")
            .that(workRoomIds)
            .doesNotContain(favouriteWorkRoomId)
    }

    @Test
    fun `People, Rooms and Low priority leave out every room shown in a custom section, but not the rooms of a deleted section`() {
        val layout = RoomListSectionsLayout(settingsWithFamilyThenWork(), tagsOfRoomsInCustomSections)

        listOf(RoomListSection.People, RoomListSection.Rooms(), RoomListSection.LowPriority).forEach { section ->
            val excludedRoomIds = layout.roomsOfSectionAmong(section, RoomListFilter.all()).excludedRoomIds()
            assertWithMessage(
                "$section must leave out the rooms of Family and Work so they are not listed twice, " +
                    "and keep the room whose section was deleted, as it falls back to People or Rooms",
            ).that(excludedRoomIds)
                .containsExactly(workRoomId, familyAndWorkRoomId)
        }
    }

    @Test
    fun `the filter selected by the user still applies inside each section`() {
        val layout = RoomListSectionsLayout(settingsWithFamilyThenWork(), tagsOfRoomsInCustomSections)

        layout.sectionsInDisplayOrder.forEach { section ->
            val roomsOfSection = layout.roomsOfSectionAmong(section, RoomListFilter.Unread)
            assertWithMessage("with the Unread filter selected, $section must only show its unread rooms, but its filter was $roomsOfSection")
                .that(roomsOfSection.containsFilter(RoomListFilter.Unread))
                .isTrue()
        }
    }

    @Test
    fun `a pending invite only belongs to the Invites section, even when it matches the filters of another section`() {
        val pendingInvite = aRoomListRoomSummary(displayType = RoomSummaryDisplayType.INVITE, isDm = true)
        val layout = RoomListSectionsLayout(settingsWithFamilyThenWork(), tagsOfRoomsInCustomSections)

        val sectionsContainingTheInvite = layout.sectionsInDisplayOrder.filter { section -> section.containsRoom(pendingInvite) }

        assertWithMessage("an invite to a direct message must be listed under Invites, not People")
            .that(sectionsContainingTheInvite)
            .containsExactly(RoomListSection.Invites)
    }

    @Test
    fun `the persistence keys of the fixed sections never change, so the sections a user collapsed stay collapsed after an update`() {
        val persistenceKeyBySection = listOf(
            RoomListSection.Invites,
            RoomListSection.Favourites,
            RoomListSection.People,
            RoomListSection.Rooms(),
            RoomListSection.LowPriority,
        ).associateWith { section -> section.persistenceKey }

        assertWithMessage(
            "the collapsed sections are stored by persistence key; changing a key silently expands that section for every user",
        ).that(persistenceKeyBySection)
            .containsExactly(
                RoomListSection.Invites,
                "invites",
                RoomListSection.Favourites,
                "m.favourite",
                RoomListSection.People,
                "m.dm",
                RoomListSection.Rooms(),
                "chats",
                RoomListSection.LowPriority,
                "m.lowpriority",
            )
    }

    private fun RoomListSectionsLayout.roomIdsOf(section: RoomListSection.Custom): List<RoomId> {
        return roomsOfSectionAmong(section, RoomListFilter.all()).includedRoomIds()
    }

    private fun RoomListFilter.includedRoomIds(): List<RoomId> = when (this) {
        is RoomListFilter.Identifiers -> values
        is RoomListFilter.All -> filters.flatMap { it.includedRoomIds() }
        else -> emptyList()
    }

    private fun RoomListFilter.excludedRoomIds(): List<RoomId> = when (this) {
        is RoomListFilter.NonIdentifiers -> values
        is RoomListFilter.All -> filters.flatMap { it.excludedRoomIds() }
        else -> emptyList()
    }

    private fun RoomListFilter.containsFilter(searchedFilter: RoomListFilter): Boolean {
        return this == searchedFilter || this is RoomListFilter.All && filters.any { it.containsFilter(searchedFilter) }
    }
}
