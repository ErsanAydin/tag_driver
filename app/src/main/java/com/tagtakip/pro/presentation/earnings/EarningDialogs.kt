package com.tagtakip.pro.presentation.earnings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tagtakip.pro.R
import com.tagtakip.pro.core.util.toTlCurrency
import com.tagtakip.pro.domain.model.Earning
import com.tagtakip.pro.domain.model.EarningType
import com.tagtakip.pro.presentation.theme.EarningGreen
import com.tagtakip.pro.presentation.theme.ErrorRed
import java.util.Locale
import kotlin.math.abs

/** Kullanıcının yazdığı tutarı (virgül/nokta) pozitif Double'a çevirir; geçersizse null. */
fun parseAmount(text: String): Double? =
    text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 && !it.isInfinite() }

/** Tutar alanı için yalnızca rakam ve tek ondalık ayıracına izin verir. */
fun sanitizeAmountInput(input: String): String {
    val filtered = input.filter { it.isDigit() || it == ',' || it == '.' }
    val sepIndex = filtered.indexOfFirst { it == ',' || it == '.' }
    return if (sepIndex < 0) filtered
    else filtered.substring(0, sepIndex + 1) + filtered.substring(sepIndex + 1).filter { it.isDigit() }.take(2)
}

/** Kaydı listede göstermek için başlık (açıklama yoksa tür adı). */
@Composable
fun Earning.displayTitle(): String =
    description ?: stringResource(if (type == EarningType.EXPENSE) R.string.earning_type_expense else R.string.earning_type_income)

/** Gelir yeşil "+", gider kırmızı "-" ile gösterilir. */
fun Earning.signedAmountText(): String =
    (if (type == EarningType.EXPENSE) "-" else "+") + abs(amount).toTlCurrency()

fun Earning.amountColor() = if (type == EarningType.EXPENSE) ErrorRed else EarningGreen

/** Yeni gelir/gider ekleme dialog'u. [onConfirm] pozitif tutar alır; işaret use case'de türe göre ayarlanır. */
@Composable
fun AddEarningDialog(
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, type: EarningType, description: String?) -> Unit
) {
    EarningFormDialog(
        title = stringResource(R.string.earning_add_title),
        confirmText = stringResource(R.string.save),
        initialType = EarningType.INCOME,
        initialAmount = "",
        initialDescription = "",
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        onDelete = null
    )
}

/** Mevcut kaydı düzenleme dialog'u; değerler önceden doldurulur, silme seçeneği içerir. */
@Composable
fun EditEarningDialog(
    earning: Earning,
    onDismiss: () -> Unit,
    onUpdate: (amount: Double, type: EarningType, description: String?) -> Unit,
    onDelete: () -> Unit
) {
    EarningFormDialog(
        title = stringResource(R.string.earning_edit_title),
        confirmText = stringResource(R.string.update),
        initialType = earning.type,
        initialAmount = String.format(Locale.US, "%.2f", abs(earning.amount)),
        initialDescription = earning.description.orEmpty(),
        onDismiss = onDismiss,
        onConfirm = onUpdate,
        onDelete = onDelete
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EarningFormDialog(
    title: String,
    confirmText: String,
    initialType: EarningType,
    initialAmount: String,
    initialDescription: String,
    onDismiss: () -> Unit,
    onConfirm: (Double, EarningType, String?) -> Unit,
    onDelete: (() -> Unit)?
) {
    var type by rememberSaveable { mutableStateOf(initialType) }
    var amountText by rememberSaveable { mutableStateOf(initialAmount) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val amount = parseAmount(amountText)
    val types = listOf(EarningType.INCOME to R.string.earning_type_income, EarningType.EXPENSE to R.string.earning_type_expense)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.earning_type_label), style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    types.forEachIndexed { index, (t, labelRes) ->
                        SegmentedButton(
                            selected = type == t,
                            onClick = { type = t },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size)
                        ) { Text(stringResource(labelRes)) }
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = sanitizeAmountInput(it) },
                    label = { Text(stringResource(R.string.amount_hint)) },
                    suffix = { Text("₺") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = {
                        if (type == EarningType.EXPENSE && amount != null) {
                            Text(stringResource(R.string.earning_expense_preview, (-amount).toTlCurrency()), color = ErrorRed)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(MAX_DESCRIPTION_LENGTH) },
                    label = { Text(stringResource(R.string.description_optional)) },
                    placeholder = { Text(stringResource(R.string.earning_description_placeholder)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null,
                onClick = { amount?.let { onConfirm(it, type, description.trim().ifEmpty { null }) } }
            ) { Text(confirmText) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onDelete != null) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Text(stringResource(R.string.delete))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.earning_delete_confirm_title)) },
            text = { Text(stringResource(R.string.earning_delete_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

const val MAX_DESCRIPTION_LENGTH = 200
