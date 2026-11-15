package id.biz.arneta.admin.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import id.biz.arneta.admin.ui.theme.ArnetaGoldPrimary
import id.biz.arneta.admin.ui.theme.ArnetaRedDanger
import kotlin.math.roundToInt

private tailrec fun Context.findOwnerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findOwnerActivity()
    else -> null
}

/**
 * WADAH LAYAR PENUH NATIVE DINAMIS (ANTI TERTUTUP NAVIGASI 3-TOMBOL, GESTURE & KEYBOARD)
 * Menghitung posisi aktual jendela Dialog terhadap batas aman Status Bar & Navigation Bar Activity.
 */
@Composable
internal fun ArnetaFullScreenDialog(
    onDismissRequest: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.background,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        val context = LocalContext.current
        val dialogView = LocalView.current
        val density = LocalDensity.current
        val activity = remember(context) { context.findOwnerActivity() }

        var topOverlapPx by remember { mutableIntStateOf(0) }
        var bottomOverlapPx by remember { mutableIntStateOf(0) }
        var boxCoordsRef by remember { mutableStateOf<LayoutCoordinates?>(null) }

        fun recalculateSafeWindowBounds(coords: LayoutCoordinates? = boxCoordsRef) {
            val c = coords ?: return
            if (!c.isAttached) return

            val actDecor = activity?.window?.decorView ?: dialogView.rootView
            val actInsets = ViewCompat.getRootWindowInsets(actDecor)
            val dlgInsets = ViewCompat.getRootWindowInsets(dialogView)

            val sysBars = actInsets?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
            val navBars = actInsets?.getInsets(WindowInsetsCompat.Type.navigationBars())
            val statusBars = actInsets?.getInsets(WindowInsetsCompat.Type.statusBars())
            val actIme = actInsets?.getInsets(WindowInsetsCompat.Type.ime())
            val dlgIme = dlgInsets?.getInsets(WindowInsetsCompat.Type.ime())

            val topInset = maxOf(sysBars?.top ?: 0, statusBars?.top ?: 0)
            val bottomNav = maxOf(sysBars?.bottom ?: 0, navBars?.bottom ?: 0)
            val bottomIme = maxOf(actIme?.bottom ?: 0, dlgIme?.bottom ?: 0)
            val requiredBottomClearance = maxOf(bottomNav, bottomIme)

            val decorLoc = IntArray(2)
            actDecor.getLocationOnScreen(decorLoc)
            val screenTopY = decorLoc[1]
            val screenBottomY = if (actDecor.height > 0) {
                decorLoc[1] + actDecor.height
            } else {
                context.resources.displayMetrics.heightPixels
            }

            val safeTopY = screenTopY + topInset
            val safeBottomY = screenBottomY - requiredBottomClearance

            val pos = c.positionOnScreen()
            val boxTopY = pos.y.roundToInt()
            val boxBottomY = (pos.y + c.size.height).roundToInt()

            val newTop = (safeTopY - boxTopY).coerceAtLeast(0)
            val newBottom = (boxBottomY - safeBottomY).coerceAtLeast(0)

            if (topOverlapPx != newTop) topOverlapPx = newTop
            if (bottomOverlapPx != newBottom) bottomOverlapPx = newBottom
        }

        SideEffect {
            val dlgWindow = (dialogView.parent as? DialogWindowProvider)?.window
            if (dlgWindow != null) {
                dlgWindow.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                WindowCompat.setDecorFitsSystemWindows(dlgWindow, true)
                dlgWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
            recalculateSafeWindowBounds()
        }

        DisposableEffect(activity, dialogView) {
            val actDecor = activity?.window?.decorView ?: dialogView.rootView
            val listener = ViewTreeObserver.OnGlobalLayoutListener {
                recalculateSafeWindowBounds()
            }
            actDecor.viewTreeObserver.addOnGlobalLayoutListener(listener)
            dialogView.viewTreeObserver.addOnGlobalLayoutListener(listener)
            onDispose {
                runCatching { actDecor.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
                runCatching { dialogView.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
            }
        }

        val topPadDp = with(density) { topOverlapPx.toDp() }
        val bottomPadDp = with(density) { bottomOverlapPx.toDp() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background( Color(0xFF0B1120) )
                .onGloballyPositioned { coords ->
                    boxCoordsRef = coords
                    recalculateSafeWindowBounds(coords)
                }
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = containerColor,
                topBar = topBar,
                bottomBar = bottomBar,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadDp, bottom = bottomPadDp)
            ) { innerPadding ->
                content(innerPadding)
            }
        }
    }
}

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
