@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)

package com.jwoglom.controlx2.presentation.screens.sections

import android.app.AlertDialog
import android.content.ClipData
import android.content.Context
import android.content.DialogInterface
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.text.InputType
import android.widget.EditText
import android.graphics.Typeface
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.core.app.ShareCompat
import androidx.navigation.NavHostController
import com.google.common.base.Splitter
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.annotations.HistoryLogProps
import com.jwoglom.pumpx2.pump.messages.annotations.MessageProps
import com.jwoglom.pumpx2.pump.messages.request.control.BolusPermissionReleaseRequest
import com.jwoglom.pumpx2.pump.messages.request.control.CancelBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ChangeControlIQSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ChangeTimeDateRequest
import com.jwoglom.pumpx2.pump.messages.request.control.CreateIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.DeleteIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.DismissNotificationRequest
import com.jwoglom.pumpx2.pump.messages.request.control.FillCannulaRequest
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.RemoteBgEntryRequest
import com.jwoglom.pumpx2.pump.messages.request.control.RemoteCarbEntryRequest
import com.jwoglom.pumpx2.pump.messages.request.control.RenameIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetActiveIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetG6TransmitterIdRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetIDPSegmentRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetIDPSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetMaxBasalLimitRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetMaxBolusLimitRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetModesRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetPumpAlertSnoozeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetPumpSoundsRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetQuickBolusSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetSleepScheduleRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetTempRateRequest
import com.jwoglom.pumpx2.pump.messages.request.control.StartDexcomG6SensorSessionRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetDexcomG7PairingCodeRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.BolusPermissionChangeReasonRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.IDPSegmentRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.IDPSettingsRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.pumpx2.pump.messages.util.MessageHelpers
import com.jwoglom.pumpx2.shared.JavaHelpers
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.dataStore
import com.jwoglom.controlx2.util.HistoryLogSyncProgress
import com.jwoglom.controlx2.util.MaxHistoryLogRequestCount
import com.jwoglom.controlx2.db.historylog.HistoryLogDatabase
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.db.historylog.HistoryLogDao
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.presentation.components.HistoryLogSyncProgressBar
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.shared.util.shortTimeAgo
import com.jwoglom.pumpx2.pump.PumpState
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.lang.Long.min
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors
import kotlin.math.max
import com.jwoglom.controlx2.util.HistoryLogAnchorManager
import com.jwoglom.controlx2.util.HistoryLogAnchorLookup


