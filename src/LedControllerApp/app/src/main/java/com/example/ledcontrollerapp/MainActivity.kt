package com.example.ledcontrollerapp

import android.bluetooth.*
import android.content.Context
import android.graphics.Color
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import java.util.*

class MainActivity : AppCompatActivity() {

    val LED_CONTROLLER_ADDR = "30:AE:A4:3B:7A:26"
    val LED_SERVICE_UUID: UUID = UUID.fromString("5BDC13B0-F954-43D3-939B-4F701FFD80D8")
    val LED_CHAR_ALL_UUID: UUID = UUID.fromString("5BDC13B1-F954-43D3-939B-4F701FFD80D8")
    val BTN_TEXT_TO_CONNECT = "connect"
    val BTN_TEXT_TO_DISCONNECT = "disconnect"

    var btManager: BluetoothManager? = null
    var btAdapter: BluetoothAdapter? = null

    var is_connected = false;
    var is_connecting = false;
    var connect_btn : Button? = null
    var set_color_btn : Button? = null
    var gatt: BluetoothGatt? = null

    var all_char: BluetoothGattCharacteristic? = null

    var picker: View? = null

    var color_view: View? = null

    var seek_r: SeekBar? = null
    var seek_g: SeekBar? = null
    var seek_b: SeekBar? = null

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
        }
    }

    val seekbar_listener = object : SeekBar.OnSeekBarChangeListener {
        override fun onStartTrackingTouch(seekBar: SeekBar?) {

        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            val r = seek_r?.progress ?: return
            val g = seek_g?.progress ?: return
            val b = seek_b?.progress ?: return

            val rgb = Color.rgb(r, g, b)

            runOnUiThread {
                color_view?.setBackgroundColor(rgb)
            }
        }
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            val r = seek_r?.progress ?: return
            val g = seek_g?.progress ?: return
            val b = seek_b?.progress ?: return

            val rgb = Color.rgb(r, g, b)

            runOnUiThread {
                color_view?.setBackgroundColor(rgb)
            }
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

        color_view = findViewById(R.id.view_led_color)

        seek_r = findViewById(R.id.seek_color_r)
        seek_r?.setOnSeekBarChangeListener(seekbar_listener)

        seek_g = findViewById(R.id.seek_color_g)
        seek_g?.setOnSeekBarChangeListener(seekbar_listener)

        seek_b = findViewById(R.id.seek_color_b)
        seek_b?.setOnSeekBarChangeListener(seekbar_listener)
    }


    private fun setLedColor() {
        if (!is_connected) return


        val data = byteArrayOf(this.seek_r?.progress?.toByte() ?: 0, this.seek_g?.progress?.toByte() ?: 0, this.seek_b?.progress?.toByte() ?: 0)
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
