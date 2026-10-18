package com.intelliq.rifatplex.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import com.intelliq.rifatplex.data.Backup
import com.intelliq.rifatplex.data.BackupResult
import com.intelliq.rifatplex.data.CloudSync
import com.intelliq.rifatplex.data.CloudUser
import com.intelliq.rifatplex.data.RestoreResult
import com.intelliq.rifatplex.data.SignInResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.intelliq.rifatplex.R
import com.intelliq.rifatplex.ui.theme.IQ
import com.intelliq.rifatplex.ui.theme.IQD
import com.intelliq.rifatplex.ui.theme.PIcons

/* Developer Info + Coming Soon dialogs (website: #devInfoOverlay, #comingSoonOverlay). */

private const val DEV_NAME = "RIFAT HOSSAIN"
private const val DEV_FACEBOOK = "https://www.facebook.com/share/1DNatqf5gi/"
private const val DEV_INSTAGRAM = "https://www.instagram.com/rifatplex?stkn=MWhyaHE3bW90cXNkMQ=="
private const val DEV_WHATSAPP = "https://wa.me/8801838301818"
private const val DEV_PHONE = "+8801838301818"
private const val DEV_EMAIL = "rifatplex@gmail.com"

private object SocialIcons {
    val Facebook: ImageVector by lazy {
        ImageVector.Builder("Facebook", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(addPathNodes("M22 12.06C22 6.505 17.523 2 12 2S2 6.505 2 12.06c0 5.02 3.657 9.184 8.438 9.94v-7.03H7.898v-2.91h2.54V9.845c0-2.507 1.492-3.89 3.777-3.89 1.094 0 2.238.195 2.238.195v2.46h-1.26c-1.243 0-1.63.771-1.63 1.562v1.878h2.773l-.443 2.91h-2.33V22c4.78-.756 8.437-4.92 8.437-9.94Z"), fill = SolidColor(Color.Black))
        }.build()
    }
    val WhatsApp: ImageVector by lazy {
        ImageVector.Builder("WhatsApp", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(addPathNodes("M12.04 2C6.58 2 2.13 6.45 2.13 11.91c0 1.75.46 3.39 1.26 4.81L2 22l5.42-1.42a9.87 9.87 0 0 0 4.62 1.17h.01c5.46 0 9.9-4.45 9.9-9.91C21.95 6.45 17.5 2 12.04 2Zm0 18.06h-.01a8.18 8.18 0 0 1-4.17-1.14l-.3-.18-3.11.82.83-3.03-.2-.31a8.08 8.08 0 0 1-1.26-4.31c0-4.48 3.65-8.13 8.14-8.13 2.18 0 4.22.85 5.76 2.39a8.07 8.07 0 0 1 2.38 5.75c0 4.48-3.65 8.14-8.06 8.14Zm4.46-6.09c-.24-.12-1.44-.71-1.66-.79-.22-.08-.39-.12-.55.12-.16.24-.63.79-.78.95-.14.16-.29.18-.53.06-.24-.12-1.02-.38-1.94-1.2-.72-.64-1.2-1.44-1.35-1.68-.14-.24-.02-.37.11-.49.11-.11.24-.29.37-.43.12-.14.16-.24.24-.4.08-.16.04-.31-.02-.43-.06-.12-.55-1.32-.75-1.81-.2-.48-.4-.41-.55-.42h-.47c-.16 0-.43.06-.65.31-.22.24-.86.84-.86 2.05 0 1.21.88 2.38 1 2.54.12.16 1.73 2.65 4.2 3.71.59.25 1.05.4 1.41.52.59.19 1.13.16 1.55.1.47-.07 1.44-.59 1.65-1.15.2-.57.2-1.05.14-1.15-.06-.1-.22-.16-.46-.28Z"), fill = SolidColor(Color.Black))
        }.build()
    }
    val Instagram: ImageVector by lazy {
        ImageVector.Builder("Instagram", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                addPathNodes("M8,3H16A5,5 0 0 1 21,8V16A5,5 0 0 1 16,21H8A5,5 0 0 1 3,16V8A5,5 0 0 1 8,3Z M12,8A4,4 0 1 1 12,16A4,4 0 1 1 12,8Z"),
                fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round,
            )
            addPath(addPathNodes("M17.2,5.7A1.1,1.1 0 1 1 17.2,7.9A1.1,1.1 0 1 1 17.2,5.7Z"), fill = SolidColor(Color.Black))
        }.build()
    }
    val Call: ImageVector by lazy {
        ImageVector.Builder("Call", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                addPathNodes("M6.62,10.79c1.44,2.83 3.76,5.14 6.59,6.59l2.2,-2.2c0.27,-0.27 0.67,-0.36 1.02,-0.24 1.12,0.37 2.33,0.57 3.57,0.57 0.55,0 1,0.45 1,1V20c0,0.55 -0.45,1 -1,1 -9.39,0 -17,-7.61 -17,-17 0,-0.55 0.45,-1 1,-1h3.5c0.55,0 1,0.45 1,1 0,1.25 0.2,2.45 0.57,3.57 0.11,0.35 0.03,0.74 -0.25,1.02l-2.2,2.2z"),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }
    val Mail: ImageVector by lazy {
        ImageVector.Builder("Mail", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                addPathNodes("M20,4H4C2.9,4 2,4.9 2,6v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM20,8l-8,5 -8,-5V6l8,5 8,-5v2z"),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }
}

@Composable
internal fun DialogHead(icon: ImageVector, title: String, onClose: () -> Unit) {
    val c = IQ.c
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = c.primary)
            Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
        }
        SquareBtn(40.dp, onClose) { Icon(PIcons.Close, null, Modifier.size(16.dp), tint = c.textSecondary) }
    }
}

