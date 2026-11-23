package dev.opencab.feature.throttle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opencab.core.designsystem.component.CabConnectionStatus
import dev.opencab.core.designsystem.component.ConfirmSheet
import dev.opencab.core.designsystem.component.EmptyState
import dev.opencab.core.designsystem.component.InlineError
import dev.opencab.core.designsystem.component.PrimaryActionButton
import dev.opencab.core.designsystem.component.StatusPill
import dev.opencab.core.designsystem.theme.CabTheme

const val DRIVE_ROUTE: String = "drive"

/**
 * The drive entry point.
 *
 * The view model owns the engine connection; this wrapper only maps one-shot navigation
 * callbacks into the screen actions so the composables stay independent of navigation.
 */
@Composable
fun DriveRoute(
    onOpenConnection: () -> Unit,
    onChooseLocomotive: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DriveViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val baseActions = viewModel.actions
    val chooseLocomotive = rememberUpdatedState(onChooseLocomotive)
    val openConnection = rememberUpdatedState(onOpenConnection)
    val actions =
        remember(baseActions) {
            object : DriveActions by baseActions {
                override fun onChooseLocomotive() {
                    chooseLocomotive.value()
                }

                override fun onOpenConnection() {
                    openConnection.value()
                }
            }
        }
    DriveScreen(
        uiState = uiState,
        actions = actions,
        modifier = modifier,
    )
}

/** The hero throttle surface. Pure over [DriveUiState]; every command is a [DriveActions] call. */
@Composable
fun DriveScreen(
    uiState: DriveUiState,
    modifier: Modifier = Modifier,
    actions: DriveActions = DriveActions.None,
    initialTab: DriveInstrumentTab = DriveInstrumentTab.Throttle,
) {
    var releaseRequested by remember { mutableStateOf(false) }
    var reverseTarget by remember { mutableStateOf<DriveDirection?>(null) }
    val loco = uiState.loco
    val performHaptic = rememberDriveHaptics(uiState.haptics)
    LaunchedEffect(loco?.address) {
        if (loco == null) {
            releaseRequested = false
            reverseTarget = null
        }
    }

    BoxWithConstraints(
        modifier =
        modifier
            .fillMaxSize()
            .background(CabTheme.colorScheme.background)
            .testTag(DriveTestTags.SCREEN),
    ) {
        val wide = maxWidth >= 600.dp || maxWidth > maxHeight
        if (wide) {
            DriveWideLayout(
                uiState = uiState,
                actions = actions,
                onReleaseRequest = { releaseRequested = true },
                onReverseRequest = { reverseTarget = it },
            )
        } else {
            DrivePortraitLayout(
                uiState = uiState,
                actions = actions,
                onReleaseRequest = { releaseRequested = true },
                onReverseRequest = { reverseTarget = it },
                initialTab = initialTab,
            )
        }
    }

    if (releaseRequested && loco != null) {
        ConfirmSheet(
            visible = true,
            title = stringResource(R.string.drive_release_title, loco.address),
            message = stringResource(
                if (loco.isMoving) {
                    R.string.drive_release_message_moving
                } else {
                    R.string.drive_release_message_stopped
                },
            ),
            confirmLabel = stringResource(R.string.drive_release_confirm),
            onConfirm = {
                releaseRequested = false
                performHaptic(DriveHapticToken.Confirm)
                actions.onRelease()
            },
            onDismissRequest = { releaseRequested = false },
            dismissLabel = stringResource(R.string.drive_cancel),
            destructive = true,
            modifier = Modifier.testTag(DriveTestTags.RELEASE_CONFIRM),
        )
    }

    val target = reverseTarget
    if (target != null && loco != null) {
        ConfirmSheet(
            visible = true,
            title = stringResource(R.string.drive_reverse_prompt_title),
            message = stringResource(R.string.drive_reverse_prompt_message),
            confirmLabel = stringResource(R.string.drive_reverse_confirm),
            onConfirm = {
                reverseTarget = null
                performHaptic(DriveHapticToken.Confirm)
                actions.onStop()
                actions.onDirectionChange(target)
            },
            onDismissRequest = { reverseTarget = null },
            dismissLabel = stringResource(R.string.drive_cancel),
        )
    }
}

