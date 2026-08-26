package com.vervan.chat.ui.lock

import androidx.compose.ui.res.stringResource
import com.vervan.chat.R
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import com.vervan.chat.ui.common.VervanButton as Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.common.VervanOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
    var lockoutRemainingMs by remember { mutableLongStateOf(appLockManager.pinLockoutRemainingMs()) }
    val pinFocusRequester = remember { FocusRequester() }
    val hasPin = appLockManager.hasPin()
    val biometricAvailable = remember(method) {
        method != AppLockMethod.PIN &&
            BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }
    // Recovery for a legacy/interrupted configuration that has app lock enabled but no usable
    // app PIN (for example, biometric enrollment was removed after setup). The Android device
    // credential remains a secure owner check and avoids permanently locking the user out.
    val useDeviceCredentialFallback = !hasPin && !biometricAvailable
    val allowedAuthenticators = if (useDeviceCredentialFallback) {
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
        BiometricManager.Authenticators.BIOMETRIC_WEAK
    }
    val systemAuthenticationAvailable = remember(method, allowedAuthenticators) {
        (method != AppLockMethod.PIN || useDeviceCredentialFallback) &&
            BiometricManager.from(context).canAuthenticate(allowedAuthenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val showPinField = hasPin && (method != AppLockMethod.BIOMETRIC || !systemAuthenticationAvailable)
    val lockoutMessage = if (lockoutRemainingMs > 0) {
        val seconds = ((lockoutRemainingMs + 999) / 1_000).toInt()
        context.resources.getQuantityString(R.plurals.lock_too_many_attempts, seconds, seconds)
    } else null
    BackHandler(enabled = true) { }

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
    val promptInfo = remember(allowedAuthenticators, showPinField) {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Vervan Chat")
            .setSubtitle("Confirm it's you to continue")
            .setAllowedAuthenticators(allowedAuthenticators)
            .apply {
                // Android forbids a custom negative button when device credential is one of the
                // allowed authenticators; the system supplies its own fallback affordance.
                if (!useDeviceCredentialFallback) {
                    setNegativeButtonText(if (showPinField) "Use PIN instead" else "Cancel")
                }
            }
            .build()
    }
    LaunchedEffect(systemAuthenticationAvailable, promptInfo) {
        if (systemAuthenticationAvailable) biometricPrompt.authenticate(promptInfo)
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
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
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
                            com.vervan.chat.ui.theme.vervanBrandGradient(),
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
                Text(stringResource(R.string.ui_lockscreen_139_vervan_chat_is_locked), style = MaterialTheme.typography.titleLarge)
                Text(
                    "Your data stays on this device — unlock to continue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (systemAuthenticationAvailable) {
                    OutlinedButton(onClick = { biometricPrompt.authenticate(promptInfo) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null)
                        Text(
                            if (useDeviceCredentialFallback) "Unlock with device security"
                            else stringResource(R.string.ui_lockscreen_148_unlock_with_biometrics),
                            modifier = Modifier.padding(start = Space.sm)
                        )
                    }
                }
                if (showPinField) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 12) { pin = it.filter(Char::isDigit); error = false } },
                        label = { Text(stringResource(R.string.ui_lockscreen_155_pin)) },
                        isError = error,
                        supportingText = when {
                            lockoutMessage != null -> { { Text(lockoutMessage) } }
                            error -> ({ Text(stringResource(R.string.ui_lockscreen_161_incorrect_pin)) })
                            else -> null
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(pinFocusRequester)
                    )
                    Button(
                        onClick = {
                            if (appLockManager.verifyPin(pin)) {
                                appLockManager.unlock()
                                pin = ""
                            } else {
                                error = true
                                lockoutRemainingMs = appLockManager.pinLockoutRemainingMs()
                            }
                        },
                        enabled = pin.isNotEmpty() && lockoutRemainingMs == 0L,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                    ) { Text(stringResource(R.string.ui_lockscreen_182_unlock)) }
                }
              }
            }
        }
    }
}
