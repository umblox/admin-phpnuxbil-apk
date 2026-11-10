package id.biz.arneta.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.biz.arneta.admin.ui.theme.ArnetaGoldPrimary
import id.biz.arneta.admin.ui.theme.ArnetaRedDanger

/**
 * DIALOG KONFIRMASI HAPUS REUSABLE BERTEMA ARNETA
 */
@Composable
internal fun ConfirmDeleteArnetaDialog(
    title: String,
    subtitle: String,
    description: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141B2D),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ArnetaRedDanger.copy(alpha = 0.2f))
                        .border(1.dp, ArnetaRedDanger, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteForever,
                        contentDescription = null,
                        tint = ArnetaRedDanger,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.5.sp,
                        color = Color.White
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.5.sp,
                        color = ArnetaGoldPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Text(
                text = description,
                fontSize = 12.5.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArnetaRedDanger,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ya, Hapus", fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Batal", fontSize = 12.5.sp)
            }
        }
    )
}
