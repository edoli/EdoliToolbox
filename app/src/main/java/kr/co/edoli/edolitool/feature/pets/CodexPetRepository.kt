package kr.co.edoli.edolitool.feature.pets

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

data class CodexPet(
    val id: String,
    val displayName: String,
    val description: String,
    val spriteVersionNumber: Int,
    val directory: File,
)

data class CodexPetFiles(
    val pet: CodexPet,
    val spritesheet: File,
)

object CodexPetRepository {
    private const val maxArchiveBytes = 25 * 1024 * 1024
    private const val maxFileBytes = 20 * 1024 * 1024
    private const val maxEntryCount = 20
    private val validId = Regex("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")

    fun import(context: Context, uri: Uri): CodexPet {
        val files = readZip(context, uri)
        val manifestPath = files.keys.singleOrNull { it.substringAfterLast('/') == "pet.json" }
            ?: throw PetImportException("ZIP 안에 pet.json이 하나만 있어야 합니다.")
        val manifestBytes = files.getValue(manifestPath)
        if (manifestBytes.size > 32 * 1024) {
            throw PetImportException("pet.json 파일이 너무 큽니다.")
        }

        val manifest = runCatching { JSONObject(manifestBytes.decodeToString()) }
            .getOrElse { throw PetImportException("pet.json 형식이 올바르지 않습니다.") }
        val id = manifest.optString("id")
        val displayName = manifest.optString("displayName")
        val description = manifest.optString("description")
        val version = if (manifest.has("spriteVersionNumber")) {
            manifest.optInt("spriteVersionNumber", -1)
        } else {
            1
        }
        val spritesheetPath = manifest.optString("spritesheetPath")
        if (!validId.matches(id)) throw PetImportException("펫 ID 형식이 올바르지 않습니다.")
        if (displayName.isBlank()) throw PetImportException("펫 표시 이름이 필요합니다.")
        if (version !in supportedSpriteVersions) {
            throw PetImportException("지원하지 않는 Codex 펫 버전입니다. v1 또는 v2만 가져올 수 있습니다.")
        }
        if (!spritesheetPath.endsWith(".webp", ignoreCase = true)) {
            throw PetImportException("spritesheetPath는 WebP 파일이어야 합니다.")
        }

        val sheetPath = resolveRelativePath(manifestPath, spritesheetPath)
        val sheetBytes = files[sheetPath]
            ?: throw PetImportException("spritesheet.webp 파일을 찾을 수 없습니다.")
        validateSpritesheet(sheetBytes, version)

        val root = File(context.filesDir, "codex-pets").apply { mkdirs() }
        val temporaryDirectory = File(root, ".${id}-${UUID.randomUUID()}")
        val targetDirectory = File(root, id)
        try {
            writeFile(temporaryDirectory, manifestPath, manifestBytes)
            writeFile(temporaryDirectory, sheetPath, sheetBytes)
            if (targetDirectory.exists() && !targetDirectory.deleteRecursively()) {
                throw PetImportException("기존 펫을 교체할 수 없습니다.")
            }
            if (!temporaryDirectory.renameTo(targetDirectory)) {
                throw PetImportException("펫 설치 폴더를 만들 수 없습니다.")
            }
        } finally {
            if (temporaryDirectory.exists()) temporaryDirectory.deleteRecursively()
        }
        return CodexPet(id, displayName, description, version, targetDirectory)
    }

    fun installedPets(context: Context): List<CodexPet> {
        val root = File(context.filesDir, "codex-pets")
        return root.listFiles().orEmpty().mapNotNull { directory ->
            val manifestFile = directory.walkTopDown().firstOrNull { it.name == "pet.json" } ?: return@mapNotNull null
            runCatching {
                val manifest = JSONObject(manifestFile.readText())
                CodexPet(
                    id = manifest.getString("id"),
                    displayName = manifest.getString("displayName"),
                    description = manifest.optString("description"),
                    spriteVersionNumber = manifest.optInt("spriteVersionNumber", 1),
                    directory = directory,
                )
            }.getOrNull()
        }.sortedBy { it.displayName.lowercase() }
    }

