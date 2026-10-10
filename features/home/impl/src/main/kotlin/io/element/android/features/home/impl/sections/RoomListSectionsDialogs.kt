/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.sections.custom.ElementWebRoomListSettings
import io.element.android.features.home.impl.sections.custom.SectionMoveDirection
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.components.dialogs.TextFieldDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
internal fun RoomListSectionsDialogs(
    state: RoomListSectionsState,
    roomIdOfTheCurrentSpaceOrHomeSpaceKey: String,
) {
    val dismiss = { state.eventSink(RoomListSectionsEvent.DismissDialog) }
    when (val dialog = state.dialog) {
        RoomListSectionsDialog.None -> Unit
        is RoomListSectionsDialog.SectionOptions -> SectionOptionsSheet(dialog = dialog, eventSink = state.eventSink, onDismiss = dismiss)
        is RoomListSectionsDialog.CreateSection -> TextFieldDialog(
            title = stringResource(R.string.screen_roomlist_section_new),
            value = null,
            placeholder = stringResource(R.string.screen_roomlist_section_name_placeholder),
            validation = { name -> !name.isNullOrBlank() },
            submitText = stringResource(CommonStrings.action_create),
            onSubmit = { name ->
                state.eventSink(
                RoomListSectionsEvent.CreateSection(name, dialog.roomToMoveIntoTheNewSection, roomIdOfTheCurrentSpaceOrHomeSpaceKey)
            )
            },
            onDismissRequest = dismiss,
        )
        is RoomListSectionsDialog.RenameSection -> TextFieldDialog(
            title = stringResource(R.string.screen_roomlist_section_rename),
            value = dialog.section.name,
            placeholder = stringResource(R.string.screen_roomlist_section_name_placeholder),
            validation = { name -> !name.isNullOrBlank() },
            submitText = stringResource(CommonStrings.action_save),
            onSubmit = { name -> state.eventSink(RoomListSectionsEvent.RenameSection(dialog.section, name)) },
            onDismissRequest = dismiss,
        )
        is RoomListSectionsDialog.ConfirmSectionDeletion -> ConfirmationDialog(
            title = stringResource(R.string.screen_roomlist_section_delete),
            content = stringResource(R.string.screen_roomlist_section_delete_confirmation, dialog.section.name),
            submitText = stringResource(CommonStrings.action_delete),
            destructiveSubmit = true,
            onSubmitClick = { state.eventSink(RoomListSectionsEvent.DeleteSection(dialog.section)) },
            onDismiss = dismiss,
        )
        is RoomListSectionsDialog.MoveRoomToSection -> MoveRoomToSectionSheet(dialog = dialog, eventSink = state.eventSink, onDismiss = dismiss)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionOptionsSheet(
    dialog: RoomListSectionsDialog.SectionOptions,
    eventSink: (RoomListSectionsEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, scrollable = false) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            ListItem(
                content = { Text(roomListSectionTitle(dialog.section), style = ElementTheme.typography.fontBodyLgMedium) },
            )
            val section = dialog.section
            if (section is RoomListSection.Custom) {
                SheetAction(R.string.screen_roomlist_section_rename, CompoundIcons.Edit()) {
                    eventSink(RoomListSectionsEvent.StartRenamingSection(section))
                }
            }
            if (dialog.canMoveTowardsTop) {
                SheetAction(R.string.screen_roomlist_section_move_up, CompoundIcons.ArrowUp()) {
                    eventSink(RoomListSectionsEvent.MoveSection(section, SectionMoveDirection.TowardsTop))
                }
            }
            if (dialog.canMoveTowardsBottom) {
                SheetAction(R.string.screen_roomlist_section_move_down, CompoundIcons.ArrowDown()) {
                    eventSink(RoomListSectionsEvent.MoveSection(section, SectionMoveDirection.TowardsBottom))
                }
            }
            if (section == RoomListSection.People) {
                SheetAction(R.string.screen_roomlist_section_hide_people, CompoundIcons.VisibilityOff()) {
                    eventSink(RoomListSectionsEvent.SetShowPeopleSection(false))
                }
            }
            if (section is RoomListSection.Rooms && section.alsoHoldsDirectMessages) {
                SheetAction(R.string.screen_roomlist_section_show_people, CompoundIcons.VisibilityOn()) {
                    eventSink(RoomListSectionsEvent.SetShowPeopleSection(true))
                }
            }
            SheetAction(R.string.screen_roomlist_section_new, CompoundIcons.Plus()) {
                eventSink(RoomListSectionsEvent.StartCreatingSection(roomToMoveIntoTheNewSection = null))
            }
            if (section is RoomListSection.Custom) {
                SheetAction(R.string.screen_roomlist_section_delete, CompoundIcons.Delete(), style = ListItemStyle.Destructive) {
                    eventSink(RoomListSectionsEvent.StartDeletingSection(section))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveRoomToSectionSheet(
    dialog: RoomListSectionsDialog.MoveRoomToSection,
    eventSink: (RoomListSectionsEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, scrollable = false) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            ListItem(
                content = {
                    Text(
                        text = stringResource(
                            R.string.screen_roomlist_move_to_section_title,
                            dialog.roomName ?: stringResource(CommonStrings.common_no_room_name),
                        ),
                        style = ElementTheme.typography.fontBodyLgMedium,
                    )
                },
            )
            dialog.customSections.forEach { customSection ->
                val isTheSectionOfTheRoom = customSection == dialog.sectionOfTheRoom
                ListItem(
                    content = { Text(customSection.name) },
                    leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Folder())),
                    trailingContent = if (isTheSectionOfTheRoom) ListItemContent.Icon(IconSource.Vector(CompoundIcons.Check())) else null,
                    onClick = { eventSink(RoomListSectionsEvent.MoveRoomToSection(dialog.roomId, customSection)) },
                )
            }
            if (dialog.sectionOfTheRoom != null) {
                SheetAction(R.string.screen_roomlist_remove_from_section, CompoundIcons.Close()) {
                    eventSink(RoomListSectionsEvent.MoveRoomToSection(dialog.roomId, targetSectionOrNullToLeaveTheSection = null))
                }
            }
            SheetAction(R.string.screen_roomlist_section_new, CompoundIcons.Plus()) {
                eventSink(RoomListSectionsEvent.StartCreatingSection(roomToMoveIntoTheNewSection = dialog.roomId))
            }
        }
    }
}

@Composable
private fun SheetAction(
    textResource: Int,
    icon: ImageVector,
    style: ListItemStyle = ListItemStyle.Default,
    onClick: () -> Unit,
) {
    ListItem(
        content = { Text(stringResource(textResource)) },
        leadingContent = ListItemContent.Icon(IconSource.Vector(icon)),
        style = style,
        onClick = onClick,
    )
}

@PreviewsDayNight
@Composable
internal fun RoomListSectionsDialogsPreview(@PreviewParameter(RoomListSectionsDialogPreviewParam::class) dialog: RoomListSectionsDialog) =
    ElementPreview {
        RoomListSectionsDialogs(
            state = aDisabledRoomListSectionsState().copy(isEnabled = true, dialog = dialog),
            roomIdOfTheCurrentSpaceOrHomeSpaceKey = ElementWebRoomListSettings.HOME_SPACE_KEY,
        )
    }
