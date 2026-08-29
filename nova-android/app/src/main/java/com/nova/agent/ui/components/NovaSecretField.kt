package com.nova.agent.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Gizli değer alanı — web'deki `SecretField` ile aynı davranış:
 * göz düğmesi maskeyi açar, kopyala düğmesi panoya alır.
 *
 * Maske kalıcı açık kalmasın diye [revealTimeoutMillis] sonunda kendiliğinden
 * kapanır; omuz üstünden okunma riskini sınırlar. 0 verilirse kapanmaz.
 *
 * Kopyalama geri bildirimi kısa süreli bir ikon değişimiyle verilir; Android 13+
 * zaten sistem düzeyinde "panoya kopyalandı" bildirimi gösterdiği için ayrıca
 * Snackbar açılmaz (çift bildirim olurdu).
 */
@Composable
fun NovaSecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    contentLabel: String = label,
    supportingText: String? = null,
    readOnly: Boolean = false,
    revealTimeoutMillis: Long = 30_000L,
    testTag: String = "secret_field",
    clipboard: ClipboardManager = LocalClipboardManager.current,
    context: android.content.Context = androidx.compose.ui.platform.LocalContext.current,
) {
    var revealed by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val hasValue = value.isNotEmpty()

    // Değer temizlenirse maskeyi tekrar kapat.
    LaunchedEffect(hasValue) { if (!hasValue) revealed = false }

    // Otomatik gizleme.
    LaunchedEffect(revealed, revealTimeoutMillis) {
        if (revealed && revealTimeoutMillis > 0) {
            delay(revealTimeoutMillis)
            revealed = false
        }
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .testTag(testTag)
            .semantics {
                contentDescription = contentLabel
                // Maske açıkken password() bildirmek ekran okuyucuyu yanıltır.
                if (!revealed) password()
            },
        label = { Text(label) },
        // Not: `supportingText?.let { { Text(it) } }` yazılmamalı — iç içe iki
        // lambdada `it` belirsizleşir ve @Composable tipi `let` üzerinden
        // çıkarsanmaz. Açık if/else güvenli.
        supportingText = if (supportingText != null) {
            { Text(supportingText) }
        } else {
            null
        },
        singleLine = true,
        readOnly = readOnly,
        visualTransformation =
            if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            Row {
                IconButton(
                    onClick = { revealed = !revealed },
                    enabled = hasValue,
                    modifier = Modifier.testTag(testTag + "_reveal"),
                ) {
                    Icon(
                        imageVector =
                            if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (revealed) "Gizle" else "Göster",
                    )
                }
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(value))
                        // Android 13+ pano önizlemesi anahtarı düz metin
                        // gösteriyordu; maskelemenin tüm anlamı kaçıyordu.
                        markClipboardSensitive(context, "NOVA", value)
                        copied = true
                    },
                    enabled = hasValue,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .testTag(testTag + "_copy"),
                ) {
                    Icon(
                        imageVector =
                            if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = "Panoya kopyala",
                        tint = if (copied) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        },
    )
}
