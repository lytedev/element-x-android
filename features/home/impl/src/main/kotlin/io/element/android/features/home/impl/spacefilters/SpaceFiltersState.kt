/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.spacefilters

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import io.element.android.features.home.impl.sections.custom.ElementWebRoomListSettings
import io.element.android.libraries.matrix.api.roomlist.RoomListFilter
import io.element.android.libraries.matrix.api.spaces.SpaceServiceFilter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Immutable
sealed interface SpaceFiltersState {
    data object Disabled : SpaceFiltersState

    data class Unselected(
        val eventSink: (SpaceFiltersEvent.Unselected) -> Unit,
    ) : SpaceFiltersState

    data class Selecting(
        val availableFilters: ImmutableList<SpaceServiceFilter>,
        val searchQuery: TextFieldState,
        val eventSink: (SpaceFiltersEvent.Selecting) -> Unit,
    ) : SpaceFiltersState {
        val visibleFilters: ImmutableList<SpaceServiceFilter>
            get() {
                val query = searchQuery.text.toString()
                if (query.isBlank()) return availableFilters
                return availableFilters.filter { filter ->
                    filter.spaceRoom.displayName.contains(query, ignoreCase = true) ||
                        (filter.spaceRoom.canonicalAlias?.value ?: "").contains(query, ignoreCase = true)
                }.toImmutableList()
            }
    }

    data class Selected(
        val selectedFilter: SpaceServiceFilter,
        val eventSink: (SpaceFiltersEvent.Selected) -> Unit,
    ) : SpaceFiltersState
}

fun SpaceFiltersState.selectedFilter(): SpaceServiceFilter? {
    return when (this) {
        is SpaceFiltersState.Selected -> this.selectedFilter
        else -> null
    }
}

fun SpaceServiceFilter?.into(): RoomListFilter? {
    return this?.let { RoomListFilter.Identifiers(descendants) }
}

/**
 * The key Element Web uses for the space a custom room list section was created in.
 */
fun SpaceFiltersState.roomIdOfTheSelectedSpaceOrHomeSpaceKey(): String {
    return selectedFilter()?.spaceRoom?.roomId?.value ?: ElementWebRoomListSettings.HOME_SPACE_KEY
}
