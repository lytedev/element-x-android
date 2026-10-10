/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.sections

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.tags.RoomTagName
import kotlinx.collections.immutable.persistentListOf

open class RoomListSectionsDialogPreviewParam : PreviewParameterProvider<RoomListSectionsDialog> {
    override val values: Sequence<RoomListSectionsDialog>
        get() = sequenceOf(
            RoomListSectionsDialog.SectionOptions(section = aWorkCustomSection(), canMoveTowardsTop = true, canMoveTowardsBottom = true),
            RoomListSectionsDialog.SectionOptions(section = RoomListSection.People, canMoveTowardsTop = false, canMoveTowardsBottom = true),
            RoomListSectionsDialog.SectionOptions(
                section = RoomListSection.Rooms(alsoHoldsDirectMessages = true),
                canMoveTowardsTop = true,
                canMoveTowardsBottom = false,
            ),
            RoomListSectionsDialog.CreateSection(roomToMoveIntoTheNewSection = null),
            RoomListSectionsDialog.RenameSection(aWorkCustomSection()),
            RoomListSectionsDialog.ConfirmSectionDeletion(aWorkCustomSection()),
            RoomListSectionsDialog.MoveRoomToSection(
                roomId = RoomId("!room:domain"),
                roomName = "Design team",
                sectionOfTheRoom = aWorkCustomSection(),
                customSections = persistentListOf(aWorkCustomSection(), aFamilyCustomSection()),
            ),
        )
}

internal fun aWorkCustomSection() = RoomListSection.Custom(tag = RoomTagName("element.io.section.work"), name = "Work")

internal fun aFamilyCustomSection() = RoomListSection.Custom(tag = RoomTagName("element.io.section.family"), name = "Family")
