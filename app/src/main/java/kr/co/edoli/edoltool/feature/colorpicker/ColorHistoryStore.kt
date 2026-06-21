package kr.co.edoli.edoltool.feature.colorpicker

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import kr.co.edoli.edoltool.util.PickedColor
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedColor(
    val id: Long = System.currentTimeMillis(),
    val pickedColor: PickedColor,
    val savedAtMillis: Long = System.currentTimeMillis(),
) {
    val savedAtText: String = SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss",
        Locale.getDefault(),
    ).format(Date(savedAtMillis))
}

object ColorHistoryStore {
    private const val prefsName = "color_history"
    private const val historyKey = "items"
    private const val maxHistorySize = 100
    private var loaded = false

    val history = mutableStateListOf<SavedColor>()

    fun load(context: Context) {
        if (loaded) return
        loaded = true

        val raw = prefs(context).getString(historyKey, null) ?: return
        val restored = runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                SavedColor(
                    id = item.optLong("id", item.optLong("savedAtMillis", System.currentTimeMillis())),
                    savedAtMillis = item.optLong("savedAtMillis", System.currentTimeMillis()),
                    pickedColor = PickedColor(
                        colorInt = item.getInt("colorInt"),
                        source = item.optString("source", "저장됨"),
                        x = item.optNullableInt("x"),
                        y = item.optNullableInt("y"),
                    ),
                )
            }
        }.getOrDefault(emptyList())

        history.clear()
        history.addAll(restored.take(maxHistorySize))
    }

    fun save(
        context: Context,
        pickedColor: PickedColor,
    ) {
        load(context)
        history.add(
            0,
            SavedColor(
                id = System.currentTimeMillis(),
                pickedColor = pickedColor,
            ),
        )
        if (history.size > maxHistorySize) {
            history.removeRange(maxHistorySize, history.size)
        }
        persist(context)
    }

    fun remove(
        context: Context,
        savedColor: SavedColor,
    ) {
        load(context)
        history.removeAll { it.id == savedColor.id }
        persist(context)
    }

    fun clear(context: Context) {
        load(context)
        history.clear()
        persist(context)
    }

    private fun persist(context: Context) {
        val array = JSONArray()
        history.forEach { savedColor ->
            array.put(
                JSONObject()
                    .put("id", savedColor.id)
                    .put("savedAtMillis", savedColor.savedAtMillis)
                    .put("colorInt", savedColor.pickedColor.colorInt)
                    .put("source", savedColor.pickedColor.source)
                    .put("x", savedColor.pickedColor.x)
                    .put("y", savedColor.pickedColor.y),
            )
        }

        prefs(context)
            .edit()
            .putString(historyKey, array.toString())
            .apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(
        prefsName,
        Context.MODE_PRIVATE,
    )

    private fun JSONObject.optNullableInt(name: String): Int? {
        return if (has(name) && !isNull(name)) optInt(name) else null
    }
}
