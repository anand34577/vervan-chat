package com.vervan.chat.ui.lock

import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.vervan.chat.security.AppLockManager
import com.vervan.chat.security.AppLockMethod
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.delay

/**
 * Full-screen gate rendered on top of the app (see [com.vervan.chat.ui.nav.VervanNavGraph]'s
 * caller in MainActivity) whenever app lock is enabled and [AppLockManager.isLocked] is true.
 * Composed over the rest of the UI rather than replacing it, so the NavHost underneath keeps
 * its back stack/state across a lock/unlock cycle instead of being torn down.
 */
@Composable
fun LockScreen(activity: FragmentActivity, appLockManager: AppLockManager, method: AppLockMethod) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var lockoutRemainingMs by remember { mutableStateOf(appLockManager.pinLockoutRemainingMs()) }
    var verifying by remember { mutableStateOf(false) }
    val pinScope = androidx.compose.runtime.rememberCoroutineScope()
    val pinFocusRequester = remember { FocusRequester() }
    val biometricAvailable = remember(method) {
        method != AppLockMethod.PIN &&
            BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }
    fun submitPin() {
        if (pin.isEmpty() || lockoutRemainingMs > 0L || verifying) return
        // PBKDF2 (120k iterations) takes a noticeable fraction of a second on budget phones;
        // keep it off the main thread.
        val attempt = pin
        verifying = true
        pinScope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { appLockManager.verifyPin(attempt) }
            verifying = false
            if (ok) {
                appLockManager.unlock()
                pin = ""
            } else {
                error = true
                lockoutRemainingMs = appLockManager.pinLockoutRemainingMs()
            }
        }
    }
    val showPinField = method != AppLockMethod.BIOMETRIC || !biometricAvailable
    // Back can't get past the lock, but it shouldn't feel dead either: it sends the app to the
    // background like Back does at the root of any other app.
    BackHandler(enabled = true) { activity.moveTaskToBack(true) }

    val biometricPrompt = remember {
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    appLockManager.unlock()
                }
                // No-op on error/failure — the user can still use the PIN field below (or
                // just retry the fingerprint icon) rather than being stuck on a dead screen.
            }
        )
    }
    // Resolved through Compose (configuration-aware) and keyed on the strings, so the negative
    // button follows showPinField instead of freezing at whatever it was on first composition.
    val biometricTitle = androidx.compose.ui.res.stringResource(R.string.lock_biometric_title)
    val biometricSubtitle = androidx.compose.ui.res.stringResource(R.string.lock_biometric_subtitle)
    val biometricNegative = androidx.compose.ui.res.stringResource(
        if (showPinField) R.string.lock_use_pin_instead else R.string.action_cancel
    )
    val promptInfo = remember(biometricTitle, biometricSubtitle, biometricNegative) {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(biometricTitle)
            .setSubtitle(biometricSubtitle)
            .setNegativeButtonText(biometricNegative)
            .build()
    }
    LaunchedEffect(biometricAvailable) {
        if (biometricAvailable) biometricPrompt.authenticate(promptInfo)
    }
    LaunchedEffect(showPinField) {
        if (showPinField) pinFocusRequester.requestFocus()
    }
    LaunchedEffect(lockoutRemainingMs) {
        if (lockoutRemainingMs > 0) {
            delay(1_000)
            lockoutRemainingMs = appLockManager.pinLockoutRemainingMs()
            if (lockoutRemainingMs == 0L) error = false
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            // Consumes every touch so nothing underneath is reachable while locked.
            .pointerInput(Unit) {},
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
                .imePadding().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 360.dp).padding(Space.lg),
                shape = com.vervan.chat.ui.theme.VervanExtraShapes.hero,
                color = com.vervan.chat.ui.theme.SurfaceRole.Overlay.containerColor(),
                border = com.vervan.chat.ui.theme.SurfaceRole.Overlay.border(),
                shadowElevation = com.vervan.chat.ui.theme.SurfaceRole.Overlay.shadowElevation
            ) {
              Column(
                Modifier.fillMaxWidth().padding(Space.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.lg)
              ) {
                // Brand-gradient lock mark — the same identity mark the nav dock and chat avatar
                // carry, so the gate reads as Vervan rather than a generic dialog.
                Box(
                    Modifier
                        .size(64.dp)
                        .background(
                            com.vervan.chat.ui.theme.vervanBrandFill(),
                            androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(stringResource(R.string.lock_vervan_chat_is_locked), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.lock_your_data_stays_on_this),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (biometricAvailable) {
                    OutlinedButton(onClick = { biometricPrompt.authenticate(promptInfo) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null)
                        Text(stringResource(R.string.lock_unlock_with_biometrics), modifier = Modifier.padding(start = Space.sm))
                    }
                }
                if (showPinField) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 12) { pin = it.filter(Char::isDigit); error = false } },
                        label = { Text(stringResource(R.string.lock_pin)) },
                        isError = error,
                        supportingText = when {
                            lockoutRemainingMs > 0 -> {
                                {
                                    val seconds = ((lockoutRemainingMs + 999) / 1_000).toInt()
                                    Text(androidx.compose.ui.res.pluralStringResource(R.plurals.lock_too_many_attempts, seconds, seconds))
                                }
                            }
                            error -> ({ Text(stringResource(R.string.lock_incorrect_pin)) })
                            else -> null
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submitPin() }),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(pinFocusRequester)
                    )
                    Button(
                        onClick = ::submitPin,
                        enabled = pin.isNotEmpty() && lockoutRemainingMs == 0L && !verifying,
                        modifier = Modifier.fillMaxWidth()
                    ) { if (verifying) {
                            androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.lock_unlock))
                        }
                    }
                }
              }
            }
        }
    }
}