@Composable
fun Debug(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    anchorManager: HistoryLogAnchorManager = HistoryLogAnchorManager(LocalContext.current),
    navigateToFeatureFlags: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager: ClipboardManager = LocalClipboardManager.current


    var showSendPumpMessageMenu by remember { mutableStateOf(false) }
    var showMessageCache by remember { mutableStateOf(false) }
    var showHistoryLogs by remember { mutableStateOf(false) }
    var showPumpState by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val ds = LocalDataStore.current

    fun setClipboard(str: String) {
        clipboardManager.setText(AnnotatedString(str))
        Toast.makeText(context, "Saved to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun shareTextContents(str: String, label: String, mimeType: String) {
        ShareCompat.IntentBuilder(context)
            .setType(mimeType)
            .setText(str)
            .setChooserTitle(label)
            .startChooser();
    }

    fun clearDebugLog(context: Context) {
        AlertDialog.Builder(context)
            .setMessage("Are you sure you want to clear the saved debug logs?")
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("Delete") { dialog, _ ->
                dialog.dismiss()
                val filePath = File(context.filesDir, "debugLog-MUA.txt")
                filePath.delete()

            }
            .show()
    }

    fun emptyDatabase(context: Context) {
        AlertDialog.Builder(context)
            .setMessage("Are you sure you want to empty the database?")
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("Delete") { dialog, _ ->
                dialog.dismiss()
                coroutineScope.launch {
                    val historyLogDb = HistoryLogDatabase.getDatabase(context)
                    val historyLogDao = historyLogDb.historyLogDao()
                    historyLogDao.deleteAll()
                }
            }
            .show()
    }

    LazyColumn(
        contentPadding = innerPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 0.dp),
        content = {
            item {
                HeaderLine("Debug")
                Divider()
            }
            if (Prefs(context).serviceEnabled()) {
                item {
                    ListItem(
                        headlineContent = { Text("Disable ControlX2 service") },
                        supportingContent = { Text("Stops the background service and disables it from starting automatically when the app is opened.") },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Stop icon",
                            )
                        },
                        modifier = Modifier.clickable {
                            Prefs(context).setServiceEnabled(false)
                            coroutineScope.launch {
                                delay(250)
                                sendMessage(MessagePaths.TO_SERVER_FORCE_RELOAD, "".toByteArray())
                            }
                        }
                    )
                    Divider()
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.TopStart)
                ) {
                    ListItem(
                        headlineContent = { Text("Send Pump Message") },
                        supportingContent = { Text("Displays the response message for the given request.") },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Build,
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.clickable {
                            showSendPumpMessageMenu = true
                        }
                    )
                    DropdownMenu(
                        expanded = showSendPumpMessageMenu,
                        onDismissRequest = { showSendPumpMessageMenu = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val requestMessages = MessageHelpers.getAllPumpRequestMessages()
                            .stream().filter { m: String ->
                                !m.startsWith("authentication.") &&
                                    !m.startsWith("historyLog.") &&
                                    !m.contains("FactoryResetRequest") &&
                                    !m.contains("FactoryResetBRequest") &&
                                    !m.contains("Nonexistent", ignoreCase = true)
                            }.collect(Collectors.toList())

                        requestMessages.forEach { message ->
                            DropdownMenuItem(
                                text = { Text("$message") },
                                onClick = {
                                    try {
                                        val className =
                                            MessageHelpers.REQUEST_PACKAGE + "." + message

                                        // Custom processing for arguments
                                        if (className == IDPSegmentRequest::class.java.name) {
                                            triggerIDPSegmentDialog(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == IDPSettingsRequest::class.java.name) {
                                            triggerIDPSettingsDialog(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == HistoryLogRequest::class.java.name) {
                                            coroutineScope.launch {
                                                triggerHistoryLogRequestDialog(
                                                    context,
                                                    sendPumpCommands,
                                                    null,
                                                    ds.pumpSid.value,
                                                    coroutineScope,
                                                    anchorManager
                                                )
                                            }
                                            return@DropdownMenuItem
                                        } else if (className == InitiateBolusRequest::class.java.name) {
                                            triggerInitiateBolusRequestDialog(
                                                context,
                                                sendPumpCommands
                                            )
                                            return@DropdownMenuItem
                                        } else if (className == CancelBolusRequest::class.java.name) {
                                            triggerCancelBolusRequestDialog(
                                                context,
                                                sendPumpCommands
                                            )
                                            return@DropdownMenuItem
                                        } else if (className == BolusPermissionChangeReasonRequest::class.java.name) {
                                            triggerMessageWithBolusIdParameter(
                                                context,
                                                sendPumpCommands,
                                                BolusPermissionChangeReasonRequest::class.java
                                            )
                                            return@DropdownMenuItem
                                        } else if (className == BolusPermissionReleaseRequest::class.java.name) {
                                            triggerMessageWithBolusIdParameter(
                                                context,
                                                sendPumpCommands,
                                                BolusPermissionReleaseRequest::class.java
                                            )
                                            return@DropdownMenuItem
                                        } else if (className == RemoteBgEntryRequest::class.java.name) {
                                            triggerRemoteBgEntryRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == RemoteCarbEntryRequest::class.java.name) {
                                            triggerRemoteCarbEntryRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetModesRequest::class.java.name) {
                                            triggerSetModesRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetSleepScheduleRequest::class.java.name) {
                                            triggerSetSleepScheduleRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetTempRateRequest::class.java.name) {
                                            triggerSetTempRateRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == ChangeControlIQSettingsRequest::class.java.name) {
                                            triggerChangeControlIQSettingsRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetQuickBolusSettingsRequest::class.java.name) {
                                            triggerSetQuickBolusSettingsRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == DismissNotificationRequest::class.java.name) {
                                            triggerDismissNotificationRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetG6TransmitterIdRequest::class.java.name) {
                                            triggerSetG6TransmitterIdRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == StartDexcomG6SensorSessionRequest::class.java.name) {
                                            triggerStartDexcomG6SensorSessionRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetDexcomG7PairingCodeRequest::class.java.name) {
                                            triggerSetDexcomG7PairingCodeRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == FillCannulaRequest::class.java.name) {
                                            triggerFillCannulaRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == ChangeTimeDateRequest::class.java.name) {
                                            triggerChangeTimeDateRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetActiveIDPRequest::class.java.name) {
                                            triggerSetActiveIDPRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == CreateIDPRequest::class.java.name) {
                                            triggerCreateIDPRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == RenameIDPRequest::class.java.name) {
                                            triggerRenameIDPRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == DeleteIDPRequest::class.java.name) {
                                            triggerDeleteIDPRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetIDPSettingsRequest::class.java.name) {
                                            triggerSetIDPSettingsRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetIDPSegmentRequest::class.java.name) {
                                            triggerSetIDPSegmentRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetMaxBolusLimitRequest::class.java.name) {
                                            triggerSetMaxBolusLimitRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetMaxBasalLimitRequest::class.java.name) {
                                            triggerSetMaxBasalLimitRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetPumpSoundsRequest::class.java.name) {
                                            triggerSetPumpSoundsRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        } else if (className == SetPumpAlertSnoozeRequest::class.java.name) {
                                            triggerSetPumpAlertSnoozeRequestMessage(context, sendPumpCommands)
                                            return@DropdownMenuItem
                                        }
                                        val clazz = Class.forName(className)
                                        if (!Message::class.java.isAssignableFrom(clazz)) {
                                            throw IllegalArgumentException("Class is not a Message: $className")
                                        }
                                        Timber.i("Selected %s: %s", className, clazz)
                                        triggerGenericPumpMessageDialog(
                                            context,
                                            sendPumpCommands,
                                            clazz.asSubclass(Message::class.java)
                                        )
                                    } catch (e: ClassNotFoundException) {
                                        Timber.e(e)
                                        e.printStackTrace()
                                    } catch (e: IllegalArgumentException) {
                                        Timber.e(e)
                                        e.printStackTrace()
                                    }
                                    showSendPumpMessageMenu = false
                                },
                                leadingIcon = {}
                            )
                            Divider()
                        }
                    }

                }
            }

            item {
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("View Received Message Cache") },
                    supportingContent = { Text("Displays recently received pump messages.") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Build,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        showMessageCache = true
                    }
                )

                if (showMessageCache) {
                    Popup(
                        onDismissRequest = { showMessageCache = false }
                    ) {
                        LaunchedEffect(Unit) {
                            sendMessage(MessagePaths.TO_PUMP_DEBUG_MESSAGE_CACHE, "".toByteArray())
                        }

                        val debugMessageCache = ds.debugMessageCache.observeAsState()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .wrapContentSize(Alignment.TopStart)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            LazyColumn {
                                item {
                                    Spacer(Modifier.height(64.dp))
                                }
                                item {
                                    LazyRow(Modifier.fillMaxWidth()) {
                                        item {
                                            IconButton(
                                                onClick = {
                                                    showMessageCache = false
                                                }
                                            ) {
                                                Icon(Icons.Filled.Close, contentDescription = "Close")
                                            }
                                        }
                                        item {
                                            Spacer(Modifier.width(16.dp))
                                        }
                                        item {
                                            FilledTonalButton(
                                                onClick = {
                                                    debugMessageCache.value?.let {
                                                        shareTextContents(messageCacheToJson(it), "ControlX2 JSON debug data","text/json")
                                                    }
                                                }
                                            ) {
                                                Text("Export")
                                            }
                                        }
                                    }
                                }
                                if (debugMessageCache.value?.isEmpty() == true) {
                                    item {
                                        Text("No message entries present in cache.")
                                    }
                                }
                                debugMessageCache.value?.sortedBy {
                                    it.second.epochSecond * -1
                                }?.forEach { message ->
                                    item {
                                        ListItem(
                                            headlineContent = {
                                                // message name
                                                Text(shortPumpMessageTitle(message.first))
                                            },
                                            supportingContent = {
                                                // message detail
                                                Text(shortPumpMessageDetail(message.first))
                                            },
                                            overlineContent = {
                                                // time
                                                Text("${message.second} (${shortTimeAgo(message.second, nowThresholdSeconds = 1)})")
                                            },
                                            modifier = Modifier.clickable {
                                                shareTextContents(messagePairToJson(message), "ControlX2 ${shortPumpMessageTitle(message.first)} debug data","text/json")
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Divider()
            }

            // History Log Sync Progress Bar
            item {
                HistoryLogSyncProgressBar(
                    replaceWithPaddingWhenComplete = true
                )
            }
            item {
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Get History Logs") },
                    supportingContent = { Text("Fetches history logs within the given range.") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Build,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        Timber.d("pressed Get History Logs")
                        coroutineScope.launch {
                            dataStore.historyLogStatus.value = null
                            sendPumpCommands(SendType.STANDARD, listOf(HistoryLogStatusRequest()))

                            for (i in 1..50) {
                                Timber.d("waiting for historyLogStatus: ${dataStore.historyLogStatus}")
                                if (dataStore.historyLogStatus.value != null) {
                                    break
                                }
                                delay(100)
                            }

                            triggerHistoryLogRequestDialog(
                                context,
                                sendPumpCommands,
                                dataStore.historyLogStatus.value,
                                ds.pumpSid.value,
                                coroutineScope,
                                anchorManager = anchorManager,
                            )
                        }
                    }
                )
            }

            item {
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("View History Log Messages") },
                    supportingContent = { Text("Displays pump history log messages") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Build,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        sendMessage(MessagePaths.TO_PUMP_DEBUG_HISTORYLOG_CACHE, "".toByteArray())
                        Handler(Looper.getMainLooper()).postDelayed({
                            showHistoryLogs = true
                        }, 500)
                    }
                )

                if (showHistoryLogs) {
                    Popup(
                        onDismissRequest = { showHistoryLogs = false }
                    ) {
                        val historyLogCache = ds.historyLogCache.observeAsState()
                        var filterToType by remember { mutableStateOf<String?>(null) }
                        var filterFields by remember { mutableStateOf(historyLogCache.value?.entries?.map { shortHistoryLogPumpMessageTitle(it.value) }?.toSet()?.sorted()) }
                        LaunchedEffect (historyLogCache.value) {
                            filterFields = historyLogCache.value?.entries?.map { shortHistoryLogPumpMessageTitle(it.value) }?.toSet()?.sorted()
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .wrapContentSize(Alignment.TopStart)
                                .background(Color.DarkGray.copy(alpha = 0.3f))
                        ) {
                            LazyColumn {
                                item {
                                    Spacer(Modifier.height(64.dp))
                                }
                                item {
                                    LazyRow(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                                        item {
                                            IconButton(
                                                onClick = {
                                                    showHistoryLogs = false
                                                }
                                            ) {
                                                Icon(Icons.Filled.Close, contentDescription = "Close")
                                            }
                                        }
                                        item {
                                            Spacer(Modifier.width(16.dp))
                                        }
                                        item {
                                            FilledTonalButton(
                                                onClick = {
                                                    historyLogCache.value?.let {
                                                        shareTextContents(historyLogCacheToJson(it), "ControlX2 History Log JSON data","text/json")
                                                    }
                                                }
                                            ) {
                                                Text("Export")
                                            }
                                        }
                                    }
                                }

                                item {
                                    var expanded by remember { mutableStateOf(false) }
                                    val icon = if (expanded)
                                        Icons.Filled.KeyboardArrowUp
                                    else
                                        Icons.Filled.KeyboardArrowDown

                                    var textFieldSize by remember { mutableStateOf(Size.Zero)}

                                    Box (Modifier
                                        .background(Color.White)
                                        .padding(start = 16.dp, end = 16.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = when (filterToType) {
                                                null -> "<All>"
                                                else -> "$filterToType"
                                            },
                                            onValueChange = { filterToType = it },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onGloballyPositioned { coordinates ->
                                                    // This value is used to assign to
                                                    // the DropDown the same width
                                                    textFieldSize = coordinates.size.toSize()
                                                },
                                            label = { Text("Filter") },
                                            trailingIcon = {
                                                Icon(icon, "contentDescription",
                                                    Modifier.clickable { expanded = !expanded })
                                            },
                                            textStyle = TextStyle.Default.copy(fontSize = 16.sp),
                                        )

                                        DropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false },
                                            modifier = Modifier
                                                .width(with(LocalDensity.current) { textFieldSize.width.toDp() })
                                        ) {
                                            filterFields?.forEach { label ->
                                                DropdownMenuItem(onClick = {
                                                    filterToType = label
                                                    expanded = false
                                                }, text = {
                                                    Text(text = label)
                                                })
                                            }
                                        }
                                    }
                                }

                                if (historyLogCache.value?.entries?.isEmpty() == true) {
                                    item {
                                        Text(
                                            "No history log entries present in cache.",
                                            modifier = Modifier
                                                .background(Color.White)
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                        )
                                    }
                                } else {
                                    item {
                                        Text(
                                            "${historyLogCache.value?.size ?: 0} history log entries",
                                            modifier = Modifier
                                                .background(Color.White)
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                                .clickable {
                                                    sendMessage(MessagePaths.TO_PUMP_DEBUG_HISTORYLOG_CACHE, "".toByteArray())
                                                }
                                        )
                                    }
                                }
                                historyLogCache.value?.entries?.sortedBy {
                                    it.key * -1
                                }?.filter {
                                    if (filterToType == null) {
                                        true
                                    } else {
                                        shortHistoryLogPumpMessageTitle(it.value) == filterToType
                                    }
                                }?.forEach { log ->
                                    item {
                                        ListItem(
                                            headlineContent = {
//                                                // message name
                                                Text(shortHistoryLogPumpMessageTitle(log.value))
                                            },
                                            supportingContent = {
                                                // message detail
                                                Text("${log.value}")
                                            },
                                            overlineContent = {
                                                // time
                                                Text("#${log.key} ${log.value.pumpTimeSecInstant}")
                                            },
                                            modifier = Modifier.clickable {
                                                shareTextContents(historyLogToJson(log), "ControlX2 History Log Event","text/json")
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (Prefs(context).connectionSharingEnabled()) {
                item {
                    Divider()
                }

                item {
                    if (Prefs(context).onlySnoopBluetoothEnabled()) {
                        ListItem(
                            headlineContent = { Text("Disable Only Snoop Bluetooth") },
                            supportingContent = { Text("Re-enables app functionality.") },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Stop icon",
                                )
                            },
                            modifier = Modifier.clickable {
                                Prefs(context).setOnlySnoopBluetoothEnabled(false)
                                coroutineScope.launch {
                                    delay(250)
                                    sendMessage(MessagePaths.TO_SERVER_FORCE_RELOAD, "".toByteArray())
                                }
                            }
                        )
                    } else {
                        ListItem(
                            headlineContent = { Text("Enable Only Snoop Bluetooth") },
                            supportingContent = { Text("All app functionality will be disabled, for debugging purposes only.") },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Start icon",
                                )
                            },
                            modifier = Modifier.clickable {
                                Prefs(context).setOnlySnoopBluetoothEnabled(true)
                                coroutineScope.launch {
                                    delay(250)
                                    sendMessage(MessagePaths.TO_SERVER_FORCE_RELOAD, "".toByteArray())
                                }
                            }
                        )
                    }
                }
            }

            item {
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("View PumpState") },
                    supportingContent = { Text("Displays the pump MAC, pairing key, and authentication secrets.") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        showPumpState = true
                    }
                )

                if (showPumpState) {
                    val exportedPumpState = PumpState.exportState(context)
                    Popup(
                        onDismissRequest = { showPumpState = false }
                    ) {

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .wrapContentSize(Alignment.TopStart)
                                .background(Color.DarkGray.copy(alpha = 0.7f))
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                item {
                                    Spacer(Modifier.height(64.dp))
                                }
                                item {
                                    TextField(
                                        value = exportedPumpState,
                                        label = { Text("PumpState") },
                                        onValueChange = {v -> },
                                        modifier = Modifier.fillMaxWidth().height(200.dp).padding(10.dp).background(Color.White)
                                    )
                                }

                                item {
                                    LazyRow {
                                        item {
                                            Button(onClick = {
                                                setClipboard(exportedPumpState)
                                            }) {
                                                Text("Save to clipboard")
                                            }
                                        }
                                        item {
                                            Spacer(Modifier.width(16.dp))
                                        }
                                        item {
                                            Button(onClick = {
                                                showPumpState = false
                                            }) {
                                                Text("Close")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Clear Debug Logs") },
                    supportingContent = { Text("Clears the saved debug logs.") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        clearDebugLog(context)
                    }
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Empty database") },
                    supportingContent = { Text("Removes all saved history logs in sqlite.") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        emptyDatabase(context)
                    }
                )
            }

            item {
                Divider()
            }

            item {
                var qualifyingEventToastsEnabled by remember { mutableStateOf(Prefs(context).qualifyingEventToastsEnabled()) }
                ListItem(
                    headlineContent = { Text(if (qualifyingEventToastsEnabled) "Disable Qualifying Event Toasts" else "Enable Qualifying Event Toasts") },
                    supportingContent = { Text("Show toast notifications when CommService receives qualifying events.") },
                    leadingContent = {
                        Icon(
                            if (qualifyingEventToastsEnabled) Icons.Filled.Check else Icons.Filled.Close,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        qualifyingEventToastsEnabled = !qualifyingEventToastsEnabled
                        Prefs(context).setQualifyingEventToastsEnabled(qualifyingEventToastsEnabled)
                        Toast.makeText(
                            context,
                            "Qualifying event toasts ${if (qualifyingEventToastsEnabled) "enabled" else "disabled"}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            item {
                var httpDebugApiEnabled by remember { mutableStateOf(Prefs(context).httpDebugApiEnabled()) }
                ListItem(
                    headlineContent = { Text(if (httpDebugApiEnabled) "Disable HTTP Debug API" else "Enable HTTP Debug API") },
                    supportingContent = { Text("Toggle the HTTP Debug API on port 18282. Service restart required.") },
                    leadingContent = {
                        Icon(
                            if (httpDebugApiEnabled) Icons.Filled.Check else Icons.Filled.Close,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        httpDebugApiEnabled = !httpDebugApiEnabled
                        Prefs(context).setHttpDebugApiEnabled(httpDebugApiEnabled)
                        Toast.makeText(context, "HTTP Debug API ${if (httpDebugApiEnabled) "enabled" else "disabled"}. Restart service to apply.", Toast.LENGTH_SHORT).show()
                    }
                )

                if (httpDebugApiEnabled) {
                    var username by remember { mutableStateOf(Prefs(context).httpDebugApiUsername()) }
                    ListItem(
                        headlineContent = { Text("API Username") },
                        supportingContent = {
                            OutlinedTextField(
                                value = username,
                                onValueChange = {
                                    username = it
                                    Prefs(context).setHttpDebugApiUsername(it)
                                },
                                label = { Text("Username") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                            )
                        }
                    )
                    var password by remember { mutableStateOf(Prefs(context).httpDebugApiPassword()) }
                    var passwordVisible by remember { mutableStateOf(false) }

                    ListItem(
                        headlineContent = { Text("API Password") },
                        supportingContent = {
                            OutlinedTextField(
                                value = password,
                                onValueChange = {
                                    password = it
                                    Prefs(context).setHttpDebugApiPassword(it)
                                },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                label = { Text("Password") },
                                trailingIcon = {
                                    val image = if (passwordVisible)
                                        Icons.Filled.Visibility
                                    else Icons.Filled.VisibilityOff

                                    // Please provide localized description for accessibility services
                                    val description = if (passwordVisible) "Hide password" else "Show password"

                                    IconButton(onClick = {passwordVisible = !passwordVisible}){
                                        Icon(imageVector  = image, description)
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                            )
                        }
                    )
                }
            }

            item {
                ListItem(
                    headlineContent = { Text("Feature Flags") },
                    supportingContent = { Text("Toggle experimental features.") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable { navigateToFeatureFlags() },
                )
            }
        }
    )
}


fun triggerIDPSegmentDialog(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("Enter IDP ID")
    builder.setMessage("Enter the ID for the Insulin Delivery Profile")
    val input1 = EditText(context)
    input1.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
    builder.setView(input1)
    builder.setPositiveButton("OK") { dialog, which ->
        val idpId = input1.text.toString()
        Timber.i("idp id: %s", idpId)
        val builder2 = AlertDialog.Builder(context)
        builder2.setTitle("Enter segment index")
        builder2.setMessage("Enter the index for the Insulin Delivery Profile segment")
        val input2 = EditText(context)
        input2.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        builder2.setView(input2)
        builder2.setPositiveButton(
            "OK"
        ) { dialog, which ->
            val idpSegment = input2.text.toString()
            Timber.i("idp segment: %s", idpSegment)
            sendPumpCommands(
                SendType.DEBUG_PROMPT,
                listOf(
                    IDPSegmentRequest(idpId.toInt(), idpSegment.toInt())
                )
            )
        }
        builder2.setNegativeButton(
            "Cancel"
        ) { dialog, which -> dialog.cancel() }
        builder2.show()
    }
    builder.setNegativeButton(
        "Cancel"
    ) { dialog, which -> dialog.cancel() }
    builder.show()
}

fun triggerIDPSettingsDialog(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("Enter IDP ID")
    builder.setMessage("Enter the ID for the Insulin Delivery Profile")
    val input1 = EditText(context)
    input1.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
    builder.setView(input1)
    builder.setPositiveButton("OK") { dialog, which ->
        val idpId = input1.text.toString()
        Timber.i("idp id: %s", idpId)
        sendPumpCommands(
            SendType.DEBUG_PROMPT,
            listOf(
                IDPSettingsRequest(idpId.toInt())
            )
        )
    }
    builder.setNegativeButton(
        "Cancel"
    ) { dialog, which -> dialog.cancel() }
    builder.show()
}

const val HistoryLogPreloadTimeoutMs = 30_000L

/**
 * Range dialog for pulling a span of history-log rows, with human-readable dates.
 *
 * Two native sliders pick the start (oldest) and end (head) sequence IDs. Dates are shown by
 * interpolating the sampled anchors via [HistoryLogAnchorLookup]; the anchors are preloaded during
 * startup and here we ensure they're sampled, awaiting the sampler (already running or freshly
 * started) with a timeout so the dialog never hangs. The request count reflects the full span, not
 * how many rows already exist.
 */
suspend fun triggerHistoryLogRequestDialog(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    historyLogStatus: HistoryLogStatusResponse? = null,
    pumpSid: Int? = null,
    scope: CoroutineScope,
    anchorManager: HistoryLogAnchorManager,
) {
    val status = historyLogStatus
    if (status == null) {
        Toast.makeText(context, "History status unavailable", Toast.LENGTH_SHORT).show()
        return
    }
    val sid = pumpSid
    if (sid == null) {
        Toast.makeText(context, "Pump not connected - no pumpSid", Toast.LENGTH_SHORT).show()
        return
    }

    // Ensure date anchors are sampled, awaiting the sampler (already running or freshly started)
    // with a timeout. A missing/empty result degrades gracefully to IDs-only.
    val lookup: HistoryLogAnchorLookup = try {
        val progress = AlertDialog.Builder(context)
            .setTitle("Preparing history")
            .setMessage("Loading date references\u2026")
            .setCancelable(false)
            .show()
        try {
            val ready = anchorManager.ensureLoaded(sid)
            withTimeoutOrNull(HistoryLogPreloadTimeoutMs) { ready.await() }
            // anchors() is a Room query; read it off the main thread once sampling has settled.
            val anchors = withContext(Dispatchers.IO) { anchorManager.anchors(sid) }
            HistoryLogAnchorLookup(anchors)
        } finally {
            progress.dismiss()
        }
    } catch (e: TimeoutCancellationException) {
        Timber.w("HistoryLogAnchor preload timed out after ${HistoryLogPreloadTimeoutMs}ms; IDs only")
        Toast.makeText(
            context,
            "Date references timed out \u2014 showing sequence IDs only",
            Toast.LENGTH_SHORT
        ).show()
        HistoryLogAnchorLookup(emptyList())
    }

    val first = status.firstSequenceNum
    val head = status.lastSequenceNum

    rangeSliderRangeDialog(
        context,
        "Get history logs",
        "Drag the two handles to select the range of history to fetch.",
        first,
        head,
        dateLabel = { id -> formatAnchor(id, lookup) },
        detail = { startId, endId -> "~${requestCount(startId, endId)} request(s)" },
        confirmText = "Fetch"
    ) { startId, endId ->
        // Fetch the full span from start to end. triggerHistoryLogRange walks it end-to-start, so
        // every HistoryLogRequest stays within the pump's history; no head cap, the user can pull
        // the entire fetched range.
        val reqCount = requestCount(startId, endId)
        Toast.makeText(
            context,
            "Fetching IDs $startId\u2013$endId (~$reqCount request(s))",
            Toast.LENGTH_SHORT
        ).show()
        // A new range cancels the running fetch so the two don't overlap the pump's history.
        dataStore.historyLogFetchJob.value?.cancel()
        dataStore.historyLogFetchJob.value = scope.launch {
            triggerHistoryLogRange(
                context,
                sendPumpCommands,
                startId,
                endId,
                sid,
                progressCallback = { target, synced ->
                    dataStore.historyLogSyncProgress.value = HistoryLogSyncProgress(target, synced)
                }
            )
        }
    }
}

private fun formatAnchor(id: Long, lookup: HistoryLogAnchorLookup): String {
    val date = lookup.dateForId(id)
    return if (date != null) "$id  \u00b7  ${date.format(ANCHOR_DATE_FORMAT)}" else id.toString()
}

private val ANCHOR_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")

/** Estimate request batches of MaxHistoryLogRequestCount IDs, matching triggerHistoryLogRange's chunk size (ceil over the span). */
private fun requestCount(startId: Long, endId: Long): Long {
    val span = endId - startId + 1
    return if (span <= 0) 0 else (span + MaxHistoryLogRequestCount - 1) / MaxHistoryLogRequestCount
}

private fun sectionHeading(context: Context, text: String): TextView {
    return TextView(context).apply {
        this.text = text
        setTypeface(null, Typeface.BOLD)
        setPadding(0, 4, 0, 12)
    }
}

suspend fun triggerHistoryLogRange(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    startLog: Long,
    endLog: Long,
    pumpSid: Int,
    progressCallback: ((target: Long, synced: Long) -> Unit)? = null,
    chunkSize: Int = MaxHistoryLogRequestCount,
    requestDelayMs: Long = 500,
    pollIntervalMs: Long = 500,
    groupTimeoutMs: Long = 8000,
) {
    // Progress = fraction of the requested range handled. Chunks already fully in the DB are skipped
    // (no pump ping) and counted as handled; fetched chunks count as they're sent. The bar starts at
    // 0 and reaches 100% once the whole range is covered -- so re-fetching an in-range set is fast.
    val target = endLog - startLog + 1
    val totalNums = (target + chunkSize - 1) / chunkSize
    var num = 0
    var requested = 0L
    progressCallback?.invoke(target, requested)
    val dao = HistoryLogDatabase.getDatabase(context).historyLogDao()

    // HistoryLogRequest returns items BACKWARD from the start ID, so walk the range from its END
    // downward, requesting each chunk from the top so we pull down to chunkStart.
    var i = endLog
    while (i >= startLog) {
        val count = min(chunkSize.toLong(), i - startLog + 1).toInt()
        val chunkEnd = i
        val chunkStart = i - count + 1
        // Skip chunks already fully in the DB -- don't re-ping the pump for IDs we already have.
        val dbCount = dao.getCount(pumpSid, chunkStart, chunkEnd).firstOrNull() ?: 0
        if (dbCount >= count) {
            Timber.i("HistoryLogRange: chunk $chunkStart..$chunkEnd already present ($dbCount/$count), skipping")
            num++
            requested += count
            progressCallback?.invoke(target, requested)
            i = chunkStart - 1
            continue
        }
        num++
        requested += count
        Timber.i("HistoryLogRequest $num/$totalNums from $chunkStart to $chunkEnd count=$count")
        sendPumpCommands(SendType.STANDARD, listOf(HistoryLogRequest(chunkEnd, count)))
        progressCallback?.invoke(target, requested)
        Toast.makeText(context, "$num/$totalNums: Requesting $chunkStart to $chunkEnd", Toast.LENGTH_SHORT).show()

        // Wait for the pump to deliver this chunk (up to a timeout) before requesting the next, so we
        // keep backpressure and do not flood the BLE link.
        delay(requestDelayMs)
        var waitTimeMs = 0L
        while (waitTimeMs < groupTimeoutMs) {
            delay(pollIntervalMs)
            waitTimeMs += pollIntervalMs
            val dbCount = dao.getCount(pumpSid, chunkStart, chunkEnd).firstOrNull() ?: 0
            if (dbCount >= count) break
        }
        i = chunkStart - 1
    }
    progressCallback?.invoke(target, requested)
    Toast.makeText(context, "History fetch complete — IDs $startLog to $endLog", Toast.LENGTH_SHORT).show()
}

fun triggerInitiateBolusRequestDialog(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("Enter units to deliver bolus")
    builder.setMessage("Enter the number of units in INTEGER FORM: 1000 = 1 unit, 100 = 0.1 unit, 10 = 0.01 unit. Minimum value is 50 (0.05 unit)")
    val input1 = EditText(context)
    input1.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
    builder.setView(input1)
    builder.setPositiveButton("OK", DialogInterface.OnClickListener { dialog, which ->
        val numUnitsStr = input1.text.toString()
        Timber.i("numUnits: %s", numUnitsStr)
        if ("" == numUnitsStr) {
            Timber.e("Not delivering bolus because no units entered.")
            return@OnClickListener
        }
        val builder2 = AlertDialog.Builder(context)
        builder2.setTitle("CONFIRM BOLUS!!")
        builder2.setMessage("Enter the bolus ID from BolusPermissionRequest. THIS WILL ACTUALLY DELIVER THE BOLUS. Enter a blank value to cancel.")
        val input2 = EditText(context)
        input2.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        builder2.setView(input2)
        builder2.setPositiveButton("OK",
            DialogInterface.OnClickListener { dialog, which ->
                val bolusIdStr = input2.text.toString()
                Timber.i("currentIob: %s", bolusIdStr)
                if ("" == bolusIdStr) {
                    Timber.e("Not delivering bolus because no bolus ID entered.")
                    return@OnClickListener
                }
                val numUnits = numUnitsStr.toInt()
                val bolusId = bolusIdStr.toInt()
                // tandemEventCallback.lastBolusId = bolusId
                // InitiateBolusRequest(long totalVolume, int bolusTypeBitmask, long foodVolume, long correctionVolume, int bolusCarbs, int bolusBG, long bolusIOB)
                sendPumpCommands(
                    SendType.DEBUG_PROMPT,
                    listOf(
                        InitiateBolusRequest(
                            numUnits.toLong(),
                            bolusId,
                            BolusDeliveryHistoryLog.BolusType.toBitmask(BolusDeliveryHistoryLog.BolusType.FOOD2),
                            0L,
                            0L,
                            0,
                            0,
                            0
                        )
                    )
                )
            })
        builder2.setNegativeButton(
            "Cancel"
        ) { dialog, which -> dialog.cancel() }
        builder2.show()
    })
    builder.setNegativeButton(
        "Cancel"
    ) { dialog, which -> dialog.cancel() }
    builder.show()
}

fun triggerCancelBolusRequestDialog(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("CancelBolusRequest")
    builder.setMessage("Enter the bolus ID (this can be received from currentStatus.LastBolusStatusV2)")
    val input1 = EditText(context)
    input1.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
//    if (tandemEventCallback.lastBolusId > 0) {
//        input1.setText(java.lang.String.valueOf(tandemEventCallback.lastBolusId))
//    }
    builder.setView(input1)
    builder.setPositiveButton("OK", DialogInterface.OnClickListener { dialog, which ->
        val bolusIdStr = input1.text.toString()
        Timber.i("bolusId: %s", bolusIdStr)
        if ("" == bolusIdStr) {
            Timber.e("Not cancelling bolus because no units entered.")
            return@OnClickListener
        }
        val bolusId = bolusIdStr.toInt()
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(CancelBolusRequest(bolusId)))
    })
    builder.setNegativeButton(
        "Cancel"
    ) { dialog, which -> dialog.cancel() }
    builder.show()
}

fun triggerMessageWithBolusIdParameter(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    messageClass: Class<out Message>
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle(messageClass.simpleName)
    builder.setMessage("Enter the bolus ID (this can be received from the in-progress bolus)")
    val input1 = EditText(context)
    input1.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
//    if (tandemEventCallback.lastBolusId > 0) {
//        input1.setText(java.lang.String.valueOf(tandemEventCallback.lastBolusId))
//    }
    builder.setView(input1)
    builder.setPositiveButton("OK", DialogInterface.OnClickListener { dialog, which ->
        val bolusIdStr = input1.text.toString()
        Timber.i("bolusId: %s", bolusIdStr)
        if ("" == bolusIdStr) {
            Timber.e("Not sending message because no bolus ID entered.")
            return@OnClickListener
        }
        val bolusId = bolusIdStr.toLong()
        val constructor = messageClass.constructors
            .filterIsInstance<Constructor<out Message>>()
            .filter { java.lang.reflect.Modifier.isPublic(it.modifiers) && it.parameterCount == 1 }
            .firstOrNull { ctor ->
                val type = ctor.parameterTypes[0]
                type == Int::class.javaPrimitiveType || type == Int::class.java ||
                    type == Long::class.javaPrimitiveType || type == Long::class.java
            }
        if (constructor == null) {
            Timber.e("No constructor with numeric bolus ID parameter found for %s", messageClass.name)
            Toast.makeText(context, "No bolus ID constructor available for ${messageClass.simpleName}", Toast.LENGTH_SHORT).show()
            return@OnClickListener
        }
        try {
            val paramType = constructor.parameterTypes[0]
            val message = if (paramType == Int::class.javaPrimitiveType || paramType == Int::class.java) {
                constructor.newInstance(bolusId.toInt())
            } else {
                constructor.newInstance(bolusId)
            }
            sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        } catch (e: IllegalAccessException) {
            Timber.e(e)
        } catch (e: InstantiationException) {
            Timber.e(e)
        } catch (e: InvocationTargetException) {
            Timber.e(e)
        }
    })
    builder.setNegativeButton(
        "Cancel"
    ) { dialog, which -> dialog.cancel() }
    builder.show()
}

fun triggerGenericPumpMessageDialog(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    messageClass: Class<out Message>,
) {
    val constructors = messageClass.constructors
        .filterIsInstance<Constructor<out Message>>()
        .filter { java.lang.reflect.Modifier.isPublic(it.modifiers) }

    val noArgConstructor = constructors.firstOrNull { it.parameterCount == 0 }
    val argumentConstructors = constructors
        .filter { it.parameterCount > 0 }
        .filter { ctor -> ctor.parameterTypes.none { it == ByteArray::class.java } }
        .filter { ctor -> ctor.parameterTypes.all { isSupportedDebugConstructorType(it) } }
        .sortedBy { it.parameterCount }

    if (argumentConstructors.isEmpty()) {
        if (noArgConstructor == null) {
            Toast.makeText(context, "No usable constructor found for ${messageClass.simpleName}", Toast.LENGTH_SHORT).show()
            return
        }
        sendConstructedPumpMessage(context, sendPumpCommands, noArgConstructor, emptyList())
        return
    }

    val options = mutableListOf<String>()
    val optionConstructors = mutableListOf<Constructor<out Message>?>()
    if (noArgConstructor != null) {
        options.add("No arguments")
        optionConstructors.add(noArgConstructor)
    }
    argumentConstructors.forEach {
        options.add(formatDebugConstructorSignature(messageClass, it))
        optionConstructors.add(it)
    }

    if (optionConstructors.size == 1) {
        val onlyConstructor = optionConstructors.first()
        if (onlyConstructor == null || onlyConstructor.parameterCount == 0) {
            sendConstructedPumpMessage(context, sendPumpCommands, noArgConstructor, emptyList())
        } else {
            triggerConstructorArgumentDialog(context, sendPumpCommands, messageClass, onlyConstructor)
        }
        return
    }

    AlertDialog.Builder(context)
        .setTitle("${messageClass.simpleName}: Constructor")
        .setItems(options.toTypedArray()) { dialog, which ->
            val selected = optionConstructors[which]
            if (selected == null || selected.parameterCount == 0) {
                sendConstructedPumpMessage(context, sendPumpCommands, noArgConstructor, emptyList())
            } else {
                triggerConstructorArgumentDialog(context, sendPumpCommands, messageClass, selected)
            }
        }
        .setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        .show()
}

fun triggerConstructorArgumentDialog(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    messageClass: Class<out Message>,
    constructor: Constructor<out Message>,
) {
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val inputs = mutableListOf<EditText>()
    constructor.parameterTypes.forEachIndexed { index, paramType ->
        val input = EditText(context)
        input.hint = "arg${index + 1}: ${debugTypeHint(paramType)}"
        input.inputType = debugInputType(paramType)
        layout.addView(input)
        inputs.add(input)
    }

    AlertDialog.Builder(context)
        .setTitle(formatDebugConstructorSignature(messageClass, constructor))
        .setView(layout)
        .setPositiveButton("Send") { dialog, _ ->
            val args = mutableListOf<Any>()
            for ((index, paramType) in constructor.parameterTypes.withIndex()) {
                val raw = inputs[index].text.toString().trim()
                if (raw.isBlank()) {
                    Toast.makeText(context, "Argument ${index + 1} is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                try {
                    args.add(parseDebugConstructorArg(raw, paramType))
                } catch (e: Exception) {
                    Timber.e(e)
                    Toast.makeText(
                        context,
                        "Invalid value for argument ${index + 1} (${debugTypeHint(paramType)})",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
            }
            sendConstructedPumpMessage(context, sendPumpCommands, constructor, args)
            dialog.dismiss()
        }
        .setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        .show()
}

fun sendConstructedPumpMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    constructor: Constructor<out Message>?,
    args: List<Any>,
) {
    if (constructor == null) {
        Toast.makeText(context, "No constructor available", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val message = constructor.newInstance(*args.toTypedArray())
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
    } catch (e: IllegalAccessException) {
        Timber.e(e)
        Toast.makeText(context, "Unable to access constructor", Toast.LENGTH_SHORT).show()
    } catch (e: InstantiationException) {
        Timber.e(e)
        Toast.makeText(context, "Unable to create message", Toast.LENGTH_SHORT).show()
    } catch (e: InvocationTargetException) {
        Timber.e(e)
        Toast.makeText(context, "Message constructor failed", Toast.LENGTH_SHORT).show()
    }
}

fun formatDebugConstructorSignature(
    messageClass: Class<out Message>,
    constructor: Constructor<out Message>,
): String {
    return "${messageClass.simpleName}(" + constructor.parameterTypes.joinToString(", ") { it.simpleName } + ")"
}

fun isSupportedDebugConstructorType(type: Class<*>): Boolean {
    if (type == ByteArray::class.java) {
        return false
    }
    if (type.isEnum) {
        return true
    }
    return type == String::class.java ||
        type == Instant::class.java ||
        type == Int::class.javaPrimitiveType || type == Int::class.java ||
        type == Long::class.javaPrimitiveType || type == Long::class.java ||
        type == Boolean::class.javaPrimitiveType || type == Boolean::class.java ||
        type == Float::class.javaPrimitiveType || type == Float::class.java ||
        type == Double::class.javaPrimitiveType || type == Double::class.java ||
        type == Short::class.javaPrimitiveType || type == Short::class.java ||
        type == Byte::class.javaPrimitiveType || type == Byte::class.java
}

fun debugTypeHint(type: Class<*>): String {
    if (type == Instant::class.java) {
        return "Instant (epoch seconds or ISO-8601)"
    }
    if (type.isEnum) {
        val values = type.enumConstants
            ?.map { (it as Enum<*>).name }
            ?.joinToString("|")
            ?: ""
        return "${type.simpleName} [$values]"
    }
    return type.simpleName
}

fun debugInputType(type: Class<*>): Int {
    return when {
        type == Int::class.javaPrimitiveType || type == Int::class.java ||
            type == Long::class.javaPrimitiveType || type == Long::class.java ||
            type == Short::class.javaPrimitiveType || type == Short::class.java ||
            type == Byte::class.javaPrimitiveType || type == Byte::class.java ->
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED

        type == Float::class.javaPrimitiveType || type == Float::class.java ||
            type == Double::class.javaPrimitiveType || type == Double::class.java ->
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED

        else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
    }
}

fun parseDebugConstructorArg(raw: String, type: Class<*>): Any {
    return when {
        type == String::class.java -> raw
        type == Int::class.javaPrimitiveType || type == Int::class.java -> raw.toInt()
        type == Long::class.javaPrimitiveType || type == Long::class.java -> raw.toLong()
        type == Boolean::class.javaPrimitiveType || type == Boolean::class.java -> parseBoolInput(raw)
        type == Float::class.javaPrimitiveType || type == Float::class.java -> raw.toFloat()
        type == Double::class.javaPrimitiveType || type == Double::class.java -> raw.toDouble()
        type == Short::class.javaPrimitiveType || type == Short::class.java -> raw.toShort()
        type == Byte::class.javaPrimitiveType || type == Byte::class.java -> raw.toByte()
        type == Instant::class.java -> if (raw.matches(Regex("^-?\\d+$"))) Instant.ofEpochSecond(raw.toLong()) else Instant.parse(raw)
        type.isEnum -> {
            val enumValue = type.enumConstants?.firstOrNull {
                (it as Enum<*>).name.equals(raw, ignoreCase = true)
            }
            if (enumValue != null) {
                enumValue
            } else {
                throw IllegalArgumentException("Invalid enum value '$raw' for ${type.name}")
            }
        }
        else -> throw IllegalArgumentException("Unsupported parameter type: ${type.name}")
    }
}


fun parseBoolInput(text: String): Boolean = text == "1" || text.equals("true", ignoreCase = true)

fun triggerRemoteBgEntryRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("RemoteBgEntryRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val bgInput = EditText(context)
    val glucoseUnit = dataStore.glucoseUnitPreference.value ?: GlucoseUnit.MGDL
    bgInput.hint = "BG (${glucoseUnit.abbreviation})"
    bgInput.inputType = InputType.TYPE_CLASS_NUMBER
    val calibrationInput = EditText(context)
    calibrationInput.hint = "Use for CGM calibration? (true/false/1/0)"
    val autopopInput = EditText(context)
    autopopInput.hint = "Is autopop BG? (true/false/1/0)"
    val pumpTimeInput = EditText(context)
    pumpTimeInput.hint = "Pump time seconds since boot"
    pumpTimeInput.inputType = InputType.TYPE_CLASS_NUMBER
    val bolusIdInput = EditText(context)
    bolusIdInput.hint = "Bolus ID"
    bolusIdInput.inputType = InputType.TYPE_CLASS_NUMBER
    listOf(bgInput, calibrationInput, autopopInput, pumpTimeInput, bolusIdInput).forEach {
        layout.addView(it)
    }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        val message = RemoteBgEntryRequest(
            bgInput.text.toString().toInt(),
            parseBoolInput(calibrationInput.text.toString()),
            parseBoolInput(autopopInput.text.toString()),
            pumpTimeInput.text.toString().toLong(),
            bolusIdInput.text.toString().toInt(),
        )
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerRemoteCarbEntryRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("RemoteCarbEntryRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val carbsInput = EditText(context)
    carbsInput.hint = "Carbs (g)"
    carbsInput.inputType = InputType.TYPE_CLASS_NUMBER
    val pumpTimeInput = EditText(context)
    pumpTimeInput.hint = "Pump time seconds since boot"
    pumpTimeInput.inputType = InputType.TYPE_CLASS_NUMBER
    val bolusIdInput = EditText(context)
    bolusIdInput.hint = "Bolus ID"
    bolusIdInput.inputType = InputType.TYPE_CLASS_NUMBER
    listOf(carbsInput, pumpTimeInput, bolusIdInput).forEach { layout.addView(it) }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        val message = RemoteCarbEntryRequest(
            carbsInput.text.toString().toInt(),
            pumpTimeInput.text.toString().toLong(),
            bolusIdInput.text.toString().toInt(),
        )
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetModesRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetModesRequest")
    val input = EditText(context)
    input.hint = "Mode bitmap (1-4)"
    input.inputType = InputType.TYPE_CLASS_NUMBER
    builder.setView(input)
    builder.setPositiveButton("Send") { dialog, _ ->
        val bitmap = input.text.toString().toInt()
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(SetModesRequest(bitmap)))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetSleepScheduleRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetSleepScheduleRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val slotInput = EditText(context)
    slotInput.hint = "Slot"
    slotInput.inputType = InputType.TYPE_CLASS_NUMBER
    val scheduleInput = EditText(context)
    scheduleInput.hint = "Schedule bytes (comma-separated)"
    val flagInput = EditText(context)
    flagInput.hint = "Flag"
    flagInput.inputType = InputType.TYPE_CLASS_NUMBER
    listOf(slotInput, scheduleInput, flagInput).forEach { layout.addView(it) }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        val scheduleBytes = scheduleInput.text.toString()
            .split(',')
            .filter { it.isNotBlank() }
            .map { it.trim().toInt().toByte() }
            .toByteArray()
        val message = SetSleepScheduleRequest(
            slotInput.text.toString().toInt(),
            scheduleBytes,
            flagInput.text.toString().toInt(),
        )
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetTempRateRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetTempRateRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val minutesInput = EditText(context)
    minutesInput.hint = "Minutes (>=15)"
    minutesInput.inputType = InputType.TYPE_CLASS_NUMBER
    val percentInput = EditText(context)
    percentInput.hint = "Percent (0-250)"
    percentInput.inputType = InputType.TYPE_CLASS_NUMBER
    listOf(minutesInput, percentInput).forEach { layout.addView(it) }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        val message = SetTempRateRequest(
            minutesInput.text.toString().toInt(),
            percentInput.text.toString().toInt(),
        )
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerChangeControlIQSettingsRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("ChangeControlIQSettingsRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val enabledInput = EditText(context)
    enabledInput.hint = "Enabled (true/false/1/0)"
    val weightInput = EditText(context)
    weightInput.hint = "Weight lbs"
    weightInput.inputType = InputType.TYPE_CLASS_NUMBER
    val tdiInput = EditText(context)
    tdiInput.hint = "Total daily insulin units"
    tdiInput.inputType = InputType.TYPE_CLASS_NUMBER
    listOf(enabledInput, weightInput, tdiInput).forEach { layout.addView(it) }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        val message = ChangeControlIQSettingsRequest(
            parseBoolInput(enabledInput.text.toString()),
            weightInput.text.toString().toInt(),
            tdiInput.text.toString().toInt(),
        )
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetQuickBolusSettingsRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetQuickBolusSettingsRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val enabledInput = EditText(context)
    enabledInput.hint = "Enabled (true/false/1/0)"
    val modeInput = EditText(context)
    modeInput.hint = "Mode (UNITS or CARBS)"
    val incrementInput = EditText(context)
    incrementInput.hint = "Increment enum (e.g., UNITS_0_5)"
    listOf(enabledInput, modeInput, incrementInput).forEach { layout.addView(it) }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        val enabled = parseBoolInput(enabledInput.text.toString())
        val mode = SetQuickBolusSettingsRequest.QuickBolusMode.valueOf(modeInput.text.toString().uppercase())
        val increment = SetQuickBolusSettingsRequest.QuickBolusIncrement.valueOf(
            incrementInput.text.toString().uppercase()
        )
        val message = SetQuickBolusSettingsRequest(enabled, mode, increment)
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerDismissNotificationRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("DismissNotificationRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val notificationIdInput = EditText(context)
    notificationIdInput.hint = "Notification ID"
    notificationIdInput.inputType = InputType.TYPE_CLASS_NUMBER
    val notificationTypeInput = EditText(context)
    notificationTypeInput.hint = "Notification type (0-3)"
    notificationTypeInput.inputType = InputType.TYPE_CLASS_NUMBER
    listOf(notificationIdInput, notificationTypeInput).forEach { layout.addView(it) }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        val message = DismissNotificationRequest(
            DismissNotificationRequest.NotificationType.fromId(
                notificationTypeInput.text.toString().toInt()
            ) ?: DismissNotificationRequest.NotificationType.REMINDER,
            notificationIdInput.text.toString().toLong(),
        )
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetG6TransmitterIdRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetG6TransmitterIdRequest")
    val input = EditText(context)
    input.hint = "Transmitter ID"
    builder.setView(input)
    builder.setPositiveButton("Send") { dialog, _ ->
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(SetG6TransmitterIdRequest(input.text.toString())))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerStartDexcomG6SensorSessionRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("StartDexcomG6SensorSessionRequest")
    val input = EditText(context)
    input.hint = "Sensor code (0 for no code)"
    input.inputType = InputType.TYPE_CLASS_NUMBER
    builder.setView(input)
    builder.setPositiveButton("Send") { dialog, _ ->
        val code = if (input.text.isNullOrBlank()) 0 else input.text.toString().toInt()
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(StartDexcomG6SensorSessionRequest(code)))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetDexcomG7PairingCodeRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetDexcomG7PairingCodeRequest")
    val input = EditText(context)
    input.hint = "Pairing code"
    input.inputType = InputType.TYPE_CLASS_NUMBER
    builder.setView(input)
    builder.setPositiveButton("Send") { dialog, _ ->
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(SetDexcomG7PairingCodeRequest(input.text.toString().toInt())))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerFillCannulaRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("FillCannulaRequest")
    val input = EditText(context)
    input.hint = "Prime size (milliunits)"
    input.inputType = InputType.TYPE_CLASS_NUMBER
    builder.setView(input)
    builder.setPositiveButton("Send") { dialog, _ ->
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(FillCannulaRequest(input.text.toString().toInt())))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerChangeTimeDateRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("ChangeTimeDateRequest")
    val input = EditText(context)
    input.hint = "Tandem epoch seconds"
    input.inputType = InputType.TYPE_CLASS_NUMBER
    builder.setView(input)
    builder.setPositiveButton("Send") { dialog, _ ->
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(ChangeTimeDateRequest(input.text.toString().toLong())))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetActiveIDPRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetActiveIDPRequest")
    val input = EditText(context)
    input.hint = "IDP ID"
    input.inputType = InputType.TYPE_CLASS_NUMBER
    builder.setView(input)
    builder.setPositiveButton("Send") { dialog, _ ->
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(SetActiveIDPRequest(input.text.toString().toInt())))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerCreateIDPRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("CreateIDPRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val nameInput = EditText(context)
    nameInput.hint = "Profile name"
    val carbRatioInput = EditText(context)
    carbRatioInput.hint = "First segment carb ratio"
    carbRatioInput.inputType = InputType.TYPE_CLASS_NUMBER
    val basalRateInput = EditText(context)
    basalRateInput.hint = "First segment basal rate"
    basalRateInput.inputType = InputType.TYPE_CLASS_NUMBER
    val targetBgInput = EditText(context)
    targetBgInput.hint = "First segment target BG"
    targetBgInput.inputType = InputType.TYPE_CLASS_NUMBER
    val isfInput = EditText(context)
    isfInput.hint = "First segment ISF"
    isfInput.inputType = InputType.TYPE_CLASS_NUMBER
    val durationInput = EditText(context)
    durationInput.hint = "Insulin duration"
    durationInput.inputType = InputType.TYPE_CLASS_NUMBER
    val carbEntryInput = EditText(context)
    carbEntryInput.hint = "Carb entry"
    carbEntryInput.inputType = InputType.TYPE_CLASS_NUMBER
    val sourceIdInput = EditText(context)
    sourceIdInput.hint = "Source IDP ID (blank for new)"
    listOf(
        nameInput,
        carbRatioInput,
        basalRateInput,
        targetBgInput,
        isfInput,
        durationInput,
        carbEntryInput,
        sourceIdInput,
    ).forEach { layout.addView(it) }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        val message = if (sourceIdInput.text.isNullOrBlank()) {
            CreateIDPRequest(
                nameInput.text.toString(),
                carbRatioInput.text.toString().toInt(),
                basalRateInput.text.toString().toInt(),
                targetBgInput.text.toString().toInt(),
                isfInput.text.toString().toInt(),
                durationInput.text.toString().toInt(),
                carbEntryInput.text.toString().toInt(),
            )
        } else {
            CreateIDPRequest(
                nameInput.text.toString(),
                sourceIdInput.text.toString().toInt(),
            )
        }
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerRenameIDPRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("RenameIDPRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val idInput = EditText(context)
    idInput.hint = "IDP ID"
    idInput.inputType = InputType.TYPE_CLASS_NUMBER
    val nameInput = EditText(context)
    nameInput.hint = "New profile name"
    listOf(idInput, nameInput).forEach { layout.addView(it) }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(RenameIDPRequest(idInput.text.toString().toInt(), nameInput.text.toString())))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerDeleteIDPRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("DeleteIDPRequest")
    val input = EditText(context)
    input.hint = "IDP ID"
    input.inputType = InputType.TYPE_CLASS_NUMBER
    builder.setView(input)
    builder.setPositiveButton("Send") { dialog, _ ->
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(DeleteIDPRequest(input.text.toString().toInt())))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetIDPSettingsRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetIDPSettingsRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val idInput = EditText(context)
    idInput.hint = "IDP ID"
    idInput.inputType = InputType.TYPE_CLASS_NUMBER
    val insulinDurationInput = EditText(context)
    insulinDurationInput.hint = "Insulin duration"
    insulinDurationInput.inputType = InputType.TYPE_CLASS_NUMBER
    val carbEntryInput = EditText(context)
    carbEntryInput.hint = "Carb entry"
    carbEntryInput.inputType = InputType.TYPE_CLASS_NUMBER
    val changeTypeInput = EditText(context)
    changeTypeInput.hint = "Change type (CHANGE_INSULIN_DURATION or CHANGE_CARB_ENTRY)"
    listOf(idInput, insulinDurationInput, carbEntryInput, changeTypeInput).forEach { layout.addView(it) }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        val changeType = SetIDPSettingsRequest.ChangeType.valueOf(changeTypeInput.text.toString().uppercase())
        val message = SetIDPSettingsRequest(
            idInput.text.toString().toInt(),
            insulinDurationInput.text.toString().toInt(),
            carbEntryInput.text.toString().toInt(),
            changeType,
        )
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetIDPSegmentRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetIDPSegmentRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val idpIdInput = EditText(context)
    idpIdInput.hint = "IDP ID"
    idpIdInput.inputType = InputType.TYPE_CLASS_NUMBER
    val unknownIdInput = EditText(context)
    unknownIdInput.hint = "Unknown ID"
    unknownIdInput.inputType = InputType.TYPE_CLASS_NUMBER
    val segmentIndexInput = EditText(context)
    segmentIndexInput.hint = "Segment index"
    segmentIndexInput.inputType = InputType.TYPE_CLASS_NUMBER
    val operationInput = EditText(context)
    operationInput.hint = "Operation (MODIFY_SEGMENT_ID/CREATE_SEGMENT/DELETE_SEGMENT_ID)"
    val startTimeInput = EditText(context)
    startTimeInput.hint = "Profile start time"
    startTimeInput.inputType = InputType.TYPE_CLASS_NUMBER
    val basalRateInput = EditText(context)
    basalRateInput.hint = "Profile basal rate"
    basalRateInput.inputType = InputType.TYPE_CLASS_NUMBER
    val carbRatioInput = EditText(context)
    carbRatioInput.hint = "Profile carb ratio"
    carbRatioInput.inputType = InputType.TYPE_CLASS_NUMBER
    val targetBgInput = EditText(context)
    targetBgInput.hint = "Profile target BG"
    targetBgInput.inputType = InputType.TYPE_CLASS_NUMBER
    val isfInput = EditText(context)
    isfInput.hint = "Profile ISF"
    isfInput.inputType = InputType.TYPE_CLASS_NUMBER
    val statusInput = EditText(context)
    statusInput.hint = "Status bitmask"
    statusInput.inputType = InputType.TYPE_CLASS_NUMBER
    listOf(
        idpIdInput,
        unknownIdInput,
        segmentIndexInput,
        operationInput,
        startTimeInput,
        basalRateInput,
        carbRatioInput,
        targetBgInput,
        isfInput,
        statusInput,
    ).forEach { layout.addView(it) }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        val message = SetIDPSegmentRequest(
            idpIdInput.text.toString().toInt(),
            unknownIdInput.text.toString().toInt(),
            segmentIndexInput.text.toString().toInt(),
            SetIDPSegmentRequest.IDPSegmentOperation.valueOf(operationInput.text.toString().uppercase()),
            startTimeInput.text.toString().toInt(),
            basalRateInput.text.toString().toInt(),
            carbRatioInput.text.toString().toLong(),
            targetBgInput.text.toString().toInt(),
            isfInput.text.toString().toInt(),
            statusInput.text.toString().toInt(),
        )
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetMaxBolusLimitRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetMaxBolusLimitRequest")
    val input = EditText(context)
    input.hint = "Max bolus (milliunits)"
    input.inputType = InputType.TYPE_CLASS_NUMBER
    builder.setView(input)
    builder.setPositiveButton("Send") { dialog, _ ->
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(SetMaxBolusLimitRequest(input.text.toString().toInt())))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetMaxBasalLimitRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetMaxBasalLimitRequest")
    val input = EditText(context)
    input.hint = "Max hourly basal (milliunits)"
    input.inputType = InputType.TYPE_CLASS_NUMBER
    builder.setView(input)
    builder.setPositiveButton("Send") { dialog, _ ->
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(SetMaxBasalLimitRequest(input.text.toString().toLong())))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetPumpSoundsRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetPumpSoundsRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val quickBolusInput = EditText(context)
    quickBolusInput.hint = "Quick bolus annunciation"
    quickBolusInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
    val generalInput = EditText(context)
    generalInput.hint = "General annunciation"
    generalInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
    val reminderInput = EditText(context)
    reminderInput.hint = "Reminder annunciation"
    reminderInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
    val alertInput = EditText(context)
    alertInput.hint = "Alert annunciation"
    alertInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
    val alarmInput = EditText(context)
    alarmInput.hint = "Alarm annunciation"
    alarmInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
    val cgmAInput = EditText(context)
    cgmAInput.hint = "CGM alert annunciation A"
    cgmAInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
    val cgmBInput = EditText(context)
    cgmBInput.hint = "CGM alert annunciation B"
    cgmBInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
    val changeBitmaskInput = EditText(context)
    changeBitmaskInput.hint = "Change bitmask"
    changeBitmaskInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
    listOf(
        quickBolusInput,
        generalInput,
        reminderInput,
        alertInput,
        alarmInput,
        cgmAInput,
        cgmBInput,
        changeBitmaskInput,
    ).forEach { layout.addView(it) }
    builder.setView(layout)
//    builder.setNeutralButton("RunTest") { dialog, _ ->
//        dialog.dismiss()
//        val message = {r: Int -> SetPumpSoundsRequest(
//            r,
//            3,
//            r,
//            3,
//            3,
//            3,
//            0,
//            0,
//            4,
//        )}
//        sendPumpCommands(SendType.DEBUG_PROMPT, (-127..128).flatMap { i -> listOf(message(i)) } )
//    }
    builder.setPositiveButton("Send") { dialog, _ ->
        val message = SetPumpSoundsRequest(
            quickBolusInput.text.toString().toInt(),
            generalInput.text.toString().toInt(),
            reminderInput.text.toString().toInt(),
            alertInput.text.toString().toInt(),
            alarmInput.text.toString().toInt(),
            cgmAInput.text.toString().toInt(),
            cgmBInput.text.toString().toInt(),
            changeBitmaskInput.text.toString().toInt(),
        )
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun triggerSetPumpAlertSnoozeRequestMessage(
    context: Context,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("SetPumpAlertSnoozeRequest")
    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.VERTICAL
    val enabledInput = EditText(context)
    enabledInput.hint = "Snooze enabled (true/false/1/0)"
    val durationInput = EditText(context)
    durationInput.hint = "Snooze duration minutes"
    durationInput.inputType = InputType.TYPE_CLASS_NUMBER
    listOf(enabledInput, durationInput).forEach { layout.addView(it) }
    builder.setView(layout)
    builder.setPositiveButton("Send") { dialog, _ ->
        val message = SetPumpAlertSnoozeRequest(
            parseBoolInput(enabledInput.text.toString()),
            durationInput.text.toString().toInt(),
        )
        sendPumpCommands(SendType.DEBUG_PROMPT, listOf(message))
        dialog.dismiss()
    }
    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
    builder.show()
}

fun messageCacheToJson(cache: List<Pair<Message, Instant>>): String {
    val arr = JSONArray()
    cache.forEach {
        arr.put(JSONObject(messagePairToJson(it)))
    }
    return arr.toString()
}

fun messagePairToJson(it: Pair<Message, Instant>): String {
    val obj = JSONObject()
    obj.put("message", it.first.javaClass.name)
    obj.put("props", messagePropsToJson(it.first.props()))
    obj.put("fields", JSONObject(JavaHelpers.autoToStringJson(it.first, setOf())))
    obj.put("time", it.second)
    obj.put("timeEpoch", it.second.epochSecond)
    return obj.toString()
}

fun messagePropsToJson(props: MessageProps): JSONObject? {
    val raw = props.toString().removePrefix("@com.jwoglom.pumpx2.pump.messages.annotations.MessageProps(").removeSuffix(")")
    val spl = Splitter.on(", ")
            .withKeyValueSeparator('=')
            .split(raw) as Map<*, *>?
    return spl?.let { JSONObject(it) }
}

fun shortPumpMessageTitle(message: Any): String {
    return message.javaClass.name.replace(
        "com.jwoglom.pumpx2.pump.messages.response.",
        ""
    )
}

fun shortHistoryLogPumpMessageTitle(message: HistoryLog): String {
    val title = message.javaClass.name.replace(
        "com.jwoglom.pumpx2.pump.messages.response.historyLog.",
        ""
    )
    if (title == "UnknownHistoryLog") {
        return "${title}_${message.typeId()}"
    }
    return title
}

fun shortPumpMessageDetail(message: Message): String {
    return message.toString().removePrefix("${message.javaClass.simpleName}[").removeSuffix("]")
}

fun verbosePumpMessage(message: Message): String {
    return message.verboseToString().replace("com.jwoglom.pumpx2.pump.messages.", "")
}

fun historyLogCacheToJson(cache: Map<Long, HistoryLog>): String {
    val arr = JSONArray()
    cache.entries.sortedBy {
        it.key
    }.forEach {
        arr.put(JSONObject(historyLogToJson(it)))
    }
    return arr.toString()
}

fun historyLogMessagePropsToJson(props: HistoryLogProps?): JSONObject? {
    if (props == null) {
        return JSONObject()
    }
    val raw = props.toString().removePrefix("@com.jwoglom.pumpx2.pump.messages.annotations.HistoryLogProps(").removeSuffix(")")
    val spl = Splitter.on(", ")
        .withKeyValueSeparator('=')
        .split(raw) as Map<*, *>?
    return spl?.let { JSONObject(it) }
}

fun historyLogToJson(it: Map.Entry<Long, HistoryLog>): String {
    val obj = JSONObject()
    obj.put("historyLog", it.value.javaClass.name)
    obj.put("props", historyLogMessagePropsToJson(it.value.props()))
    obj.put("fields", JSONObject(JavaHelpers.autoToStringJson(it.value, setOf())))
    obj.put("sequenceNum", it.key)
    obj.put("pumpTimeSec", it.value.pumpTimeSec)
    obj.put("pumpTime", it.value.pumpTimeSecInstant)
    return obj.toString()
}

@Preview(showBackground = true)
@Composable
internal fun DebugDefaultPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            Debug(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
            )
        }
    }
}
