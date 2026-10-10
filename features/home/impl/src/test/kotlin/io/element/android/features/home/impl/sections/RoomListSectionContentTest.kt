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
import io.element.android.libraries.matrix.api.room.RoomNotificationMode
import org.junit.Test

class RoomListSectionContentTest {
    @Test
    fun `the collapsed header counts notifications of unmuted rooms, 1 per room with only mentions, and 1 per invite, like Element Web`() {
        val sectionContent = aRoomListSectionContent(
            isCollapsed = true,
            summaries = listOf(
                aRoomListRoomSummary(id = "!notifications:domain", numberOfUnreadNotifications = 4),
                aRoomListRoomSummary(id = "!mentionOnly:domain", numberOfUnreadMentions = 2),
                aRoomListRoomSummary(id = "!muted:domain", numberOfUnreadNotifications = 7, notificationMode = RoomNotificationMode.MUTE),
                aRoomListRoomSummary(id = "!unreadMessagesOnly:domain", numberOfUnreadMessages = 9),
                aRoomListRoomSummary(id = "!invite:domain", displayType = RoomSummaryDisplayType.INVITE),
            ),
        )

        val notificationCountShownWhenCollapsed = sectionContent.notificationCountShownWhenCollapsed

        assertWithMessage(
            "4 notifications + 1 for the room with only mentions + 0 for the muted room + 0 for plain unread messages + 1 for the invite " +
                "must give 6",
        ).that(notificationCountShownWhenCollapsed).isEqualTo(6L)
    }

    @Test
    fun `a muted room with mentions neither shows the mention indicator nor makes the section title stand out`() {
        val sectionContent = aRoomListSectionContent(
            isCollapsed = true,
            summaries = listOf(
                aRoomListRoomSummary(numberOfUnreadMentions = 1, notificationMode = RoomNotificationMode.MUTE),
            ),
        )

        assertWithMessage("the user muted the only room with mentions, so the collapsed header must not show a mention")
            .that(sectionContent.hasUnmutedMentionShownWhenCollapsed)
            .isFalse()
        assertWithMessage("the user muted the only room with mentions, so the section title must not stand out")
            .that(sectionContent.hasRoomNeedingAttention)
            .isFalse()
    }

    @Test
    fun `a collapsed section shows none of its rooms, while an expanded one shows all of them`() {
        val summaries = listOf(aRoomListRoomSummary(id = "!a:domain"), aRoomListRoomSummary(id = "!b:domain"))

        val collapsedVisibleSummaries = aRoomListSectionContent(isCollapsed = true, summaries = summaries).visibleSummaries
        val expandedVisibleSummaries = aRoomListSectionContent(isCollapsed = false, summaries = summaries).visibleSummaries

        assertWithMessage("a collapsed section must only show its header").that(collapsedVisibleSummaries).isEmpty()
        assertWithMessage("an expanded section must show every one of its rooms").that(expandedVisibleSummaries).isEqualTo(summaries)
    }
}
