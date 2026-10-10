/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.sections.custom

import com.google.common.truth.Truth.assertWithMessage
import io.element.android.libraries.matrix.api.room.tags.RoomTagInfo
import io.element.android.libraries.matrix.api.room.tags.RoomTagName
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomSectionMoverTest {
    private val workTag = RoomTagName("element.io.section.work")
    private val familyTag = RoomTagName("element.io.section.family")
    private val settings = RoomListSectionsSettings.DEFAULT
        .withNewCustomSection(CustomRoomListSectionDefinition(workTag, "Work", ElementWebRoomListSettings.HOME_SPACE_KEY))
        .withNewCustomSection(CustomRoomListSectionDefinition(familyTag, "Family", ElementWebRoomListSettings.HOME_SPACE_KEY))

    /**
     * A room whose tags change as the mover adds and removes them, recording each change in order.
     */
    private class RoomWithTags(initialTags: Set<RoomTagName>) {
        val tags = initialTags.toMutableSet()
        val tagChanges = mutableListOf<String>()
        val room = FakeBaseRoom(
            roomId = A_ROOM_ID,
            tagsResult = { Result.success(tags.associateWith { RoomTagInfo() }) },
            setTagResult = { tag, _ ->
                tags += tag
                tagChanges += "add ${tag.value}"
                Result.success(Unit)
            },
            removeTagResult = { tag ->
                tags -= tag
                tagChanges += "remove ${tag.value}"
                Result.success(Unit)
            },
        )
    }

    private fun TestScope.createMover(roomWithTags: RoomWithTags): Pair<RoomSectionMover, RoomSectionTagsIndex> {
        val matrixClient = FakeMatrixClient().apply { givenGetRoomResult(A_ROOM_ID, roomWithTags.room) }
        val index = RoomSectionTagsIndex(matrixClient, backgroundScope)
        return RoomSectionMover(matrixClient, index) to index
    }

    @Test
    fun `moving a room to another section swaps the tag of its current section for the tag of the target section`() = runTest {
        val roomWithTags = RoomWithTags(initialTags = setOf(workTag, RoomTagName("u.unrelated")))
        val (mover, index) = createMover(roomWithTags)

        mover.moveRoom(A_ROOM_ID, familyTag, settings).getOrThrow()

        assertWithMessage("the Work tag must be removed and the Family tag added; unrelated tags must stay")
            .that(roomWithTags.tags)
            .containsExactly(familyTag, RoomTagName("u.unrelated"))
        assertWithMessage("the index must know the new tags right away, so the room list updates before the sync echoes the change")
            .that(index.tagsOf(A_ROOM_ID))
            .containsExactly(familyTag, RoomTagName("u.unrelated"))
    }

    @Test
    fun `moving a favourite into a custom section takes it out of Favourites, as Favourites would otherwise keep showing it`() = runTest {
        val roomWithTags = RoomWithTags(initialTags = setOf(RoomTagName.FAVOURITE))
        val (mover, _) = createMover(roomWithTags)

        mover.moveRoom(A_ROOM_ID, workTag, settings).getOrThrow()

        assertWithMessage("the favourite tag must be replaced by the Work tag")
            .that(roomWithTags.tags)
            .containsExactly(workTag)
    }

    @Test
    fun `moving a room to the section it is already in takes it out of that section, like Element Web does`() = runTest {
        val roomWithTags = RoomWithTags(initialTags = setOf(workTag))
        val (mover, index) = createMover(roomWithTags)

        mover.moveRoom(A_ROOM_ID, workTag, settings).getOrThrow()

        assertWithMessage("the Work tag must be removed and not added back")
            .that(roomWithTags.tagChanges)
            .containsExactly("remove ${workTag.value}")
        assertWithMessage("a room without any custom section tag must leave the index")
            .that(index.tagsOf(A_ROOM_ID))
            .isNull()
    }

    @Test
    fun `marking a room as favourite from this device removes its custom section tags`() = runTest {
        val roomWithTags = RoomWithTags(initialTags = setOf(workTag, familyTag, RoomTagName.FAVOURITE))
        val (mover, _) = createMover(roomWithTags)

        mover.removeFromCustomSections(A_ROOM_ID).getOrThrow()

        assertWithMessage("every custom section tag must be gone, and the favourite tag kept")
            .that(roomWithTags.tags)
            .containsExactly(RoomTagName.FAVOURITE)
    }
}
