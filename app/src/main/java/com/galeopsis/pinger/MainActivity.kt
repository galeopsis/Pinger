package com.galeopsis.pinger

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galeopsis.pinger.ui.theme.PingerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.exitProcess

var ipAddressToCheck = "google.com"
var portToCheck = 80

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Read the host settings from SharedPreferences
        val sharedPref = getSharedPreferences("MyHostPrefs", Context.MODE_PRIVATE)
        ipAddressToCheck = sharedPref.getString("IP_ADDRESS", "google.com") ?: "google.com"
        portToCheck = sharedPref.getInt("PORT", 80)

        setContent {
            PingerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MyButton()
                    MySettingsButton()
                    ExitButton()
                }
            }
        }
    }
}

private suspend fun checkIpAddress(ipAddress: String, port: Int): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
        val sock = Socket()
        sock.connect(InetSocketAddress(ipAddress, port), 1500)
        sock.close()
        true
    } catch (e: IOException) {
        false
    }
}

@Composable
fun MySettingsButton() {
    // Use a mutable state to control the visibility of the settings dialog
    val showSettingsDialog = remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 16.dp, top = 16.dp)
            .wrapContentSize(Alignment.TopEnd)
    ) {
        IconButton(
            onClick = {
                showSettingsDialog.value = true
            }
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.settings),
                tint = Color(0xFF499C54)
            )
        }

        // Show the settings dialog based on the value of the mutable state
        if (showSettingsDialog.value) {
            ShowSettingsDialog(onDismiss = {
                showSettingsDialog.value = false
            })
        }
    }
}

//w/o checks
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ShowSettingsDialog(onDismiss: () -> Unit) {
    val dialogData = remember { mutableStateOf(Pair(ipAddressToCheck, portToCheck)) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = {
            onDismiss()
        },
        title = {
            Text(stringResource(R.string.settings))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = dialogData.value.first,
                    onValueChange = { dialogData.value = dialogData.value.copy(first = it) },
                    label = { Text(stringResource(R.string.ip_address_label)) },
                    singleLine = true,
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = if (dialogData.value.second == 0) "" else dialogData.value.second.toString(),
                    onValueChange = {
                        dialogData.value = dialogData.value.copy(second = it.toIntOrNull() ?: 0)
                    },
                    label = { Text(stringResource(R.string.port_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number), // Set the numeric keyboard
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    ipAddressToCheck = dialogData.value.first
                    if (dialogData.value.second in 1..65535) {
                        portToCheck = dialogData.value.second
                    } else {
                        showToast(context, getStringResource(context, R.string.invalid_port_error))
                    }

                    // Save the host settings to SharedPreferences
                    saveHostSettingsToSharedPreferences(context, ipAddressToCheck, portToCheck)

                    // Close the dialog
                    onDismiss()
                    keyboardController?.hide()
                }
            ) {
                Text(stringResource(R.string.save_button))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    // Close the dialog without saving any changes
                    onDismiss()
                    keyboardController?.hide()
                }
            ) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )
}

@Composable
fun MyButton() {
    var isAvailable by remember { mutableStateOf(false) }
    var buttonColor by remember { mutableStateOf(Color.Yellow) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Use Column to arrange the text and button vertically
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        // Display the current host and port above the button
        Text(
            text = "$ipAddressToCheck: $portToCheck",
            style = TextStyle(
                color = buttonColor,
                fontSize = 16.sp,
                background = Color(0xFF00668B)
            ),
            modifier = Modifier
                .background(
                    color = Color(0xFF00668B),
                    shape = RectangleShape
                )
                .padding(horizontal = 8.dp, vertical = 4.dp) // Внутренние отступы (paddings) внутри формы
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        isAvailable = checkIpAddress(ipAddressToCheck, portToCheck)
                        Log.d("testComposer", isAvailable.toString())
                        if (isAvailable) {
                            showToast(
                                context,
                                getStringResource(context, R.string.available)
                            )
                        } else {
                            showToast(
                                context,
                                getStringResource(context, R.string.not_available)
                            )
                        }
                        buttonColor = if (isAvailable) Color.Green else Color.Red
                    }
                }
            ) {
                Text(
                    stringResource(R.string.check),
                    color = Color.White
                )
            }
            LaunchedEffect(buttonColor) {
                delay(1500L) // 1.5 seconds delay
                buttonColor = Color.Yellow // Revert color back to the original state
            }
        }
    }
}



fun saveHostSettingsToSharedPreferences(context: Context, ipAddress: String, port: Int) {
    val sharedPref = context.getSharedPreferences("MyHostPrefs", Context.MODE_PRIVATE)
    with(sharedPref.edit()) {
        putString("IP_ADDRESS", ipAddress)
        putInt("PORT", port)
        apply()
    }
}

fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

fun getStringResource(ctx: Context, @StringRes id: Int): String {
    return ctx.getString(id)
}

@Composable
fun ExitButton() {
    // Use a mutable state to control the visibility of the settings dialog

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 16.dp, bottom = 16.dp)
            .wrapContentSize(Alignment.BottomEnd)
    ) {
        IconButton(
            onClick = {
                exitProcess(0)
            }
        ) {
            Icon(
                imageVector = Icons.Default.ExitToApp,
                contentDescription = stringResource(R.string.exit),
                tint = Color(0xFF499C54)
            )
        }
    }
}

@Preview(showBackground = false)
@Composable
fun DefaultPreview() {
    PingerTheme {
        // we are passing our composable
        // function to display its preview.
        MyButton()
        MySettingsButton()
        ExitButton()
    }
}
