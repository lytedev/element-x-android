/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.filters

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.element.android.features.home.impl.filters.selection.DefaultFilterSelectionStrategy
import io.element.android.features.home.impl.filters.selection.FilterSelectionState
import io.element.android.features.home.impl.filters.selection.FilterSelectionStrategy
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.tests.testutils.awaitLastSequentialItem
import io.element.android.tests.testutils.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomListFiltersPresenterTest {
    @Test
    fun `present - initial state`() = runTest {
        val presenter = createRoomListFiltersPresenter()
        presenter.test {
            awaitItem().let { state ->
                assertThat(state.hasAnyFilterSelected).isFalse()
                assertThat(state.filterSelectionStates).containsExactly(
                    filterSelectionState(RoomListFilter.Unread, false),
                    filterSelectionState(RoomListFilter.People, false),
                    filterSelectionState(RoomListFilter.Rooms, false),
                    filterSelectionState(RoomListFilter.Favourites, false),
                    filterSelectionState(RoomListFilter.Invites, false),
                )
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `present - toggle rooms filter`() = runTest {
        val presenter = createRoomListFiltersPresenter()
        presenter.test {
            awaitItem().eventSink.invoke(RoomListFiltersEvent.ToggleFilter(RoomListFilter.Rooms))
            awaitLastSequentialItem().let { state ->
                assertThat(state.hasAnyFilterSelected).isTrue()
                assertThat(state.filterSelectionStates).containsExactly(
                    filterSelectionState(RoomListFilter.Rooms, true),
                    filterSelectionState(RoomListFilter.Unread, false),
                    filterSelectionState(RoomListFilter.Favourites, false),
                ).inOrder()

                assertThat(state.selectedFilters()).containsExactly(
                    RoomListFilter.Rooms,
                )
                state.eventSink.invoke(RoomListFiltersEvent.ToggleFilter(RoomListFilter.Rooms))
            }
            advanceUntilIdle()
            awaitLastSequentialItem().let { state ->
                assertThat(state.hasAnyFilterSelected).isFalse()
                assertThat(state.filterSelectionStates).containsExactly(
                    filterSelectionState(RoomListFilter.Unread, false),
                    filterSelectionState(RoomListFilter.People, false),
                    filterSelectionState(RoomListFilter.Rooms, false),
                    filterSelectionState(RoomListFilter.Favourites, false),
                    filterSelectionState(RoomListFilter.Invites, false),
                ).inOrder()
                assertThat(state.selectedFilters()).isEmpty()
            }
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `present - toggling two incompatible filters in the same frame keeps the first one`() = runTest {
        val presenter = createRoomListFiltersPresenter()
        presenter.test {
            awaitItem().let { state ->
                state.eventSink.invoke(RoomListFiltersEvent.ToggleFilter(RoomListFilter.Favourites))
                state.eventSink.invoke(RoomListFiltersEvent.ToggleFilter(RoomListFilter.Invites))
            }
            advanceUntilIdle()
            awaitLastSequentialItem().let { state ->
                assertThat(state.selectedFilters()).containsExactly(RoomListFilter.Favourites)
                assertThat(state.filterSelectionStates).containsExactly(
                    filterSelectionState(RoomListFilter.Favourites, true),
                    filterSelectionState(RoomListFilter.Unread, false),
                    filterSelectionState(RoomListFilter.People, false),
                    filterSelectionState(RoomListFilter.Rooms, false),
                ).inOrder()
            }
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `present - toggling two incompatible categories in the same frame keeps the first one`() = runTest {
        val presenter = createRoomListFiltersPresenter()
        presenter.test {
            awaitItem().let { state ->
                state.eventSink.invoke(RoomListFiltersEvent.ToggleFilter(RoomListFilter.People))
                state.eventSink.invoke(RoomListFiltersEvent.ToggleFilter(RoomListFilter.Rooms))
            }
            advanceUntilIdle()
            awaitLastSequentialItem().let { state ->
                assertThat(state.selectedFilters()).containsExactly(RoomListFilter.People)
                assertThat(state.filterSelectionStates).containsExactly(
                    filterSelectionState(RoomListFilter.People, true),
                    filterSelectionState(RoomListFilter.Unread, false),
                    filterSelectionState(RoomListFilter.Favourites, false),
                ).inOrder()
            }
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `present - clear filters event`() = runTest {
        val presenter = createRoomListFiltersPresenter()
        presenter.test {
            awaitItem().eventSink.invoke(RoomListFiltersEvent.ToggleFilter(RoomListFilter.Rooms))
            awaitLastSequentialItem().let { state ->
                assertThat(state.hasAnyFilterSelected).isTrue()
                state.eventSink.invoke(RoomListFiltersEvent.ClearSelectedFilters)
            }
            advanceUntilIdle()
            awaitLastSequentialItem().let { state ->
                assertThat(state.hasAnyFilterSelected).isFalse()
            }
        }
    }
}

class RoomListFiltersPresenterWithSectionsTest {
    @Test
    fun `while sections are enabled the favourites and invites filters are deselected and hidden, as their sections replace them`() = runTest {
        val filterSelectionStrategy = DefaultFilterSelectionStrategy().apply {
            select(RoomListFilter.Favourites)
        }
        val presenter = createRoomListFiltersPresenter(
            featureFlagService = FakeFeatureFlagService(initialState = mapOf(FeatureFlags.RoomListSections.key to true)),
            filterSelectionStrategy = filterSelectionStrategy,
        )
        presenter.test {
            val stateOnceSectionsAreEnabled = awaitLastSequentialItem()
            assertWithMessage(
                "Favourites was selected before sections were enabled, but the Favourites section now shows those rooms, " +
                    "so the filter must be deselected rather than hide every other section",
            ).that(stateOnceSectionsAreEnabled.selectedFilters()).isEmpty()
            assertWithMessage(
                "only the filters that no section replaces may be offered while sections are enabled",
            ).that(stateOnceSectionsAreEnabled.filterSelectionStates.map { it.filter }).containsExactly(
                RoomListFilter.Unread,
                RoomListFilter.People,
                RoomListFilter.Rooms,
            )
        }
    }
}

private fun filterSelectionState(filter: RoomListFilter, selected: Boolean) = FilterSelectionState(
    filter = filter,
    isSelected = selected,
)

private fun TestScope.createRoomListFiltersPresenter(
    featureFlagService: FeatureFlagService = FakeFeatureFlagService(),
    filterSelectionStrategy: FilterSelectionStrategy = DefaultFilterSelectionStrategy(),
): RoomListFiltersPresenter {
    return RoomListFiltersPresenter(
        filterSelectionStrategy = filterSelectionStrategy,
        featureFlagService = featureFlagService,
    )
}
