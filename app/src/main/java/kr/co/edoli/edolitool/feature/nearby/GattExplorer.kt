package kr.co.edoli.edolitool.feature.nearby

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect

@Composable
internal fun GattExplorer(device: BluetoothDevice, deviceName: String, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val session = remember(context, device) { GattSession(context.applicationContext, device) }
    var expandedService by remember(device, session.services) { mutableStateOf<BluetoothGattService?>(null) }
    var selected by remember(device, session.services) { mutableStateOf<BluetoothGattCharacteristic?>(null) }
    var writeText by remember(selected) { mutableStateOf("") }
    LifecycleStartEffect(session) {
        session.connect()
        onStopOrDispose { session.close() }
    }

    NearbyToolScreen("BLE 탐색기", onBackClick) {
        item {
            NearbyToolCard {
                Text(deviceName, style = MaterialTheme.typography.titleMedium)
                Text(session.status)
                if (!session.isOpen) {
                    Button(onClick = session::connect) { Text("다시 연결") }
                }
            }
        }
        session.services.forEach { service ->
            item {
                NearbyToolCard {
                    Text("GATT 서비스", style = MaterialTheme.typography.labelLarge)
                    SelectionContainer { Text(service.uuid.toString()) }
                    Text("특성 ${service.characteristics.size}개")
                    if (service.characteristics.isNotEmpty()) {
                        OutlinedButton(
                            enabled = !session.busy,
                            onClick = {
                                selected = null
                                expandedService = service.takeUnless { expandedService == service }
                            },
                        ) {
                            Text(if (expandedService == service) "서비스 닫기" else "서비스 열기")
                        }
                    }
                }
            }
            val visibleCharacteristics = if (expandedService == service) service.characteristics else emptyList()
            items(visibleCharacteristics) { characteristic ->
                NearbyToolCard {
                    Text("특성", style = MaterialTheme.typography.labelLarge)
                    SelectionContainer { Text(characteristic.uuid.toString()) }
                    val readable = characteristic.hasProperty(BluetoothGattCharacteristic.PROPERTY_READ)
                    val writable = characteristic.hasProperty(BluetoothGattCharacteristic.PROPERTY_WRITE) ||
                        characteristic.hasProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)
                    val operations = listOfNotNull("읽기".takeIf { readable }, "쓰기".takeIf { writable })
                    Text(operations.joinToString(" · ").ifEmpty { "읽기·쓰기 미지원" })
                    if (readable || writable) {
                        OutlinedButton(
                            enabled = !session.busy,
                            onClick = { selected = characteristic.takeUnless { selected == characteristic } },
                        ) {
                            Text(if (selected == characteristic) "특성 닫기" else "특성 열기")
                        }
                    }
                    if (selected == characteristic) {
                        session.operationMessage(characteristic)?.let { Text(it) }
                        session.values[characteristic]?.let { value ->
                            SelectionContainer { Text(value.ifEmpty { "빈 값" }) }
                        }
                        if (readable) {
                            Button(enabled = !session.busy, onClick = { session.read(characteristic) }) {
                                Text("값 읽기")
                            }
                        }
                        if (writable) {
                            val hex = writeText.filterNot(Char::isWhitespace)
                            val valid = hex.length in 2..1024 && hex.length % 2 == 0 && hex.all { it.digitToIntOrNull(16) != null }
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = writeText,
                                onValueChange = { if (it.length <= 1536) writeText = it },
                                label = { Text("쓸 값 (16진수)") },
                                placeholder = { Text("01 FF") },
                                isError = writeText.isNotBlank() && !valid,
                            )
                            Button(
                                enabled = valid && !session.busy,
                                onClick = {
                                    val bytes = ByteArray(hex.length / 2) { index ->
                                        hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
                                    }
                                    session.write(characteristic, bytes)
                                },
                            ) { Text("값 쓰기") }
                        }
                    }
                }
            }
        }
    }
}

private fun BluetoothGattCharacteristic.hasProperty(property: Int): Boolean = (properties and property) != 0

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
private class GattSession(private val context: Context, private val device: BluetoothDevice) {
    var status by mutableStateOf("연결 중…")
        private set
    var services by mutableStateOf(emptyList<BluetoothGattService>())
        private set
    val values = mutableStateMapOf<BluetoothGattCharacteristic, String>()

