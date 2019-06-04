package com.example.ledcontrollerapp

import android.bluetooth.*
import android.content.Context
import android.graphics.Color
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.*
import java.util.*

class MainActivity : AppCompatActivity() {

    val LED_CONTROLLER_ADDR = "30:AE:A4:3B:7A:26"
    val LED_SERVICE_UUID: UUID = UUID.fromString("5BDC13B0-F954-43D3-939B-4F701FFD80D8")
    val LED_CHAR_ALL_UUID: UUID = UUID.fromString("5BDC13B1-F954-43D3-939B-4F701FFD80D8")
    val BTN_TEXT_TO_CONNECT = "connect"
    val BTN_TEXT_TO_DISCONNECT = "disconnect"
    val TimeSpinnerItems = arrayOf(0, 10, 20, 30, 40, 50, 60,70, 80, 90, 100)

    var btManager: BluetoothManager? = null
    var btAdapter: BluetoothAdapter? = null

    var is_connected = false;
    var is_connecting = false;
    var connect_btn : Button? = null
    var set_color_btn : Button? = null
    var gatt: BluetoothGatt? = null

    var all_char: BluetoothGattCharacteristic? = null

    var picker: View? = null

    var time_spinner: Spinner? = null

    var color_view_start: View? = null
    var seek_r_start: SeekBar? = null
    var seek_g_start: SeekBar? = null
    var seek_b_start: SeekBar? = null

    var color_view_end: View? = null
    var seek_r_end: SeekBar? = null
    var seek_g_end: SeekBar? = null
    var seek_b_end: SeekBar? = null

    var btn_copy_to_end: Button? = null
    var btn_copy_to_start: Button? = null

