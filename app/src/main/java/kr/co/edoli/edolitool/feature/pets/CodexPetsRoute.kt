package kr.co.edoli.edolitool.feature.pets

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodexPetsRoute(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pets by remember { mutableStateOf(CodexPetRepository.installedPets(context)) }
    var isImporting by remember { mutableStateOf(false) }
    var activePetId by remember { mutableStateOf(PetOverlayController.activePetId(context)) }
    var petSizeDp by remember { mutableStateOf(PetOverlayController.petSizeDp(context).toFloat()) }
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isImporting = true
            val result = withContext(Dispatchers.IO) {
                runCatching { CodexPetRepository.import(context, uri) }
            }
            isImporting = false
            result.onSuccess { pet ->
                pets = CodexPetRepository.installedPets(context)
                snackbarHostState.showSnackbar("${pet.displayName} 펫을 가져왔습니다.")
            }.onFailure { error ->
                snackbarHostState.showSnackbar(error.message ?: "펫을 가져올 수 없습니다.")
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Codex 펫", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ImportCard(
                    isImporting = isImporting,
                    onImport = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                )
            }
            item {
                PetSizeCard(
                    sizeDp = petSizeDp,
                    onSizeChange = { petSizeDp = it },
                    onSizeChangeFinished = {
                        PetOverlayController.updatePetSize(context, petSizeDp.roundToInt())
                    },
                    onReset = {
                        petSizeDp = PetOverlayController.defaultPetSizeDp.toFloat()
                        PetOverlayController.updatePetSize(context, PetOverlayController.defaultPetSizeDp)
                    },
                )
            }
            if (pets.isEmpty()) {
                item {
                    Text(
                        text = "아직 가져온 펫이 없습니다.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item {
                    Text("설치된 펫", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                items(pets, key = { it.id }) { pet ->
                    PetCard(
                        pet = pet,
                        isActive = activePetId == pet.id,
                        onShow = {
                            if (PetOverlayController.canDrawOverApps(context)) {
                                PetOverlayController.show(context, pet.id)
                                activePetId = pet.id
                            } else {
                                PetOverlayController.openOverlayPermission(context)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "다른 앱 위에 표시 권한을 허용한 뒤 다시 눌러 주세요.",
                                    )
                                }
                            }
                        },
                        onHide = {
                            PetOverlayController.hide(context)
                            activePetId = null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportCard(isImporting: Boolean, onImport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("펫 ZIP 가져오기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Button(onClick = onImport, enabled = !isImporting) {
                Icon(Icons.Rounded.UploadFile, contentDescription = null)
                Text(if (isImporting) "가져오는 중…" else "ZIP 파일 선택", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun PetSizeCard(
    sizeDp: Float,
    onSizeChange: (Float) -> Unit,
    onSizeChangeFinished: () -> Unit,
    onReset: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("펫 크기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "${sizeDp.roundToInt()}dp",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = sizeDp,
                onValueChange = onSizeChange,
                onValueChangeFinished = onSizeChangeFinished,
                valueRange = PetOverlayController.minPetSizeDp.toFloat()..PetOverlayController.maxPetSizeDp.toFloat(),
            )
            Button(onClick = onReset) {
                Text("기본 크기로 재설정")
            }
        }
    }
}

@Composable
private fun PetCard(
    pet: CodexPet,
    isActive: Boolean,
    onShow: () -> Unit,
    onHide: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PetIdlePreview(pet)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(pet.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (pet.description.isNotBlank()) Text(pet.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("ID: ${pet.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = if (isActive) onHide else onShow) {
                    Text(if (isActive) "화면에서 숨기기" else "화면에 띄우기")
                }
            }
        }
    }
}

@Composable
private fun PetIdlePreview(pet: CodexPet) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, pet.id) {
        value = withContext(Dispatchers.IO) {
            CodexPetRepository.findPetFiles(context, pet.id)
                ?.spritesheet
                ?.let { BitmapFactory.decodeFile(it.path) }
        }
    }
    var frameIndex by remember(pet.id) { mutableStateOf(0) }
    LaunchedEffect(pet.id) {
        while (true) {
            delay(idleFrameDurations[frameIndex].toLong())
            frameIndex = (frameIndex + 1) % idleFrameDurations.size
        }
    }

    Canvas(modifier = Modifier.size(width = 80.dp, height = 88.dp)) {
        bitmap?.asImageBitmap()?.let { image ->
            drawImage(
                image = image,
                srcOffset = IntOffset(frameIndex * spriteCellWidth, 0),
                srcSize = IntSize(spriteCellWidth, spriteCellHeight),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

private val idleFrameDurations = intArrayOf(280, 110, 110, 140, 140, 320)
private const val spriteCellWidth = 192
private const val spriteCellHeight = 208
