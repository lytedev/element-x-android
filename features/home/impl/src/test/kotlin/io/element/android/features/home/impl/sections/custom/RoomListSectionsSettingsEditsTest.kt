/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.sections.custom

import com.google.common.truth.Truth.assertWithMessage
import io.element.android.libraries.matrix.api.room.tags.RoomTagName
import org.junit.Test

class RoomListSectionsSettingsEditsTest {
    private fun aSection(name: String) = CustomRoomListSectionDefinition(
        tag = RoomTagName("element.io.section.$name"),
        name = name,
        roomIdOfTheSpaceItWasCreatedInOrHomeSpaceKey = ElementWebRoomListSettings.HOME_SPACE_KEY,
    )

    @Test
    fun `a new section is placed right above Chats, below the sections created before it, like on Element Web`() {
        val settings = RoomListSectionsSettings.DEFAULT
            .withNewCustomSection(aSection("first"))
            .withNewCustomSection(aSection("second"))

        assertWithMessage("new sections go just above Chats, in creation order")
            .that(settings.reorderableSectionsInOrder)
            .containsExactly(
                ReorderableRoomListSectionKey.People,
                ReorderableRoomListSectionKey.Custom(aSection("first").tag),
                ReorderableRoomListSectionKey.Custom(aSection("second").tag),
                ReorderableRoomListSectionKey.Chats,
            )
            .inOrder()
    }

    @Test
    fun `deleting a section removes it from both the section data and the order`() {
        val settings = RoomListSectionsSettings.DEFAULT
            .withNewCustomSection(aSection("work"))
            .withoutCustomSection(aSection("work").tag)

        assertWithMessage("after deleting the only custom section, the settings must be back to the defaults")
            .that(settings)
            .isEqualTo(RoomListSectionsSettings.DEFAULT)
    }

    @Test
    fun `moving a section swaps it with its visible neighbour, skipping a hidden People section`() {
        val settings = RoomListSectionsSettings.DEFAULT
            .withNewCustomSection(aSection("work"))
            .copy(showPeopleSection = false)

        val chatsMovedUp = settings.withSectionMoved(ReorderableRoomListSectionKey.Chats, SectionMoveDirection.TowardsTop)

        assertWithMessage("Chats must swap with Work, the section shown above it, not with the hidden People section")
            .that(chatsMovedUp.reorderableSectionsInOrder)
            .containsExactly(
                ReorderableRoomListSectionKey.People,
                ReorderableRoomListSectionKey.Chats,
                ReorderableRoomListSectionKey.Custom(aSection("work").tag),
            )
            .inOrder()
        assertWithMessage("with People hidden, Chats is now the first visible section, so it cannot move up any further")
            .that(chatsMovedUp.canMove(ReorderableRoomListSectionKey.Chats, SectionMoveDirection.TowardsTop))
            .isFalse()
    }
}