@Composable
private fun DrivePortraitLayout(
    uiState: DriveUiState,
    actions: DriveActions,
    onReleaseRequest: () -> Unit,
    onReverseRequest: (DriveDirection) -> Unit,
    initialTab: DriveInstrumentTab,
) {
    val loco = uiState.loco
    Column(modifier = Modifier.fillMaxSize()) {
        DriveHeaderFor(uiState, actions, onReleaseRequest)
        DriveBannersFor(uiState)
        if (loco == null) {
            DriveEmptyContent(
                uiState = uiState,
                actions = actions,
                modifier = Modifier.weight(1f),
            )
            return@Column
        }
        if (loco.issue != null) {
            DriveIssueCard(
                address = loco.address,
                issue = loco.issue,
                actions = actions,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CabTheme.spacing.page, vertical = CabTheme.spacing.xs),
            )
        }
        DrivePortraitInstrument(
            loco = loco,
            enabled = uiState.controlsEnabled,
            scale = uiState.scale,
            haptics = uiState.haptics,
            actions = actions,
            onReverseRequest = onReverseRequest,
            initialTab = initialTab,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun DriveWideLayout(
    uiState: DriveUiState,
    actions: DriveActions,
    onReleaseRequest: () -> Unit,
    onReverseRequest: (DriveDirection) -> Unit,
) {
    val loco = uiState.loco
    val landscape =
        LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    Column(modifier = Modifier.fillMaxSize()) {
        DriveHeaderFor(uiState, actions, onReleaseRequest)
        DriveBannersFor(uiState)
        if (loco == null) {
            DriveEmptyContent(
                uiState = uiState,
                actions = actions,
                modifier = Modifier.weight(1f),
            )
            return@Column
        }
        Row(
            modifier =
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = CabTheme.spacing.page, vertical = CabTheme.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(CabTheme.spacing.md),
        ) {
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier =
                    Modifier
                        .widthIn(max = 1000.dp)
                        .then(if (landscape) Modifier.heightIn(max = 820.dp) else Modifier)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(CabTheme.spacing.sm),
                ) {
                    if (loco.issue != null) {
                        DriveIssueCard(address = loco.address, issue = loco.issue, actions = actions)
                    }
                    DriveLeverPanel(
                        loco = loco,
                        enabled = uiState.controlsEnabled,
                        scale = uiState.scale,
                        haptics = uiState.haptics,
                        actions = actions,
                        onReverseRequest = onReverseRequest,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            DriveFunctionsPanel(
                functions = loco.functions,
                enabled = uiState.controlsEnabled,
                actions = actions,
                modifier =
                Modifier
                    .widthIn(min = 240.dp, max = 360.dp)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun DriveHeaderFor(
    uiState: DriveUiState,
    actions: DriveActions,
    onReleaseRequest: () -> Unit,
) {
    DriveHeader(
        locoName = uiState.loco?.name,
        addressLine = headerAddressLine(uiState),
        status = uiState.link.toCabStatus(),
        attempt = if (uiState.link == DriveLink.Reconnecting) uiState.reconnectAttempt else null,
        hasLoco = uiState.loco != null,
        actions = actions,
        onReleaseRequest = onReleaseRequest,
    )
}

@Composable
private fun DriveBannersFor(uiState: DriveUiState) {
    DriveBanners(
        health = uiState.loco?.health,
        link = uiState.link,
        reconnectAttempt = uiState.reconnectAttempt,
        trackPowerOn = uiState.trackPowerOn,
    )
}

@Composable
private fun DriveHeader(
    locoName: String?,
    addressLine: String,
    status: CabConnectionStatus,
    attempt: Int?,
    hasLoco: Boolean,
    actions: DriveActions,
    onReleaseRequest: () -> Unit,
) {
    val largeText =
        LocalDensity.current.fontScale >= 1.5f &&
            LocalConfiguration.current.screenWidthDp < 600
    Surface(
        color = CabTheme.colorScheme.surface,
        contentColor = CabTheme.colorScheme.onSurface,
    ) {
        Column {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                        ),
                    )
                    .heightIn(min = 56.dp)
                    .padding(
                        start = CabTheme.spacing.page,
                        end = CabTheme.spacing.xs,
                        top = CabTheme.spacing.xs,
                        bottom = CabTheme.spacing.xs,
                    )
                    .testTag(DriveTestTags.HEADER),
                verticalAlignment = if (largeText) Alignment.Top else Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = locoName ?: stringResource(R.string.drive_header_no_loco),
                        style = CabTheme.typography.address,
                        color = CabTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = addressLine,
                        style = CabTheme.typography.readoutSmall,
                        color = CabTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!largeText) {
                    StatusPill(
                        status = status,
                        attempt = attempt,
                        modifier = Modifier.testTag(DriveTestTags.STATUS),
                    )
                }
                DriveMenu(
                    hasLoco = hasLoco,
                    actions = actions,
                    onReleaseRequest = onReleaseRequest,
                )
            }
            if (largeText) {
                StatusPill(
                    status = status,
                    attempt = attempt,
                    modifier =
                    Modifier
                        .padding(
                            start = CabTheme.spacing.page,
                            end = CabTheme.spacing.page,
                            bottom = CabTheme.spacing.xs,
                        )
                        .testTag(DriveTestTags.STATUS),
                )
            }
            HorizontalDivider(color = CabTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun headerAddressLine(uiState: DriveUiState): String {
    val loco = uiState.loco
    val layout = uiState.layoutName
    return when {
        loco != null && layout != null ->
            stringResource(R.string.drive_header_address_layout, loco.address, layout)

        loco != null -> stringResource(R.string.drive_header_address, loco.address)

        layout != null -> layout

        else -> stringResource(R.string.drive_header_no_layout)
    }
}

@Composable
private fun DriveMenu(
    hasLoco: Boolean,
    actions: DriveActions,
    onReleaseRequest: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(CabTheme.sizing.touchMin).testTag(DriveTestTags.MENU),
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.drive_more_actions),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.drive_choose_loco)) },
                onClick = {
                    expanded = false
                    actions.onChooseLocomotive()
                },
                modifier = Modifier.testTag(DriveTestTags.CHOOSE_LOCO),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.drive_open_connection)) },
                onClick = {
                    expanded = false
                    actions.onOpenConnection()
                },
            )
            if (hasLoco) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.drive_release)) },
                    onClick = {
                        expanded = false
                        onReleaseRequest()
                    },
                    modifier = Modifier.testTag(DriveTestTags.RELEASE),
                )
            }
        }
    }
}

