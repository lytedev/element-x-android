/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.sections.custom

import com.google.common.truth.Truth.assertWithMessage
import io.element.android.libraries.matrix.api.room.tags.RoomTagName
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class ElementWebRoomListSettingsCodecTest {
    private val workTag = RoomTagName("element.io.section.1b4e28ba-2fa1-11d2-883f-0016d3cca427")

    private val contentWrittenByElementWeb = """
        {
          "RoomList.CustomSectionData": {
            "${workTag.value}": {"tag": "${workTag.value}", "name": "Work", "spaceId": "!space:domain"},
            "element.io.section.noSpace": {"tag": "element.io.section.noSpace", "name": "Anywhere"},
            "element.io.section.keyMismatch": {"tag": "element.io.section.other", "name": "Broken"},
            "u.notASection": {"tag": "u.notASection", "name": "Wrong prefix"},
            "element.io.section.noName": {"tag": "element.io.section.noName"}
          },
          "RoomList.OrderedCustomSections": ["${workTag.value}", "chats", "element.io.section.unknown", "element.io.section.noSpace"],
          "RoomList.showPeopleSection": false,
          "SomeOtherWebSetting": {"keep": "me"}
        }
    """.trimIndent()

    @Test
    fun `sections written by Element Web are read with its validation rules`() {
        val settings = ElementWebRoomListSettingsCodec.decode(ElementWebRoomListSettingsCodec.parseContent(contentWrittenByElementWeb))

        assertWithMessage(
            "only entries keyed by their own tag, with the section prefix and a name, are sections; the others must be dropped like Element Web does",
        ).that(settings.customSectionsByTag.keys)
            .containsExactly(workTag, RoomTagName("element.io.section.noSpace"))
        assertWithMessage("a section without spaceId belongs to the Home space")
            .that(settings.customSectionsByTag.getValue(RoomTagName("element.io.section.noSpace")).roomIdOfTheSpaceItWasCreatedInOrHomeSpaceKey)
            .isEqualTo(ElementWebRoomListSettings.HOME_SPACE_KEY)
        assertWithMessage("unknown tags must be dropped from the order, and a missing People must be put first, like Element Web does")
            .that(settings.reorderableSectionsInOrder)
            .containsExactly(
                ReorderableRoomListSectionKey.People,
                ReorderableRoomListSectionKey.Custom(workTag),
                ReorderableRoomListSectionKey.Chats,
                ReorderableRoomListSectionKey.Custom(RoomTagName("element.io.section.noSpace")),
            )
            .inOrder()
        assertWithMessage("RoomList.showPeopleSection must be honoured").that(settings.showPeopleSection).isFalse()
    }

    @Test
    fun `missing settings give People then Chats, with the People section shown, like the defaults of Element Web`() {
        val settings = ElementWebRoomListSettingsCodec.decode(ElementWebRoomListSettingsCodec.parseContent(null))

        assertWithMessage("an account that never used sections must get the Element Web defaults")
            .that(settings)
            .isEqualTo(RoomListSectionsSettings.DEFAULT)
    }

    @Test
    fun `writing the sections keeps every other key of the event, as Element Web stores all its settings in it`() {
        val storedContent = ElementWebRoomListSettingsCodec.parseContent(contentWrittenByElementWeb)
        val editedSettings = ElementWebRoomListSettingsCodec.decode(storedContent).withRenamedCustomSection(workTag, "Job")

        val writtenContent = ElementWebRoomListSettingsCodec.withSettings(storedContent, editedSettings)

        assertWithMessage("a setting this app does not know about must be written back unchanged, or Element Web would lose it")
            .that(writtenContent["SomeOtherWebSetting"])
            .isEqualTo(storedContent["SomeOtherWebSetting"])
        assertWithMessage("reading back what was written must give the edited settings")
            .that(ElementWebRoomListSettingsCodec.decode(writtenContent))
            .isEqualTo(editedSettings)
        assertWithMessage("RoomList.showPeopleSection must still be a JSON boolean for Element Web")
            .that(writtenContent[ElementWebRoomListSettings.SHOW_PEOPLE_SECTION_KEY])
            .isEqualTo(JsonPrimitive(false))
    }
}
