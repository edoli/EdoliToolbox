package kr.co.edoli.edoltool.feature.colorpicker

import androidx.compose.runtime.mutableStateListOf
import kr.co.edoli.edoltool.util.PickedColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedColor(
    val pickedColor: PickedColor,
    val savedAtMillis: Long = System.currentTimeMillis(),
) {
    val savedAtText: String = SimpleDateFormat(
        "HH:mm:ss",
        Locale.getDefault(),
    ).format(Date(savedAtMillis))
}

object ColorHistoryStore {
    val history = mutableStateListOf<SavedColor>()

    fun save(pickedColor: PickedColor) {
        history.add(0, SavedColor(pickedColor = pickedColor))
        if (history.size > 50) {
            history.removeRange(50, history.size)
        }
    }
}
