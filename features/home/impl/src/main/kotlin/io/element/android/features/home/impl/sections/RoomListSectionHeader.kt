/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.components.MentionIndicatorAtom
import io.element.android.features.home.impl.components.OnGoingCallIcon
import io.element.android.libraries.designsystem.atomic.atoms.UnreadIndicatorAtom
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * Ref: the section headers of Element Web, as no mobile design exists yet.
 * The indicators summing up the rooms of the section are only shown while it is collapsed, as its rooms show them otherwise.
 */
@Composable
internal fun RoomListSectionHeader(
    sectionContent: RoomListSectionContent,
    onToggleCollapse: (RoomListSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sectionTitle = stringResource(sectionContent.section.titleResource)
    val toggleCollapsedLabel = if (sectionContent.isCollapsed) {
        stringResource(R.string.a11y_roomlist_section_expand, sectionTitle)
    } else {
        stringResource(R.string.a11y_roomlist_section_collapse, sectionTitle)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clickable(onClickLabel = toggleCollapsedLabel) { onToggleCollapse(sectionContent.section) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { heading() },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = if (sectionContent.isCollapsed) CompoundIcons.ChevronRight() else CompoundIcons.ChevronDown(),
            contentDescription = null,
            tint = ElementTheme.colors.iconSecondary,
        )
        Text(
            modifier = Modifier.weight(1f),
            text = sectionTitle,
            style = if (sectionContent.hasRoomNeedingAttention) {
                ElementTheme.typography.fontBodyMdMedium
            } else {
                ElementTheme.typography.fontBodyMdRegular
            },
            color = if (sectionContent.hasRoomNeedingAttention) ElementTheme.colors.textPrimary else ElementTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (sectionContent.isCollapsed) {
            if (sectionContent.hasOngoingCallShownWhenCollapsed) {
                OnGoingCallIcon(color = ElementTheme.colors.iconAccentPrimary, isAudio = false)
            }
            if (sectionContent.hasUnmutedMentionShownWhenCollapsed) {
                MentionIndicatorAtom()
            }
            if (sectionContent.notificationCountShownWhenCollapsed > 0) {
                UnreadIndicatorAtom(
                    count = sectionContent.notificationCountShownWhenCollapsed,
                    contentDescription = stringResource(CommonStrings.a11y_notifications_new_messages),
                )
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun RoomListSectionHeaderPreview(@PreviewParameter(RoomListSectionContentPreviewParam::class) sectionContent: RoomListSectionContent) =
    ElementPreview {
        Column {
            RoomListSectionHeader(
                sectionContent = sectionContent,
                onToggleCollapse = {},
            )
        }
    }