@Composable
private fun SocialBtn(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    val c = IQ.c
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(c.panel)
            .border(1.dp, c.border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(20.dp), tint = tint)
    }
}

@Composable
fun DevInfoDialog(onDismiss: () -> Unit, onIntro: (() -> Unit)? = null) {
    val c = IQ.c
    val ctx = LocalContext.current
    val shape = IQD.dialog
    val open: (String) -> Unit = { url ->
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .shadow(8.dp, shape)
                .clip(shape)
                .background(c.panel)
                .border(1.dp, c.glassBorder, shape)
        ) {
            DialogHead(PIcons.AutoAwesome, tr("dev.title"), onDismiss)
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    tr("dev.by").uppercase(),
                    color = c.textFaint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp, fontFamily = uiFont(),
                )
                Box(Modifier.size(10.dp))
                Image(
                    painterResource(R.drawable.dev_avatar), DEV_NAME,
                    Modifier.size(76.dp).clip(CircleShape).border(3.dp, c.primarySoft, CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.size(10.dp))
                Text(DEV_NAME, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = uiFont())
                Box(Modifier.size(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SocialBtn(SocialIcons.Facebook, "Facebook", Color(0xFF1877F2)) { open(DEV_FACEBOOK) }
                    SocialBtn(SocialIcons.Instagram, "Instagram", Color(0xFFE1306C)) { open(DEV_INSTAGRAM) }
                    SocialBtn(SocialIcons.WhatsApp, "WhatsApp", Color(0xFF25D366)) { open(DEV_WHATSAPP) }
                    SocialBtn(SocialIcons.Call, "Call", c.primary) { open("tel:$DEV_PHONE") }
                    SocialBtn(SocialIcons.Mail, "Email", c.primary) { open("mailto:$DEV_EMAIL") }
                }
                if (onIntro != null) {
                    Box(Modifier.size(18.dp))
                    IQButton(tr("ob.replay"), BtnKind.Secondary, Modifier.fillMaxWidth(), onIntro)
                }
            }
        }
    }
}

