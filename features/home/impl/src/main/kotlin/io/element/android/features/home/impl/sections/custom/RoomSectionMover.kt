/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.sections.custom

import dev.zacsweers.metro.Inject
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.tags.RoomTagName

/**
 * Moves rooms between sections by editing their tags like Element Web does
 * (element-hq/element-web@171955a9e208e8aca41383a786c3f73341e8f76c, apps/web/src/utils/room/tagRoom.ts):
 * the tag of the section the room is in is removed and the tag of the target section is added,
 * and moving a room to the section it is already in takes it out of that section.
 */
@Inject
class RoomSectionMover(
    private val matrixClient: MatrixClient,
    private val roomSectionTagsIndex: RoomSectionTagsIndex,
) {
    suspend fun moveRoom(roomId: RoomId, targetSectionTagOrNullToLeaveTheSection: RoomTagName?, settings: RoomListSectionsSettings): Result<Unit> {
        val room = matrixClient.getRoom(roomId) ?: return Result.failure(IllegalStateException("Room $roomId is unknown"))
        return room.use {
            runCatchingExceptions {
                val tagsBeforeTheMove = room.tags().getOrThrow().keys
                val sectionTagOfTheRoom = sectionTagDecidingTheSectionOf(tagsBeforeTheMove, settings)
                val isMoveToTheSameSection = targetSectionTagOrNullToLeaveTheSection != null && targetSectionTagOrNullToLeaveTheSection == sectionTagOfTheRoom
                if (sectionTagOfTheRoom != null) {
                    room.removeTag(sectionTagOfTheRoom).getOrThrow()
                }
                if (targetSectionTagOrNullToLeaveTheSection != null && !isMoveToTheSameSection) {
                    room.setTag(targetSectionTagOrNullToLeaveTheSection).getOrThrow()
                }
                val tagsAfterTheMove = tagsBeforeTheMove - setOfNotNull(sectionTagOfTheRoom) +
                    setOfNotNull(targetSectionTagOrNullToLeaveTheSection.takeUnless { isMoveToTheSameSection })
                roomSectionTagsIndex.recordTags(roomId, tagsAfterTheMove)
            }
        }
    }

    /**
     * Element Web makes Favourite, Low priority and the custom sections exclusive, so marking a room as favourite or low priority
     * from this device also takes it out of its custom sections.
     */
    suspend fun removeFromCustomSections(roomId: RoomId): Result<Unit> {
        val room = matrixClient.getRoom(roomId) ?: return Result.failure(IllegalStateException("Room $roomId is unknown"))
        return room.use {
            runCatchingExceptions {
                val tagsBeforeTheChange = room.tags().getOrThrow().keys
                val customSectionTags = tagsBeforeTheChange.filter(ElementWebRoomListSettings::isCustomSectionTag)
                customSectionTags.forEach { customSectionTag -> room.removeTag(customSectionTag).getOrThrow() }
                roomSectionTagsIndex.recordTags(roomId, tagsBeforeTheChange - customSectionTags.toSet())
            }
        }
    }

    companion object {
        /**
         * The tag that places the room in its section, with the precedence the sections are shown with;
         * a tag of a deleted section still counts, as Element Web removes it first when moving the room.
         */
        fun sectionTagDecidingTheSectionOf(tagsOfTheRoom: Set<RoomTagName>, settings: RoomListSectionsSettings): RoomTagName? {
            if (RoomTagName.FAVOURITE in tagsOfTheRoom) return RoomTagName.FAVOURITE
            settings.customSectionsInOrder.firstOrNull { definition -> definition.tag in tagsOfTheRoom }?.let { return it.tag }
            if (RoomTagName.LOW_PRIORITY in tagsOfTheRoom) return RoomTagName.LOW_PRIORITY
            return tagsOfTheRoom.firstOrNull(ElementWebRoomListSettings::isCustomSectionTag)
        }
    }
}