@Composable
private fun DriveBanners(
    health: DriveHealth?,
    link: DriveLink,
    reconnectAttempt: Int?,
    trackPowerOn: Boolean,
) {
    health ?: return
    val reconnecting = link == DriveLink.Reconnecting || link == DriveLink.Connecting
    val lost = link == DriveLink.Failed || link == DriveLink.Offline
    val stale = health == DriveHealth.Stale && !reconnecting && !lost
    val hasBanner = reconnecting || lost || stale || !trackPowerOn
    if (!hasBanner) return

    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = CabTheme.spacing.page, vertical = CabTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(CabTheme.spacing.xs),
    ) {
        when {
            reconnecting -> {
                val title =
                    if (link == DriveLink.Connecting) {
                        stringResource(R.string.drive_banner_connecting)
                    } else {
                        reconnectAttempt?.let { attempt ->
                            stringResource(R.string.drive_banner_reconnecting, attempt)
                        } ?: stringResource(R.string.drive_banner_reconnecting_short)
                    }
                val body =
                    if (link == DriveLink.Connecting) {
                        stringResource(R.string.drive_banner_connecting_body)
                    } else {
                        stringResource(R.string.drive_banner_paused)
                    }
                StatusBanner(
                    title = title,
                    body = body,
                    icon = Icons.Filled.Refresh,
                    container = CabTheme.extendedColors.cautionContainer,
                    content = CabTheme.extendedColors.onCautionContainer,
                    showProgress = true,
                )
            }

            lost -> StatusBanner(
                title = stringResource(R.string.drive_banner_lost),
                body = stringResource(R.string.drive_banner_lost_body),
                icon = Icons.Filled.Warning,
                container = CabTheme.extendedColors.offlineContainer,
                content = CabTheme.extendedColors.onOfflineContainer,
            )

            stale -> StatusBanner(
                title = stringResource(R.string.drive_banner_stale),
                body = stringResource(R.string.drive_banner_stale_body),
                icon = Icons.Filled.Refresh,
                container = CabTheme.extendedColors.cautionContainer,
                content = CabTheme.extendedColors.onCautionContainer,
            )
        }
        if (!trackPowerOn) {
            StatusBanner(
                title = stringResource(R.string.drive_power_off),
                body = stringResource(R.string.drive_power_off_body),
                icon = Icons.Filled.Warning,
                container = CabTheme.extendedColors.cautionContainer,
                content = CabTheme.extendedColors.onCautionContainer,
            )
        }
    }
}