    private var gatt by mutableStateOf<BluetoothGatt?>(null)
    private var pending by mutableStateOf<GattOperation?>(null)
    private var lastOperation by mutableStateOf<Pair<BluetoothGattCharacteristic, String>?>(null)
    val isOpen get() = gatt != null
    val busy get() = pending != null

    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable {
        val message = if (busy) "요청 시간이 초과되었습니다. 다시 연결해 주세요." else "기기의 응답을 기다리는 시간이 초과되었습니다."
        close()
        status = message
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            update(gatt) {
                when {
                    status != BluetoothGatt.GATT_SUCCESS -> fail("BLE 연결에 실패했습니다. (코드 ${status})")
                    newState == BluetoothProfile.STATE_DISCONNECTED -> fail("기기 연결이 끊겼습니다.")
                    newState == BluetoothProfile.STATE_CONNECTED -> {
                        this@GattSession.status = "GATT 서비스 검색 중…"
                        if (!gatt.discoverServices()) fail("GATT 서비스 검색을 시작할 수 없습니다.")
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            update(gatt) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    handler.removeCallbacks(timeout)
                    services = gatt.services.toList()
                    this@GattSession.status = if (services.isEmpty()) "검색된 GATT 서비스가 없습니다." else "GATT 서비스 ${services.size}개"
                } else {
                    fail("GATT 서비스를 가져오지 못했습니다. (코드 ${status})")
                }
            }
        }

        @Deprecated("Legacy Android GATT callback")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT < 33) {
                finishRead(gatt, characteristic, status, characteristic.value?.copyOf() ?: byteArrayOf())
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            finishRead(gatt, characteristic, status, value.copyOf())
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            update(gatt) {
                val operation = pending ?: return@update
                if (operation.characteristic != characteristic || operation.writeType == null) return@update
                pending = null
                handler.removeCallbacks(timeout)
                val message = when {
                    status != BluetoothGatt.GATT_SUCCESS -> operationError("쓰기", status)
                    operation.writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE -> "쓰기 요청을 전송했습니다."
                    else -> "쓰기를 완료했습니다."
                }
                lastOperation = characteristic to message
            }
        }
    }

    fun connect() {
        if (gatt != null) return
        status = "연결 중…"
        try {
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) fail("BLE 연결을 시작할 수 없습니다.") else armTimeout()
        } catch (_: SecurityException) {
            fail("블루투스 연결 권한을 확인해 주세요.")
        }
    }

    fun close() {
        handler.removeCallbacks(timeout)
        val connection = gatt
        gatt = null
        pending = null
        lastOperation = null
        services = emptyList()
        values.clear()
        if (connection != null) {
            runCatching { connection.disconnect() }
            runCatching { connection.close() }
        }
    }

    fun read(characteristic: BluetoothGattCharacteristic) {
        startOperation(characteristic, writeType = null) { connection ->
            connection.readCharacteristic(characteristic)
        }
    }

    fun operationMessage(characteristic: BluetoothGattCharacteristic): String? =
        lastOperation?.takeIf { it.first == characteristic }?.second

    fun write(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        val writeType = if (characteristic.hasProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        startOperation(characteristic, writeType) { connection ->
            if (Build.VERSION.SDK_INT >= 33) {
                connection.writeCharacteristic(characteristic, value, writeType) == BluetoothStatusCodes.SUCCESS
            } else {
                characteristic.writeType = writeType
                characteristic.value = value
                connection.writeCharacteristic(characteristic)
            }
        }
    }

    private fun startOperation(
        characteristic: BluetoothGattCharacteristic,
        writeType: Int?,
        request: (BluetoothGatt) -> Boolean,
    ) {
        val connection = gatt ?: return
        if (pending != null) return
        pending = GattOperation(characteristic, writeType)
        try {
            if (request(connection)) {
                lastOperation = characteristic to if (writeType == null) "읽는 중…" else "쓰는 중…"
                armTimeout()
            } else {
                pending = null
                lastOperation = characteristic to "특성 요청을 시작할 수 없습니다."
            }
        } catch (_: SecurityException) {
            fail("블루투스 연결 권한을 확인해 주세요.")
        }
    }

    private fun finishRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        result: Int,
        value: ByteArray,
    ) {
        update(gatt) {
            val operation = pending ?: return@update
            if (operation.characteristic != characteristic || operation.writeType != null) return@update
            pending = null
            handler.removeCallbacks(timeout)
            if (result == BluetoothGatt.GATT_SUCCESS) {
                values[characteristic] = value.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
                lastOperation = characteristic to "읽기를 완료했습니다."
            } else {
                lastOperation = characteristic to operationError("읽기", result)
            }
        }
    }

    private fun update(connection: BluetoothGatt, action: () -> Unit) {
        handler.post {
            if (gatt == connection) {
                try {
                    action()
                } catch (_: SecurityException) {
                    fail("블루투스 연결 권한을 확인해 주세요.")
                }
            }
        }
    }

    private fun fail(message: String) {
        close()
        status = message
    }

    private fun armTimeout() {
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, 12_000)
    }

    private fun operationError(label: String, result: Int): String = when (result) {
        BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION,
        BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION,
        -> "기기에서 페어링 또는 암호화된 연결을 요구합니다."
        else -> "${label}에 실패했습니다. (코드 ${result})"
    }

    private data class GattOperation(val characteristic: BluetoothGattCharacteristic, val writeType: Int?)
}