    fun remove(context: Context, id: String): Boolean {
        if (!validId.matches(id)) return false
        val directory = File(context.filesDir, "codex-pets/$id")
        return !directory.exists() || directory.deleteRecursively()
    }

    fun findPetFiles(context: Context, id: String): CodexPetFiles? {
        val pet = installedPets(context).firstOrNull { it.id == id } ?: return null
        val manifestFile = pet.directory.walkTopDown().firstOrNull { it.name == "pet.json" } ?: return null
        return runCatching {
            val manifest = JSONObject(manifestFile.readText())
            val spritesheetPath = resolveRelativePath(
                manifestFile.relativeTo(pet.directory).invariantSeparatorsPath,
                manifest.getString("spritesheetPath"),
            )
            val spritesheet = File(pet.directory, spritesheetPath)
            if (!spritesheet.isFile) return null
            CodexPetFiles(pet = pet, spritesheet = spritesheet)
        }.getOrNull()
    }

    private fun readZip(context: Context, uri: Uri): Map<String, ByteArray> {
        val files = linkedMapOf<String, ByteArray>()
        var totalBytes = 0
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        if (files.size >= maxEntryCount) throw PetImportException("ZIP 파일 항목이 너무 많습니다.")
                        val path = normalizeZipPath(entry.name)
                        if (path in files) throw PetImportException("ZIP에 중복 파일이 있습니다.")
                        val bytes = zip.readLimitedBytes()
                        totalBytes += bytes.size
                        if (totalBytes > maxArchiveBytes) throw PetImportException("압축 해제 크기가 너무 큽니다.")
                        files[path] = bytes
                    }
                    zip.closeEntry()
                }
            }
        } ?: throw PetImportException("선택한 ZIP 파일을 열 수 없습니다.")
        return files
    }

    private fun ZipInputStream.readLimitedBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            if (output.size() > maxFileBytes) throw PetImportException("ZIP 안의 파일이 너무 큽니다.")
        }
        return output.toByteArray()
    }

    private fun normalizeZipPath(path: String): String {
        val normalized = path.replace('\\', '/')
        if (normalized.startsWith('/') || normalized.split('/').any { it.isBlank() || it == "." || it == ".." }) {
            throw PetImportException("ZIP 파일 경로가 안전하지 않습니다.")
        }
        return normalized
    }

    private fun resolveRelativePath(manifestPath: String, spritesheetPath: String): String {
        val parent = manifestPath.substringBeforeLast('/', missingDelimiterValue = "")
        val path = if (parent.isEmpty()) spritesheetPath else "$parent/$spritesheetPath"
        return normalizeZipPath(path)
    }

    private fun validateSpritesheet(bytes: ByteArray, version: Int) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val expectedSize = spritesheetSizes.getValue(version)
        if (options.outWidth != expectedSize.width || options.outHeight != expectedSize.height) {
            throw PetImportException(
                "Codex 펫 v$version 스프라이트시트는 ${expectedSize.width} × ${expectedSize.height} 픽셀이어야 합니다.",
            )
        }
    }

    private fun writeFile(root: File, relativePath: String, bytes: ByteArray) {
        val file = File(root, relativePath)
        if (!file.canonicalPath.startsWith("${root.canonicalPath}${File.separator}")) {
            throw PetImportException("펫 저장 경로가 안전하지 않습니다.")
        }
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { it.write(bytes) }
    }

    private data class SpritesheetSize(val width: Int, val height: Int)

    private val spritesheetSizes = mapOf(
        1 to SpritesheetSize(width = 1536, height = 1872),
        2 to SpritesheetSize(width = 1536, height = 2288),
    )
    private val supportedSpriteVersions = spritesheetSizes.keys
}

class PetImportException(message: String) : IllegalArgumentException(message)