/** Round profile photo (loaded from the Google account URL); the first letter of the name until it arrives or when offline. */
@Composable
fun Avatar(user: CloudUser, size: Dp) {
    val c = IQ.c
    var bmp by remember(user.photoUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(user.photoUrl) {
        val url = user.photoUrl ?: return@LaunchedEffect
        bmp = withContext(Dispatchers.IO) {
            try {
                java.net.URL(url).openStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(Modifier.size(size).clip(CircleShape).background(c.primarySoft), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(b, null, Modifier.size(size), contentScale = ContentScale.Crop)
        } else {
            val letter = (user.name.ifEmpty { user.email }).firstOrNull()?.uppercase() ?: "?"
            Text(letter, color = c.primaryInk, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
        }
    }
}

/** Google sign-in / account card (website `#googleAuthOverlay`). */
@Composable
fun AccountDialog(
    cloud: CloudSync,
    activity: Activity,
    onDismiss: () -> Unit,
    onToast: (String, Boolean) -> Unit,
) {
    val c = IQ.c
    val shape = IQD.dialog
    val scope = rememberCoroutineScope()
    val lang = LocalLang.current
    val user = cloud.user
    val ctx = LocalContext.current
    var hint by remember { mutableStateOf<String?>(null) }
    var hintError by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }

    // Restore: the user picks the backup .zip saved on this phone. It is MERGED into the phone's data.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            working = true
            scope.launch {
                val r = withContext(Dispatchers.IO) { Backup.restoreFrom(ctx, uri) }
                working = false
                when (r) {
                    is RestoreResult.Done -> onToast(
                        trKey("restore.done", lang).replace("{p}", r.papers.toString()).replace("{f}", r.folders.toString())
                            .replace("{k}", r.keys.toString()),
                        false,
                    )
                    RestoreResult.Invalid -> onToast(trKey("restore.invalid", lang), true)
                    RestoreResult.TooBig -> onToast(trKey("restore.tooBig", lang), true)
                    RestoreResult.Failed -> onToast(trKey("restore.failed", lang), true)
                }
            }
        }
    }

    // Backup: build the zip and save it on this phone (Downloads/IntelliQ). Nothing is sent anywhere.
    fun saveBackup(email: String) {
        if (working) return
        working = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { Backup.create(ctx, email, asJson = true) }
            when (r) {
                BackupResult.Empty -> onToast(trKey("backup.empty", lang), true)
                BackupResult.Failed -> onToast(trKey("backup.failed", lang), true)
                is BackupResult.Ready -> {
                    try {
                        val bytes = withContext(Dispatchers.IO) { r.file.readBytes() }
                        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US).format(java.util.Date())
                        val name = "intelliq_backup_$stamp.json"
                        val saved = PdfExport.deliver(activity, ExportResult(name, "application/json", bytes, false), "Downloaded Database")
                        com.intelliq.rifatplex.data.BackupReminderStore.get(ctx).markBackedUp()
                        if (saved) onToast(trKey("backup.saved", lang), false)
                    } catch (e: Exception) {
                        onToast(trKey("backup.failed", lang), true)
                    }
                }
            }
            working = false
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .shadow(8.dp, shape)
                .clip(shape)
                .background(c.panel)
                .border(1.dp, c.glassBorder, shape)
        ) {
            DialogHead(PIcons.AccountCircle, tr("account.title"), onDismiss)
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (user == null) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(c.primarySoft), contentAlignment = Alignment.Center) {
                        Icon(PIcons.AccountCircle, null, Modifier.size(28.dp), tint = c.primary)
                    }
                    if (tr("account.signInMsg").isNotBlank()) {
                        Text(
                            tr("account.signInMsg"), color = c.textSecondary, fontSize = 14.sp, lineHeight = 23.sp,
                            textAlign = TextAlign.Center, fontFamily = uiFont(),
                        )
                    }
                    Text(
                        tr("account.appOnly"), color = c.textFaint, fontSize = 13.sp, lineHeight = 20.sp,
                        textAlign = TextAlign.Center, fontFamily = uiFont(),
                    )
                    hint?.let {
                        Text(
                            it, color = if (hintError) c.danger else c.textSecondary, fontSize = 13.sp,
                            textAlign = TextAlign.Center, fontFamily = uiFont(),
                        )
                    }
                    IQButton(
                        if (cloud.busy) tr("account.signingIn") else tr("account.signInBtn"),
                        BtnKind.Primary, Modifier.fillMaxWidth(),
                    ) {
                        if (cloud.busy) return@IQButton
                        hint = null
                        scope.launch {
                            when (cloud.signIn(activity)) {
                                SignInResult.OK -> {
                                    onToast(trKey("account.signedInToast", lang), false)
                                    onDismiss()
                                }
                                SignInResult.CANCELLED -> Unit
                                SignInResult.NOT_CONFIGURED -> { hint = trKey("account.notConfigured", lang); hintError = true }
                                SignInResult.ERROR -> { hint = trKey("account.error", lang); hintError = true }
                            }
                        }
                    }
                } else {
                    Avatar(user, 64.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (user.name.isNotEmpty()) {
                            Text(user.name, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(), textAlign = TextAlign.Center)
                        }
                        Text(user.email, color = c.textSecondary, fontSize = 14.sp, fontFamily = uiFont(), textAlign = TextAlign.Center)
                    }
                    Text(tr("account.syncOn"), color = c.successInk, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
                    Text(tr("backup.title"), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
                    IQButton(
                        if (working) tr("backup.working") else tr("backup.phoneBtn"),
                        BtnKind.Secondary, Modifier.fillMaxWidth(),
                    ) { saveBackup(user.email) }
                    IQButton(tr("backup.restoreBtn"), BtnKind.Secondary, Modifier.fillMaxWidth()) {
                        if (!working) picker.launch(arrayOf("*/*"))
                    }
                    Text(
                        tr("account.signOutNote"), color = c.textFaint, fontSize = 13.sp, lineHeight = 20.sp,
                        textAlign = TextAlign.Center, fontFamily = uiFont(),
                    )
                    IQButton(tr("account.signOut"), BtnKind.Secondary, Modifier.fillMaxWidth()) {
                        scope.launch {
                            cloud.signOut(activity)
                            onToast(trKey("account.signedOutToast", lang), false)
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}
