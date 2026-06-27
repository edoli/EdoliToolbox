package kr.co.edoli.edolitool.feature.storage

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageInspectorRoute(onBackClick: () -> Unit) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(readStorageSnapshot(context)) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "저장장치 정보",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "뒤로",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { snapshot = readStorageSnapshot(context) }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "새로고침",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SummaryCard(snapshot = snapshot)
                FormatLimitCard(
                    onOpenSettings = {
                        openStorageSettings(context)
                    },
                )
                snapshot.volumes.forEach { volume ->
                    VolumeCard(volume = volume)
                }
                MountSourceCard(snapshot = snapshot)
            }
        }
    }
}

@Composable
private fun SummaryCard(snapshot: StorageSnapshot) {
    InfoCard(title = "요약") {
        DetailRow("감지된 볼륨", snapshot.volumes.size.toString())
        DetailRow("외장/탈착식", snapshot.volumes.count { it.isRemovable }.toString())
        DetailRow("Android 버전", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        DetailRow("마운트 정보", "${snapshot.mounts.size}개 항목")
    }
}

@Composable
private fun FormatLimitCard(onOpenSettings: () -> Unit) {
    InfoCard(title = "포맷 기능") {
        Text(
            text = "일반 앱 권한에서는 저장장치를 직접 포맷하거나 파일시스템을 선택할 수 없습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "이 화면은 읽기 전용 정보만 표시합니다. 포맷은 Android 시스템 저장소 설정에서 지원되는 경우에만 가능합니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
            )
            Text(
                text = "저장소 설정 열기",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun VolumeCard(volume: StorageVolumeSnapshot) {
    InfoCard(title = volume.displayName.ifBlank { "이름 없는 저장소" }) {
        DetailRow("종류", if (volume.isRemovable) "외장/탈착식" else "내장/고정")
        DetailRow("기본 저장소", if (volume.isPrimary) "예" else "아니오")
        DetailRow("상태", volume.state)
        DetailRow("UUID", volume.uuid ?: "없음")
        DetailRow("경로", volume.directoryPath ?: "공개 API로 확인 불가")
        DetailRow("앱 디렉터리", volume.appExternalDirectoryPath ?: "없음")
        DetailRow("총 용량", volume.totalBytes?.let(::formatBytes) ?: "알 수 없음")
        DetailRow("사용 가능", volume.availableBytes?.let(::formatBytes) ?: "알 수 없음")
        DetailRow("파일시스템", volume.fileSystemType ?: "알 수 없음")
        DetailRow("감지 방식", volume.fileSystemSource ?: "없음")
        volume.mountPoint?.let { mountPoint ->
            DetailRow("마운트 지점", mountPoint)
        }
    }
}

@Composable
private fun MountSourceCard(snapshot: StorageSnapshot) {
    InfoCard(title = "감지 한계") {
        Text(
            text = "파일시스템 타입은 Android 공개 API가 직접 제공하지 않아서, 읽을 수 있는 마운트 테이블에서 가장 가까운 항목을 찾아 추정합니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (snapshot.mounts.isEmpty()) {
                "현재 기기에서 앱이 읽을 수 있는 마운트 항목이 없습니다."
            } else {
                "원본: ${snapshot.mountSources.joinToString()}"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.38f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.62f),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private data class StorageSnapshot(
    val volumes: List<StorageVolumeSnapshot>,
    val mounts: List<MountEntry>,
    val mountSources: List<String>,
)

private data class StorageVolumeSnapshot(
    val displayName: String,
    val uuid: String?,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
    val state: String,
    val directoryPath: String?,
    val appExternalDirectoryPath: String?,
    val totalBytes: Long?,
    val availableBytes: Long?,
    val fileSystemType: String?,
    val fileSystemSource: String?,
    val mountPoint: String?,
)

private data class MountEntry(
    val mountPoint: String,
    val fileSystemType: String,
    val source: String,
    val sourceFile: String,
)

private fun readStorageSnapshot(context: Context): StorageSnapshot {
    val storageManager = context.getSystemService(StorageManager::class.java)
    val mountResult = readMountEntries()
    val appExternalDirs = context.getExternalFilesDirs(null)
        .filterNotNull()
        .associateBy { file -> storageManager.getStorageVolume(file)?.uuid ?: PRIMARY_VOLUME_KEY }

    val volumes = storageManager.storageVolumes.map { volume ->
        val directoryPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            volume.directory?.absolutePath
        } else {
            null
        }
        val appDirectory = appExternalDirs[volume.uuid ?: PRIMARY_VOLUME_KEY]?.absolutePath
        val statPath = directoryPath ?: appDirectory
        val stats = statPath?.let { path ->
            runCatching { StatFs(path) }.getOrNull()
        }
        val mount = mountResult.entries.findBestMount(statPath ?: directoryPath)

        StorageVolumeSnapshot(
            displayName = volume.getDescription(context).orEmpty(),
            uuid = volume.uuid,
            isPrimary = volume.isPrimary,
            isRemovable = volume.isRemovable,
            state = volume.state.toReadableStorageState(),
            directoryPath = directoryPath,
            appExternalDirectoryPath = appDirectory,
            totalBytes = stats?.totalBytes,
            availableBytes = stats?.availableBytes,
            fileSystemType = mount?.fileSystemType,
            fileSystemSource = mount?.sourceFile,
            mountPoint = mount?.mountPoint,
        )
    }

    return StorageSnapshot(
        volumes = volumes,
        mounts = mountResult.entries,
        mountSources = mountResult.sources,
    )
}

private data class MountReadResult(
    val entries: List<MountEntry>,
    val sources: List<String>,
)

private fun readMountEntries(): MountReadResult {
    val mountInfo = readMountInfo("/proc/self/mountinfo")
    val mounts = readProcMounts("/proc/mounts")
    return MountReadResult(
        entries = (mountInfo + mounts).distinctBy { entry ->
            "${entry.mountPoint}|${entry.fileSystemType}|${entry.source}"
        },
        sources = listOfNotNull(
            "/proc/self/mountinfo".takeIf { mountInfo.isNotEmpty() },
            "/proc/mounts".takeIf { mounts.isNotEmpty() },
        ),
    )
}

private fun readMountInfo(path: String): List<MountEntry> {
    return runCatching {
        java.io.File(path).readLines().mapNotNull { line ->
            val parts = line.split(" ")
            val separatorIndex = parts.indexOf("-")
            if (separatorIndex < 0 || parts.size <= separatorIndex + 2 || parts.size <= 4) {
                return@mapNotNull null
            }
            MountEntry(
                mountPoint = decodeMountPath(parts[4]),
                fileSystemType = parts[separatorIndex + 1],
                source = parts[separatorIndex + 2],
                sourceFile = path,
            )
        }
    }.getOrDefault(emptyList())
}

private fun readProcMounts(path: String): List<MountEntry> {
    return runCatching {
        java.io.File(path).readLines().mapNotNull { line ->
            val parts = line.split(" ")
            if (parts.size < 3) return@mapNotNull null
            MountEntry(
                mountPoint = decodeMountPath(parts[1]),
                fileSystemType = parts[2],
                source = parts[0],
                sourceFile = path,
            )
        }
    }.getOrDefault(emptyList())
}

private fun List<MountEntry>.findBestMount(path: String?): MountEntry? {
    if (path == null) return null
    return filter { entry ->
        path == entry.mountPoint || path.startsWith("${entry.mountPoint}/")
    }.maxByOrNull { entry -> entry.mountPoint.length }
}

private fun String.toReadableStorageState(): String {
    return when (this) {
        Environment.MEDIA_MOUNTED -> "마운트됨"
        Environment.MEDIA_MOUNTED_READ_ONLY -> "읽기 전용"
        Environment.MEDIA_UNMOUNTED -> "마운트 해제됨"
        Environment.MEDIA_CHECKING -> "검사 중"
        Environment.MEDIA_EJECTING -> "분리 중"
        Environment.MEDIA_NOFS -> "파일시스템 없음"
        Environment.MEDIA_REMOVED -> "제거됨"
        Environment.MEDIA_SHARED -> "USB 공유 중"
        Environment.MEDIA_BAD_REMOVAL -> "비정상 제거"
        Environment.MEDIA_UNMOUNTABLE -> "마운트 불가"
        else -> this
    }
}

private fun decodeMountPath(value: String): String {
    return value
        .replace("\\040", " ")
        .replace("\\011", "\t")
        .replace("\\012", "\n")
        .replace("\\134", "\\")
}

private fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index += 1
    }
    return if (index == 0) {
        "${bytes} ${units[index]}"
    } else {
        "%.1f %s".format(value, units[index])
    }
}

private fun openStorageSettings(context: Context) {
    val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .recoverCatching {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
}

private const val PRIMARY_VOLUME_KEY = "primary"
