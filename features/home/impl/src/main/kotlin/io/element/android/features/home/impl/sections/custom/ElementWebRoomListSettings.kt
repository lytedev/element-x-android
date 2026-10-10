/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.sections.custom

import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.matrix.api.room.tags.RoomTagName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.util.UUID

/**
 * The custom room list sections of Element Web, which it stores as top-level keys of its `im.vector.web.settings` global account data,
 * as read at element-hq/element-web@171955a9e208e8aca41383a786c3f73341e8f76c, apps/web/src/stores/room-list-v3/section.ts.
 */
object ElementWebRoomListSettings {
    const val ACCOUNT_DATA_EVENT_TYPE = "im.vector.web.settings"
    const val CUSTOM_SECTION_DATA_KEY = "RoomList.CustomSectionData"
    const val ORDERED_CUSTOM_SECTIONS_KEY = "RoomList.OrderedCustomSections"
    const val SHOW_PEOPLE_SECTION_KEY = "RoomList.showPeopleSection"
    const val CUSTOM_SECTION_TAG_PREFIX = "element.io.section."
    const val PEOPLE_SECTION_ORDER_KEY = "im.vector.fake.direct"
    const val CHATS_SECTION_ORDER_KEY = "chats"
    const val HOME_SPACE_KEY = "home-space"

    fun newCustomSectionTag(): RoomTagName = RoomTagName(CUSTOM_SECTION_TAG_PREFIX + UUID.randomUUID().toString())

    fun isCustomSectionTag(tag: RoomTagName): Boolean = tag.value.startsWith(CUSTOM_SECTION_TAG_PREFIX)
}

/**
 * A custom section, which while empty is only shown in the space it was created in.
 */
data class CustomRoomListSectionDefinition(
    val tag: RoomTagName,
    val name: String,
    val roomIdOfTheSpaceItWasCreatedInOrHomeSpaceKey: String,
)

/**
 * One of the sections whose order the user chooses: People, Chats, and the custom sections.
 * Invites and Favourites always come first and Low priority always comes last, so they are not part of it.
 */
sealed interface ReorderableRoomListSectionKey {
    val orderKey: String

    data object People : ReorderableRoomListSectionKey {
        override val orderKey = ElementWebRoomListSettings.PEOPLE_SECTION_ORDER_KEY
    }

    data object Chats : ReorderableRoomListSectionKey {
        override val orderKey = ElementWebRoomListSettings.CHATS_SECTION_ORDER_KEY
    }

    data class Custom(val tag: RoomTagName) : ReorderableRoomListSectionKey {
        override val orderKey = tag.value
    }
}

data class RoomListSectionsSettings(
    val customSectionsByTag: Map<RoomTagName, CustomRoomListSectionDefinition>,
    val reorderableSectionsInOrder: List<ReorderableRoomListSectionKey>,
    val showPeopleSection: Boolean,
) {
    val customSectionsInOrder: List<CustomRoomListSectionDefinition> = reorderableSectionsInOrder
        .filterIsInstance<ReorderableRoomListSectionKey.Custom>()
        .mapNotNull { customSectionKey -> customSectionsByTag[customSectionKey.tag] }

    companion object {
        val DEFAULT = RoomListSectionsSettings(
            customSectionsByTag = emptyMap(),
            reorderableSectionsInOrder = listOf(ReorderableRoomListSectionKey.People, ReorderableRoomListSectionKey.Chats),
            showPeopleSection = true,
        )
    }
}

/**
 * Reads and edits the room list section keys of the `im.vector.web.settings` content like Element Web does, leaving every other key untouched.
 */
object ElementWebRoomListSettingsCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseContent(rawContent: String?): JsonObject {
        if (rawContent.isNullOrBlank()) return JsonObject(emptyMap())
        return tryOrNull { json.parseToJsonElement(rawContent).jsonObject } ?: JsonObject(emptyMap())
    }

    fun encodeContent(content: JsonObject): String = json.encodeToString(JsonObject.serializer(), content)

    fun decode(content: JsonObject): RoomListSectionsSettings {
        val customSectionsByTag = decodeCustomSections(content[ElementWebRoomListSettings.CUSTOM_SECTION_DATA_KEY])
        return RoomListSectionsSettings(
            customSectionsByTag = customSectionsByTag,
            reorderableSectionsInOrder = decodeOrder(content[ElementWebRoomListSettings.ORDERED_CUSTOM_SECTIONS_KEY], customSectionsByTag),
            showPeopleSection = (content[ElementWebRoomListSettings.SHOW_PEOPLE_SECTION_KEY] as? JsonPrimitive)?.booleanOrNull ?: true,
        )
    }

    private fun decodeCustomSections(customSectionData: kotlinx.serialization.json.JsonElement?): Map<RoomTagName, CustomRoomListSectionDefinition> {
        val customSectionDataObject = customSectionData as? JsonObject ?: return emptyMap()
        return customSectionDataObject.entries.mapNotNull { (key, value) ->
            val sectionObject = value as? JsonObject ?: return@mapNotNull null
            val tag = (sectionObject["tag"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
            val name = (sectionObject["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
            val isValidEntry = tag.startsWith(ElementWebRoomListSettings.CUSTOM_SECTION_TAG_PREFIX) && key == tag
            if (!isValidEntry) return@mapNotNull null
            val roomIdOfTheSpaceItWasCreatedInOrHomeSpaceKey =
                (sectionObject["spaceId"] as? JsonPrimitive)?.contentOrNull ?: ElementWebRoomListSettings.HOME_SPACE_KEY
            RoomTagName(tag) to
                CustomRoomListSectionDefinition(
                    tag = RoomTagName(tag),
                    name = name,
                    roomIdOfTheSpaceItWasCreatedInOrHomeSpaceKey = roomIdOfTheSpaceItWasCreatedInOrHomeSpaceKey
                )
        }.toMap()
    }

    private fun decodeOrder(
        orderedCustomSections: kotlinx.serialization.json.JsonElement?,
        customSectionsByTag: Map<RoomTagName, CustomRoomListSectionDefinition>,
    ): List<ReorderableRoomListSectionKey> {
        val orderKeys = (orderedCustomSections as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { it.isString }?.content }
        val knownSectionsInOrder = orderKeys.mapNotNull { orderKey ->
            when (orderKey) {
                ElementWebRoomListSettings.PEOPLE_SECTION_ORDER_KEY -> ReorderableRoomListSectionKey.People
                ElementWebRoomListSettings.CHATS_SECTION_ORDER_KEY -> ReorderableRoomListSectionKey.Chats
                else -> RoomTagName(orderKey).takeIf { it in customSectionsByTag }?.let { ReorderableRoomListSectionKey.Custom(it) }
            }
        }.distinct()
        val withChats = if (ReorderableRoomListSectionKey.Chats in knownSectionsInOrder) {
            knownSectionsInOrder
        } else {
            knownSectionsInOrder + ReorderableRoomListSectionKey.Chats
        }
        return if (ReorderableRoomListSectionKey.People in withChats) withChats else listOf(ReorderableRoomListSectionKey.People) + withChats
    }

    fun withSettings(content: JsonObject, settings: RoomListSectionsSettings): JsonObject {
        val customSectionData = JsonObject(
            settings.customSectionsByTag.values.associate { definition ->
                definition.tag.value to JsonObject(
                    mapOf(
                        "tag" to JsonPrimitive(definition.tag.value),
                        "name" to JsonPrimitive(definition.name),
                        "spaceId" to JsonPrimitive(definition.roomIdOfTheSpaceItWasCreatedInOrHomeSpaceKey),
                    )
                )
            }
        )
        val orderedCustomSections = JsonArray(settings.reorderableSectionsInOrder.map { JsonPrimitive(it.orderKey) })
        return JsonObject(
            content + mapOf(
                ElementWebRoomListSettings.CUSTOM_SECTION_DATA_KEY to customSectionData,
                ElementWebRoomListSettings.ORDERED_CUSTOM_SECTIONS_KEY to orderedCustomSections,
                ElementWebRoomListSettings.SHOW_PEOPLE_SECTION_KEY to JsonPrimitive(settings.showPeopleSection),
            )
        )
    }
}
