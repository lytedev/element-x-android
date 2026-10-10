/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.sections

import androidx.compose.runtime.Immutable
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.model.RoomSummaryDisplayType
import io.element.android.libraries.matrix.api.room.RoomNotificationMode
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class RoomListSectionContent(
    val section: RoomListSection,
    val summaries: ImmutableList<RoomListRoomSummary>,
    val isCollapsed: Boolean,
) {
    val hasRoomNeedingAttention: Boolean = summaries.any { room -> room.isHighlighted || room.displayType == RoomSummaryDisplayType.INVITE }

    /**
     * Counted like the collapsed section headers of Element Web: each unmuted room with notifications adds its notification count,
     * or 1 when it only has mentions, and each pending invite adds 1.
     */
    val notificationCountShownWhenCollapsed: Long = summaries.sumOf { room ->
        val isMuted = room.userDefinedNotificationMode == RoomNotificationMode.MUTE
        when {
            room.displayType == RoomSummaryDisplayType.INVITE -> 1L
            isMuted -> 0L
            room.numberOfUnreadNotifications > 0 -> room.numberOfUnreadNotifications
            room.numberOfUnreadMentions > 0 -> 1L
            else -> 0L
        }
    }

    val hasUnmutedMentionShownWhenCollapsed: Boolean = summaries.any { room ->
        room.userDefinedNotificationMode != RoomNotificationMode.MUTE && room.numberOfUnreadMentions > 0
    }

    val hasOngoingCallShownWhenCollapsed: Boolean = summaries.any { room -> room.hasRoomCall }

    val visibleSummaries: List<RoomListRoomSummary> = if (isCollapsed) emptyList() else summaries
}
