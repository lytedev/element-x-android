/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.sections

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.model.RoomSummaryDisplayType
import io.element.android.features.home.impl.model.aRoomListRoomSummary
import kotlinx.collections.immutable.toImmutableList

open class RoomListSectionContentPreviewParam : PreviewParameterProvider<RoomListSectionContent> {
    override val values: Sequence<RoomListSectionContent>
        get() = sequenceOf(
            aRoomListSectionContent(section = RoomListSection.Favourites, isCollapsed = false),
            aRoomListSectionContent(section = RoomListSection.Favourites, isCollapsed = true),
            aRoomListSectionContent(
                section = RoomListSection.People,
                isCollapsed = true,
                summaries = listOf(
                    aRoomListRoomSummary(id = "!dm1:domain", isDm = true, numberOfUnreadNotifications = 3),
                    aRoomListRoomSummary(id = "!dm2:domain", isDm = true, numberOfUnreadMentions = 1, numberOfUnreadNotifications = 1),
                ),
            ),
            aRoomListSectionContent(
                section = RoomListSection.Rooms(),
                isCollapsed = true,
                summaries = listOf(
                    aRoomListRoomSummary(id = "!call:domain", hasRoomCall = true),
                ),
            ),
            aRoomListSectionContent(
                section = RoomListSection.Invites,
                isCollapsed = true,
                summaries = listOf(
                    aRoomListRoomSummary(id = "!invite1:domain", displayType = RoomSummaryDisplayType.INVITE),
                    aRoomListRoomSummary(id = "!invite2:domain", displayType = RoomSummaryDisplayType.INVITE),
                ),
            ),
            aRoomListSectionContent(section = RoomListSection.LowPriority, isCollapsed = false),
        )
}

internal fun aRoomListSectionContent(
    section: RoomListSection = RoomListSection.Rooms(),
    isCollapsed: Boolean = false,
    summaries: List<RoomListRoomSummary> = listOf(aRoomListRoomSummary()),
) = RoomListSectionContent(
    section = section,
    summaries = summaries.toImmutableList(),
    isCollapsed = isCollapsed,
)