@Composable
private fun StatusBanner(
    title: String,
    body: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    showProgress: Boolean = false,
) {
    Surface(
        modifier =
        Modifier
            .fillMaxWidth()
            .testTag(DriveTestTags.BANNER)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
            },
        shape = CabTheme.shapes.control,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(CabTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = content,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(CabTheme.sizing.iconMedium),
                    tint = content,
                )
            }
            Spacer(Modifier.size(CabTheme.spacing.sm))
            Column {
                Text(text = title, style = CabTheme.typography.sectionTitle, color = content)
                Text(text = body, style = CabTheme.typography.readoutSmall, color = content)
            }
        }
    }
}

@Composable
private fun DriveIssueCard(
    address: String,
    issue: DriveIssue?,
    actions: DriveActions,
    modifier: Modifier = Modifier,
) {
    when (issue) {
        DriveIssue.InUse -> InlineError(
            title = stringResource(R.string.drive_issue_in_use_title),
            message = stringResource(R.string.drive_issue_in_use_message, address),
            onRetry = { actions.onTakeControl() },
            retryLabel = stringResource(R.string.drive_take_control),
            modifier = modifier.testTag(DriveTestTags.ISSUE),
        )

        DriveIssue.AcquireTimedOut -> InlineError(
            title = stringResource(R.string.drive_issue_timeout_title),
            message = stringResource(R.string.drive_issue_timeout_message, address),
            onRetry = { actions.onRetryAcquire() },
            retryLabel = stringResource(R.string.drive_retry),
            modifier = modifier.testTag(DriveTestTags.ISSUE),
        )

        DriveIssue.CommandFailed -> InlineError(
            title = stringResource(R.string.drive_issue_command_title),
            message = stringResource(R.string.drive_issue_command_message, address),
            onRetry = { actions.onRetryAcquire() },
            retryLabel = stringResource(R.string.drive_retry),
            modifier = modifier.testTag(DriveTestTags.ISSUE),
        )

        null -> Unit
    }
}

@Composable
private fun DriveEmptyContent(
    uiState: DriveUiState,
    actions: DriveActions,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
        modifier
            .fillMaxSize()
            .testTag(DriveTestTags.EMPTY),
        contentAlignment = Alignment.Center,
    ) {
        when (uiState.link) {
            DriveLink.Connected -> EmptyState(
                title = stringResource(R.string.drive_empty_title),
                message = stringResource(R.string.drive_empty_body),
                action = {
                    PrimaryActionButton(
                        text = stringResource(R.string.drive_choose_loco),
                        onClick = { actions.onChooseLocomotive() },
                        modifier = Modifier.testTag(DriveTestTags.CHOOSE_LOCO),
                    )
                },
            )

            DriveLink.Connecting, DriveLink.Reconnecting -> EmptyState(
                title = stringResource(R.string.drive_connecting_title),
                message = stringResource(
                    R.string.drive_connecting_body,
                    uiState.layoutName ?: stringResource(R.string.drive_header_no_layout),
                ),
                action = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(CabTheme.sizing.iconMedium),
                        strokeWidth = 2.dp,
                    )
                },
            )

            DriveLink.Failed, DriveLink.Offline -> EmptyState(
                title = stringResource(R.string.drive_no_link_title),
                message = stringResource(R.string.drive_no_link_body),
                action = {
                    PrimaryActionButton(
                        text = stringResource(R.string.drive_find_layout),
                        onClick = { actions.onOpenConnection() },
                    )
                },
            )
        }
    }
}

private fun DriveLink.toCabStatus(): CabConnectionStatus = when (this) {
    DriveLink.Connected -> CabConnectionStatus.Connected
    DriveLink.Connecting, DriveLink.Reconnecting -> CabConnectionStatus.Reconnecting
    DriveLink.Offline, DriveLink.Failed -> CabConnectionStatus.Offline
}
