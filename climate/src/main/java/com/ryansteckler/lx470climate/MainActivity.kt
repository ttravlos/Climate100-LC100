package com.ryansteckler.lx470climate

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.media.Image
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.method.MovementMethod
import android.text.method.ScrollingMovementMethod
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aoe.fytcanbusmonitor.ModuleCodes.MODULE_CODE_CANBUS
import com.aoe.fytcanbusmonitor.ModuleCodes.MODULE_CODE_CAN_UP
import com.aoe.fytcanbusmonitor.ModuleCodes.MODULE_CODE_MAIN
import com.aoe.fytcanbusmonitor.ModuleCodes.MODULE_CODE_SOUND
import com.aoe.fytcanbusmonitor.MsToolkitConnection
import com.aoe.fytcanbusmonitor.RemoteModuleProxy
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), View.OnTouchListener {

    private val remoteProxy = RemoteModuleProxy()
    private lateinit var logFile: File

    var windowOn: Boolean = false
    var faceOn: Boolean = false
    var feetOn: Boolean = false
    var acOn: Boolean = false
    var recircOn: Boolean = false
    var defrostOn: Boolean = false
    var autoOn: Boolean = false

    private fun writeLog(message: String) {
        // Debug logging disabled - re-enable by uncommenting the lines below
        // try {
        //     val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        //     FileWriter(logFile, true).use { it.write("$timestamp $message\n") }
        // } catch (e: Exception) {
        //     // write failed silently
        // }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request storage permission at runtime (required on Android 10+)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }

        // Pick first writable log location
        val candidates = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "climate_debug.txt"),
            File(Environment.getExternalStorageDirectory(), "climate_debug.txt"),
            File("/sdcard/Download/climate_debug.txt"),
            File("/sdcard/climate_debug.txt"),
            File(getExternalFilesDir(null), "climate_debug.txt"),
            File(filesDir, "climate_debug.txt")
        )
        logFile = candidates.firstOrNull { f ->
            try { f.parentFile?.mkdirs(); f.createNewFile() || f.exists() } catch (e: Exception) { false }
        } ?: File(filesDir, "climate_debug.txt")
        writeLog("=== Climate app started === logFile=${logFile.absolutePath}")

        findViewById<ImageButton>(R.id.btnTempPlus).setOnTouchListener(this)
        findViewById<ImageButton>(R.id.btnTempMinus).setOnTouchListener(this)
        findViewById<ImageButton>(R.id.btnAuto).setOnTouchListener(this)
        findViewById<ImageButton>(R.id.btnVent).setOnTouchListener(this)
        findViewById<ImageButton>(R.id.btnRecirc).setOnTouchListener(this)
        findViewById<ImageButton>(R.id.btnAC).setOnTouchListener(this)
        findViewById<ImageButton>(R.id.btnDefrost).setOnTouchListener(this)
        findViewById<ImageButton>(R.id.btnFanPlus).setOnTouchListener(this)
        findViewById<ImageButton>(R.id.btnFanMinus).setOnTouchListener(this)
        findViewById<ImageButton>(R.id.btnFanOff).setOnTouchListener(this)

        findViewById<TextView>(R.id.text_view).append("hi")
        findViewById<TextView>(R.id.text_view).movementMethod = ScrollingMovementMethod()