    val gatt_callback = object :BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                all_char = null
                is_connected = false
                runOnUiThread{
                    connect_btn?.text = BTN_TEXT_TO_CONNECT
                    set_color_btn?.isEnabled = false
                    picker?.isEnabled = false;
                }
            }
            if (newState != BluetoothGatt.STATE_CONNECTED) return

            gatt?.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)

            var service = gatt?.getService(LED_SERVICE_UUID) ?: return

            all_char = service.getCharacteristic(LED_CHAR_ALL_UUID)

            is_connected = true
            is_connecting = false
            connect_btn?.isEnabled = true
            runOnUiThread{
                connect_btn?.text = BTN_TEXT_TO_DISCONNECT
                set_color_btn?.isEnabled = true
                picker?.isEnabled = true
            }

            gatt?.readCharacteristic(all_char)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)

            if (!gatt?.device?.address.equals(LED_CONTROLLER_ADDR, true)) return

            if (characteristic == null || characteristic.uuid == null) return

            if (characteristic.uuid != LED_CHAR_ALL_UUID) return

            val data = characteristic.value.toUByteArray()

            val time_pos = TimeSpinnerItems.indexOf(data[0].toInt())

            runOnUiThread{
                time_spinner?.setSelection(time_pos)
                seek_r_start?.progress = data[1].toInt()
                seek_g_start?.progress = data[2].toInt()
                seek_b_start?.progress = data[3].toInt()
                seek_r_end?.progress = data[4].toInt()
                seek_g_end?.progress = data[5].toInt()
                seek_b_end?.progress = data[6].toInt()
            }
        }
    }

    val spinner_listener = object : AdapterView.OnItemSelectedListener{
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    val seekbar_listener = object : SeekBar.OnSeekBarChangeListener {
        override fun onStartTrackingTouch(seekBar: SeekBar?) {

        }

        private fun updateColor(isStart: Boolean) {
            var r = 0
            var g = 0
            var b = 0
            var view: View? = null

            if (isStart) {
                r = seek_r_start?.progress ?: return
                g = seek_g_start?.progress ?: return
                b = seek_b_start?.progress ?: return
                view = color_view_start
            } else {
                r = seek_r_end?.progress ?: return
                g = seek_g_end?.progress ?: return
                b = seek_b_end?.progress ?: return
                view = color_view_end
            }

            runOnUiThread {
                view?.setBackgroundColor(Color.rgb(r, g, b))
            }
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            val id = seekBar?.id ?: return

            var isStart = false

            if (id == R.id.seek_color_r_start ||
                    id == R.id.seek_color_g_start||
                    id == R.id.seek_color_b_start) {
                isStart = true
            }

            updateColor(isStart)
        }
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            val id = seekBar?.id ?: return

            var isStart = false

            if (id == R.id.seek_color_r_start ||
                id == R.id.seek_color_g_start||
                id == R.id.seek_color_b_start) {
                isStart = true
            }

            updateColor(isStart)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btManager = this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        btAdapter = btManager?.adapter

        connect_btn = findViewById(R.id.button_connect)

        connect_btn?.setOnClickListener{manageConnection()}

        set_color_btn = findViewById(R.id.button_set_color)

        set_color_btn?.setOnClickListener{setLedColor()}

        picker = findViewById(R.id.color_picker)
        picker?.isEnabled = false

        color_view_start = findViewById(R.id.view_led_color_start)

        seek_r_start = findViewById(R.id.seek_color_r_start)
        seek_r_start?.setOnSeekBarChangeListener(seekbar_listener)

        seek_g_start = findViewById(R.id.seek_color_g_start)
        seek_g_start?.setOnSeekBarChangeListener(seekbar_listener)

        seek_b_start = findViewById(R.id.seek_color_b_start)
        seek_b_start?.setOnSeekBarChangeListener(seekbar_listener)

        color_view_end = findViewById(R.id.view_led_color_end)

        seek_r_end = findViewById(R.id.seek_color_r_end)
        seek_r_end?.setOnSeekBarChangeListener(seekbar_listener)

        seek_g_end = findViewById(R.id.seek_color_g_end)
        seek_g_end?.setOnSeekBarChangeListener(seekbar_listener)

        seek_b_end = findViewById(R.id.seek_color_b_end)
        seek_b_end?.setOnSeekBarChangeListener(seekbar_listener)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, TimeSpinnerItems)
        time_spinner = findViewById(R.id.time_spinner)
        time_spinner?.adapter = adapter

        btn_copy_to_end = findViewById(R.id.btn_copy_to_end)
        btn_copy_to_end?.setOnClickListener { copyColor(false) }
        btn_copy_to_start = findViewById(R.id.btn_copy_to_start)
        btn_copy_to_start?.setOnClickListener { copyColor(true) }

    }

    private fun copyColor(toStart: Boolean) {
        var seek_r_src: SeekBar? = null
        var seek_g_src: SeekBar? = null
        var seek_b_src: SeekBar? = null
        var seek_r_dst: SeekBar? = null
        var seek_g_dst: SeekBar? = null
        var seek_b_dst: SeekBar? = null

        if (toStart) {
            seek_r_src = seek_r_end
            seek_g_src = seek_g_end
            seek_b_src = seek_b_end
            seek_r_dst = seek_r_start
            seek_g_dst = seek_g_start
            seek_b_dst = seek_b_start
        } else {
            seek_r_src = seek_r_start
            seek_g_src = seek_g_start
            seek_b_src = seek_b_start
            seek_r_dst = seek_r_end
            seek_g_dst = seek_g_end
            seek_b_dst = seek_b_end
        }

        runOnUiThread {
            if (seek_r_src != null) seek_r_dst?.progress = seek_r_src?.progress
            if (seek_g_src != null) seek_g_dst?.progress = seek_g_src?.progress
            if (seek_b_src != null) seek_b_dst?.progress = seek_b_src?.progress
        }
    }


    private fun setLedColor() {
        if (!is_connected) return

        val time = TimeSpinnerItems[time_spinner?.selectedItemPosition ?: 0].toByte()
        val data = byteArrayOf(time,
            seek_r_start?.progress?.toByte() ?: 0,
            seek_g_start?.progress?.toByte() ?: 0,
            seek_b_start?.progress?.toByte() ?: 0,
            seek_r_end?.progress?.toByte() ?: 0,
            seek_g_end?.progress?.toByte() ?: 0,
            seek_b_end?.progress?.toByte() ?: 0)

        all_char?.setValue(data)
        gatt?.writeCharacteristic(all_char)
    }

    private fun manageConnection() {
        if (btAdapter?.isEnabled != true || is_connecting) return

        if (is_connecting) return

        if (is_connected) {
            // to disconnect

            gatt?.disconnect()

        } else {
            // to connect

            if (gatt?.device?.address.equals(LED_CONTROLLER_ADDR, true)) {
                if (gatt?.connect() == true) {
                    return
                }
            }

            val device = btAdapter?.getRemoteDevice(LED_CONTROLLER_ADDR) ?: return

            gatt = device.connectGatt(this, true, gatt_callback)
            runOnUiThread {
                is_connecting = true
            }
        }
    }
}
