/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.room.tags

import io.element.android.libraries.matrix.api.core.RoomId

/**
 * The raw name of a room tag, such as `m.favourite`, `u.work` or `element.io.section.<uuid>`.
 */
@JvmInline
value class RoomTagName(val value: String) {
    companion object {
        val FAVOURITE = RoomTagName("m.favourite")
        val LOW_PRIORITY = RoomTagName("m.lowpriority")
    }
}

/**
 * @property orderAmongRoomsWithThisTag the value rooms carrying the same tag are ordered by, lexicographically, if any.
 */
data class RoomTagInfo(
    val orderAmongRoomsWithThisTag: Double? = null,
)

data class RoomTagsChange(
    val roomId: RoomId,
    val tags: Map<RoomTagName, RoomTagInfo>,
)