//        buttonUp.setOnClickListener {
//            var rm = MsToolkitConnection.instance.remoteToolkit?.getRemoteModule(MODULE_CODE_MAIN)
//                        Log.e("RYANNNNNNNNN", "rm is $rm")
//                          rm?.cmd(0, intArrayOf(11), null, null)
//                Log.e("RYANCC3", "Sending message")
//                var b = Bundle()
//                val i: Int = 0
//                b.putInt("param0", i)
//                SyuJniNative.getInstance().syu_jni_command(50, b, null)
//                Log.e("RYANCC3", "Sent message")
//            Log.e("RYANNNNNNNNN", "RTK: ${MsToolkitConnection.instance.remoteToolkit}")
//
//            var rm = MsToolkitConnection.instance.remoteToolkit?.getRemoteModule(MODULE_CODE_CANBUS)
//            Log.e("RYANNNNNNNNN", "RM: $rm")
//
//            rm?.cmd(2, intArrayOf(48, 1), null, null)
//            Log.e("RYANNNNNNNNN", "Sent message")
//            }

    }

    override fun onStart() {
        super.onStart()
        writeLog("onStart: connecting to IPC service")
        ModuleCallback.init(this)
        connectMain()
        connectCanbus()
        connectSound()
        connectCanUp()
        MsToolkitConnection.instance.connect(this)
        writeLog("onStart: connect() called")
    }


    override fun onTouch(view: View?, motionEvent: MotionEvent?): Boolean {
        var canBusCommand: Int = -1
        when (view?.id) {
            R.id.btnTempPlus->{ canBusCommand = 3 }
            R.id.btnTempMinus->{ canBusCommand = 2 }
            R.id.btnAuto->{ canBusCommand = 21 }
            R.id.btnVent->{ canBusCommand = 36 }
            R.id.btnRecirc->{ canBusCommand = 25 }
            R.id.btnAC->{ canBusCommand = 23 }
            R.id.btnDefrost->{ canBusCommand = 18 }
            R.id.btnFanPlus->{ canBusCommand = 10 }
            R.id.btnFanMinus->{ canBusCommand = 9 }
            R.id.btnFanOff->{ canBusCommand = 1 }
        }

        val image = view as ImageView
        when (motionEvent?.action) {
            MotionEvent.ACTION_DOWN -> {
                var highlight = Color.argb(50, 255, 255, 255)
                if (view.id == R.id.btnTempPlus) {
                    highlight = Color.argb(50, 255, 0, 0)
                }
                else if (view.id == R.id.btnTempMinus) {
                    highlight = Color.argb(50, 0, 0, 255)
                }

                image?.setColorFilter(highlight, PorterDuff.Mode.SRC_ATOP)
                image?.invalidate()

                // Send the press command
                if (canBusCommand != -1) {
                    writeLog("BUTTON DOWN canBusCommand=$canBusCommand viewId=${view.id}")
                    val rm = MsToolkitConnection.instance.remoteToolkit?.getRemoteModule(MODULE_CODE_CANBUS)
                    rm?.cmd(0, intArrayOf(canBusCommand, 1), null, null)
                    view.performClick()
                }
            }
            MotionEvent.ACTION_UP -> {
                image?.clearColorFilter()
                image?.invalidate()

                // Send the release command
                if (canBusCommand != -1) {
                    val rm = MsToolkitConnection.instance.remoteToolkit?.getRemoteModule(MODULE_CODE_CANBUS)
                    rm?.cmd(0, intArrayOf(canBusCommand, 0), null, null)
                }
            }
        }

        return true
    }




    private fun connectMain() {
        val callback = ModuleCallback("Main", findViewById(R.id.text_view))
        val connection = IPCConnection(MODULE_CODE_MAIN)
        for (i in 0..119) {
            connection.addCallback(callback, i)
        }
        MsToolkitConnection.instance.addObserver(connection)
    }

    private fun connectCanbus() {
        val callback = ModuleCallback("Canbus", findViewById(R.id.text_view))
        val connection = IPCConnection(MODULE_CODE_CANBUS)
        for (i in 0..50) {
            connection.addCallback(callback, i)
        }
        for (i in 1000..1036) {
            connection.addCallback(callback, i)
        }
        MsToolkitConnection.instance.addObserver(connection)
    }

    private fun connectSound() {
        val callback = ModuleCallback("Sound", findViewById(R.id.text_view))
        val connection = IPCConnection(MODULE_CODE_SOUND)
        for (i in 0..49) {
            connection.addCallback(callback, i)
        }
        MsToolkitConnection.instance.addObserver(connection)
    }

    private fun connectCanUp() {
        val callback = ModuleCallback("CanUp", findViewById(R.id.text_view))
        val connection = IPCConnection(MODULE_CODE_CAN_UP)
        connection.addCallback(callback, 100)
        MsToolkitConnection.instance.addObserver(connection)
    }

    fun canBusNotify(systemName: String, updateCode: Int, intArray: IntArray?, floatArray: FloatArray?, strArray: Array<String?>?) {
        // Log everything to file
        val intStr = intArray?.joinToString(",") ?: "null"
        writeLog("system=$systemName updateCode=$updateCode ints=[$intStr]")

        if (systemName.lowercase().equals("canbus")) {
            if (updateCode in 1..16 || updateCode in 69..81 && updateCode != 77) {
                findViewById<TextView>(R.id.text_view).append(
                    "updateCode: " + updateCode + " value: " + intArray?.get(
                        0
                    ) + "\n"
                )
            }
            when (updateCode) {
                // Temperature setpoint - driver side (raw value + 64 = Fahrenheit)
                27 -> {
                    val newTemp = intArray?.get(0)
                    val txtTemperature = findViewById<TextView>(R.id.txtTemperature)
                    if (newTemp == -2) {
                        txtTemperature.text = "LO"
                    } else if (newTemp == -3) {
                        txtTemperature.text = "HI"
                    } else {
                        val inF: Int? = newTemp?.plus(64)
                        if (inF != null) {
                            txtTemperature.text = inF.toString()
                        }
                    }
                }
                // Auto mode
                4 -> {
                    autoOn = intArray?.get(0) == 1
                    findViewById<TextView>(R.id.lblAuto).visibility =
                        if (autoOn) View.VISIBLE else View.INVISIBLE
                }
                // AC on/off
                11 -> {
                    acOn = intArray?.get(0) == 1
                    findViewById<ImageView>(R.id.imgAC).setImageResource(if (acOn) R.drawable.img_ac_on else R.drawable.img_ac_off)
                }
                // Recirc on/off
                12 -> {
                    recircOn = intArray?.get(0) == 1
                    findViewById<ImageView>(R.id.imgRecirc).setImageResource(if (recircOn) R.drawable.img_recirc_on else R.drawable.img_recirc_off)
                }
                // Windshield mode on/off (toggle - pressing again returns to previous vent mode)
                15 -> {
                    defrostOn = intArray?.get(0) == 1
                    findViewById<ImageView>(R.id.imgDefrost).setImageResource(if (defrostOn) R.drawable.img_defrost_on else R.drawable.img_defrost_off)
                }
                // Fan speed level
                21 -> {
                    val fanSpeed = intArray?.get(0)
                    findViewById<TextView>(R.id.txtFanSpeed).text = fanSpeed.toString()
                }
                // Feet vent
                18 -> {
                    feetOn = intArray?.get(0) == 1
                    handleVentStatus()
                }
                // Face vent
                19 -> {
                    faceOn = intArray?.get(0) == 1
                    handleVentStatus()
                }
                // Window vent (part of vent cycle, also activates with windshield button)
                20 -> {
                    windowOn = intArray?.get(0) == 1
                    handleVentStatus()
                }
            }
        }

    }
    fun handleVentStatus() {
        // Ignore face+window combination — this is a transient state during vent mode cycling,
        // not a valid final mode. Wait for the next update to settle into the correct state.
        if (faceOn && windowOn) return

        if (faceOn && feetOn) {
            findViewById<ImageView>(R.id.imgVents).setImageResource(R.drawable.img_vents_foot_face)
        } else if (feetOn && windowOn) {
            findViewById<ImageView>(R.id.imgVents).setImageResource(R.drawable.img_vents_foot_defrost)
        } else if (feetOn) {
            findViewById<ImageView>(R.id.imgVents).setImageResource(R.drawable.img_vents_foot)
        } else if (faceOn) {
            findViewById<ImageView>(R.id.imgVents).setImageResource(R.drawable.img_vents_face)
        } else if (windowOn) {
            findViewById<ImageView>(R.id.imgVents).setImageResource(R.drawable.img_vents_defrost_only)
        } else {
            findViewById<ImageView>(R.id.imgVents).setImageResource(R.drawable.img_vents_off)
        }
    }


}