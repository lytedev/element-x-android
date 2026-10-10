/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.sections.custom

import io.element.android.libraries.matrix.api.room.tags.RoomTagName

/**
 * Like Element Web, a new custom section is placed right above Chats, so below the custom sections created before it.
 */
fun RoomListSectionsSettings.withNewCustomSection(definition: CustomRoomListSectionDefinition): RoomListSectionsSettings {
    val newSectionKey = ReorderableRoomListSectionKey.Custom(definition.tag)
    val indexOfChats = reorderableSectionsInOrder.indexOf(ReorderableRoomListSectionKey.Chats)
    val orderWithNewSection = reorderableSectionsInOrder.toMutableList().apply {
        if (indexOfChats >= 0) add(indexOfChats, newSectionKey) else add(newSectionKey)
    }
    return copy(
        customSectionsByTag = customSectionsByTag + (definition.tag to definition),
        reorderableSectionsInOrder = orderWithNewSection,
    )
}

fun RoomListSectionsSettings.withRenamedCustomSection(tag: RoomTagName, newName: String): RoomListSectionsSettings {
    val renamedDefinition = customSectionsByTag[tag]?.copy(name = newName) ?: return this
    return copy(customSectionsByTag = customSectionsByTag + (tag to renamedDefinition))
}

/**
 * Like Element Web, the rooms keep the tag of a deleted section: they fall back to People or Chats, as no section matches that tag anymore.
 */
fun RoomListSectionsSettings.withoutCustomSection(tag: RoomTagName): RoomListSectionsSettings {
    return copy(
        customSectionsByTag = customSectionsByTag - tag,
        reorderableSectionsInOrder = reorderableSectionsInOrder - ReorderableRoomListSectionKey.Custom(tag),
    )
}

/**
 * Swaps the section with its visible neighbour, so a hidden People section never makes a move look like it did nothing.
 */
fun RoomListSectionsSettings.withSectionMoved(sectionKey: ReorderableRoomListSectionKey, direction: SectionMoveDirection): RoomListSectionsSettings {
    val visibleSectionsInOrder = reorderableSectionsInOrder.filter { it != ReorderableRoomListSectionKey.People || showPeopleSection }
    val visibleIndex = visibleSectionsInOrder.indexOf(sectionKey)
    if (visibleIndex < 0) return this
    val neighbourVisibleIndex = when (direction) {
        SectionMoveDirection.TowardsTop -> visibleIndex - 1
        SectionMoveDirection.TowardsBottom -> visibleIndex + 1
    }
    val neighbour = visibleSectionsInOrder.getOrNull(neighbourVisibleIndex) ?: return this
    val reordered = reorderableSectionsInOrder.toMutableList()
    val indexOfSection = reordered.indexOf(sectionKey)
    val indexOfNeighbour = reordered.indexOf(neighbour)
    reordered[indexOfSection] = neighbour
    reordered[indexOfNeighbour] = sectionKey
    return copy(reorderableSectionsInOrder = reordered)
}

fun RoomListSectionsSettings.canMove(sectionKey: ReorderableRoomListSectionKey, direction: SectionMoveDirection): Boolean {
    return withSectionMoved(sectionKey, direction) != this
}
