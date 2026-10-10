/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.sections

import io.element.android.features.home.impl.sections.custom.ReorderableRoomListSectionKey
import io.element.android.features.home.impl.sections.custom.RoomListSectionsSettings
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.tags.RoomTagName
import io.element.android.libraries.matrix.api.roomlist.RoomListFilter

/**
 * Decides the order of the sections and which section each room belongs to, with the rules of Element Web
 * (element-hq/element-web@171955a9e208e8aca41383a786c3f73341e8f76c, apps/web/src/stores/room-list-v3/skip-list/filters/SectionFilter.ts):
 *
 * - A pending invite belongs to Invites, whatever its tags.
 * - Otherwise a room belongs to the first section, in display order, whose tag it has: Favourites, then the custom sections in the
 *   order the user chose, then Low priority. Tags of deleted sections are ignored.
 * - Otherwise a direct message belongs to People, if shown, and any other room to Rooms.
 */
class RoomListSectionsLayout(
    val settings: RoomListSectionsSettings,
    tagsOfRoomsInCustomSections: Map<RoomId, Set<RoomTagName>>,
) {
    private val roomIdsByCustomSectionTag: Map<RoomTagName, List<RoomId>> = tagsOfRoomsInCustomSections
        .filterValues { tags -> RoomTagName.FAVOURITE !in tags }
        .mapNotNull { (roomId, tags) ->
            val firstCustomSectionOfTheRoom = settings.customSectionsInOrder.firstOrNull { definition -> definition.tag in tags }
            firstCustomSectionOfTheRoom?.let { definition -> definition.tag to roomId }
        }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })

    private val roomIdsInAnyCustomSection: List<RoomId> = roomIdsByCustomSectionTag.values.flatten()

    val sectionsInDisplayOrder: List<RoomListSection> = buildList {
        add(RoomListSection.Invites)
        add(RoomListSection.Favourites)
        settings.reorderableSectionsInOrder.forEach { reorderableSectionKey ->
            when (reorderableSectionKey) {
                ReorderableRoomListSectionKey.People -> if (settings.showPeopleSection) add(RoomListSection.People)
                ReorderableRoomListSectionKey.Chats -> add(RoomListSection.Rooms(alsoHoldsDirectMessages = !settings.showPeopleSection))
                is ReorderableRoomListSectionKey.Custom -> settings.customSectionsByTag[reorderableSectionKey.tag]?.let { definition ->
                    add(RoomListSection.Custom(tag = definition.tag, name = definition.name))
                }
            }
        }
        add(RoomListSection.LowPriority)
    }

    fun roomIdOfTheSpaceTheSectionWasCreatedInOrHomeSpaceKey(section: RoomListSection.Custom): String? {
        return settings.customSectionsByTag[section.tag]?.roomIdOfTheSpaceItWasCreatedInOrHomeSpaceKey
    }

    fun roomsOfSectionAmong(section: RoomListSection, userSelectedFilter: RoomListFilter): RoomListFilter {
        return RoomListFilter.all(userSelectedFilter, roomsOfSection(section))
    }

    private fun roomsOfSection(section: RoomListSection): RoomListFilter {
        val notInAnyCustomSection = RoomListFilter.NonIdentifiers(roomIdsInAnyCustomSection)
        return when (section) {
            RoomListSection.Invites -> RoomListFilter.Invite
            RoomListSection.Favourites -> RoomListFilter.Favorite
            is RoomListSection.Custom -> RoomListFilter.all(
                RoomListFilter.Identifiers(roomIdsByCustomSectionTag[section.tag].orEmpty()),
                RoomListFilter.NonFavorite,
            )
            RoomListSection.People -> RoomListFilter.all(
                RoomListFilter.Category.People,
                RoomListFilter.NonFavorite,
                RoomListFilter.NonLowPriority,
                notInAnyCustomSection,
            )
            is RoomListSection.Rooms -> RoomListFilter.All(
                listOfNotNull(
                    RoomListFilter.Category.Group.takeUnless { section.alsoHoldsDirectMessages },
                    RoomListFilter.NonFavorite,
                    RoomListFilter.NonLowPriority,
                    notInAnyCustomSection,
                )
            )
            RoomListSection.LowPriority -> RoomListFilter.all(
                RoomListFilter.LowPriority,
                RoomListFilter.NonFavorite,
                notInAnyCustomSection,
            )
        }
    }

    companion object {
        /**
         * When the only section with rooms is Rooms, Element Web draws the list without any header, as if sections were disabled.
         */
        fun isDrawnWithoutHeaders(nonEmptySections: List<RoomListSection>): Boolean {
            return nonEmptySections.isEmpty() || nonEmptySections.singleOrNull() is RoomListSection.Rooms
        }
    }
}
