/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.room.tags

import io.element.android.libraries.matrix.api.room.tags.RoomTagInfo
import io.element.android.libraries.matrix.api.room.tags.RoomTagName
import org.matrix.rustcomponents.sdk.TagInfo as RustTagInfo

fun Map<String, RustTagInfo>.toRoomTags(): Map<RoomTagName, RoomTagInfo> {
    return entries.associate { (rawTagName, rustTagInfo) ->
        RoomTagName(rawTagName) to RoomTagInfo(orderAmongRoomsWithThisTag = rustTagInfo.order)
    }
}
