package com.sersoluciones.flutter_pos_printer_platform.usb

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Handler
import android.util.Log
import android.widget.Toast
import com.sersoluciones.flutter_pos_printer_platform.PrintOutcome
import com.sersoluciones.flutter_pos_printer_platform.R

class USBPrinterService private constructor(private var mHandler: Handler?) {
    private var mContext: Context? = null
    private var mUSBManager: UsbManager? = null
    private var mPermissionIndent: PendingIntent? = null
    private var mUsbDevice: UsbDevice? = null
    private var mUsbDeviceConnection: UsbDeviceConnection? = null
    private var mUsbInterface: UsbInterface? = null
    private var mEndPoint: UsbEndpoint? = null
    private var mInEndPoint: UsbEndpoint? = null
    var state: Int = STATE_USB_NONE

    fun setHandler(handler: Handler?) {
        mHandler = handler
    }

    private val mUsbDeviceReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if ((ACTION_USB_PERMISSION == action)) {
                synchronized(this) {
                    val usbDevice: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Log.i(
                            LOG_TAG,
                            "Success get permission for device ${usbDevice?.deviceId}, vendor_id: ${usbDevice?.vendorId} product_id: ${usbDevice?.productId}"
                        )
                        mUsbDevice = usbDevice
                        state = STATE_USB_CONNECTED
                        mHandler?.obtainMessage(STATE_USB_CONNECTED)?.sendToTarget()
                    } else {
                        Toast.makeText(context, mContext?.getString(R.string.user_refuse_perm) + ": ${usbDevice?.deviceName ?: "Unknown Device"}", Toast.LENGTH_LONG).show()
                        state = STATE_USB_NONE
                        mHandler?.obtainMessage(STATE_USB_NONE)?.sendToTarget()
                    }
                }
            } else if ((UsbManager.ACTION_USB_DEVICE_DETACHED == action)) {

                if (mUsbDevice != null) {
                    Toast.makeText(context, mContext?.getString(R.string.device_off), Toast.LENGTH_LONG).show()
                    closeConnectionIfExists()
                    state = STATE_USB_NONE
                    mHandler?.obtainMessage(STATE_USB_NONE)?.sendToTarget()
                }

            } else if ((UsbManager.ACTION_USB_DEVICE_ATTACHED == action)) {
//                if (mUsbDevice != null) {
//                    Toast.makeText(context, "USB device has been turned off", Toast.LENGTH_LONG).show()
//                    closeConnectionIfExists()
//                }
            }
        }
    }

    fun init(reactContext: Context?) {
        mContext = reactContext
        mUSBManager = mContext!!.getSystemService(Context.USB_SERVICE) as UsbManager

        val GetEXTRA_PERMISSION_GRANTEDIntent = Intent(ACTION_USB_PERMISSION).apply {
            putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true)
            setPackage(mContext?.packageName)
        }

        mPermissionIndent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(mContext, 0, GetEXTRA_PERMISSION_GRANTEDIntent, PendingIntent.FLAG_MUTABLE)
        } else {
            PendingIntent.getBroadcast(mContext, 0, GetEXTRA_PERMISSION_GRANTEDIntent, 0)
        }
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            mContext!!.registerReceiver(mUsbDeviceReceiver, filter, 4) // Context.RECEIVER_EXPORTED
        } else {
            mContext!!.registerReceiver(mUsbDeviceReceiver, filter)
        }
        Log.v(LOG_TAG, "ESC/POS Printer initialized")
    }

    fun closeConnectionIfExists() {
        if (mUsbDeviceConnection != null) {
            mUsbDeviceConnection!!.releaseInterface(mUsbInterface)
            mUsbDeviceConnection!!.close()
            mUsbInterface = null
            mEndPoint = null
            mInEndPoint = null
            mUsbDevice = null
            mUsbDeviceConnection = null
        }
    }

    val deviceList: List<UsbDevice>
        get() {
            if (mUSBManager == null) {
                Toast.makeText(mContext, mContext?.getString(R.string.not_usb_manager), Toast.LENGTH_LONG).show()
                return emptyList()
            }
            return ArrayList(mUSBManager!!.deviceList.values)
        }

    fun selectDevice(vendorId: Int, productId: Int): Boolean {
//        Log.v(LOG_TAG, " status usb ______ $state")
        if ((mUsbDevice == null) || (mUsbDevice!!.vendorId != vendorId) || (mUsbDevice!!.productId != productId)) {
            synchronized(printLock) {
                closeConnectionIfExists()
                val usbDevices: List<UsbDevice> = deviceList
                for (usbDevice: UsbDevice in usbDevices) {
                    if ((usbDevice.vendorId == vendorId) && (usbDevice.productId == productId)) {
                        Log.v(LOG_TAG, "Request for device: vendor_id: " + usbDevice.vendorId + ", product_id: " + usbDevice.productId)
                        closeConnectionIfExists()
                        mUSBManager!!.requestPermission(usbDevice, mPermissionIndent)
                        state = STATE_USB_CONNECTING
                        mHandler?.obtainMessage(STATE_USB_CONNECTING)?.sendToTarget()
                        return true
                    }
                }
                return false
            }
        } else {
            mHandler?.obtainMessage(state)?.sendToTarget()
        }

        return true
    }

    private fun openConnection(): Boolean {
        if (mUsbDevice == null) {
            Log.e(LOG_TAG, "USB Device is not initialized")
            return false
        }
        if (mUSBManager == null) {
            Log.e(LOG_TAG, "USB Manager is not initialized")
            return false
        }
        if (mUsbDeviceConnection != null) {
            Log.i(LOG_TAG, "USB Connection already connected")
            return true
        }
        val usbInterface = mUsbDevice!!.getInterface(0)
        val endpoints = (0 until usbInterface.endpointCount)
            .map { usbInterface.getEndpoint(it) }
            .filter { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
        val outEndpoint = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }
        if (outEndpoint == null) {
            Log.e(LOG_TAG, "USB device has no bulk OUT endpoint, so it is not a printer we can write to")
            return false
        }
        val usbDeviceConnection = mUSBManager!!.openDevice(mUsbDevice)
        if (usbDeviceConnection == null) {
            Log.e(LOG_TAG, "Failed to open USB Connection")
            return false
        }
        Toast.makeText(mContext, mContext?.getString(R.string.connected_device), Toast.LENGTH_SHORT).show()
        if (!usbDeviceConnection.claimInterface(usbInterface, true)) {
            usbDeviceConnection.close()
            Log.e(LOG_TAG, "Failed to retrieve usb connection")
            return false
        }
        mEndPoint = outEndpoint
        mInEndPoint = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_IN }
        mUsbInterface = usbInterface
        mUsbDeviceConnection = usbDeviceConnection
        return true
    }

    // See README.md, "Print results". Blocks until the printer took the job, so call it off the main thread.
    fun printBytes(bytes: ByteArray, statusTimeoutMs: Int): Map<String, Any?> {
        synchronized(printLock) {
            return writeJob(bytes, statusTimeoutMs)
        }
    }

    private fun writeJob(bytes: ByteArray, statusTimeoutMs: Int): Map<String, Any?> {
        if (!openConnection()) return PrintOutcome.failed("Could not open the USB printer. Check the cable and the USB permission.")
        val connection = mUsbDeviceConnection
            ?: return PrintOutcome.failed("USB printer connection closed before the job was sent")
        val endpoint = mEndPoint ?: return PrintOutcome.failed("USB printer has no endpoint to write to")
        val pieceSize = endpoint.maxPacketSize.coerceAtLeast(1)

        var offset = 0
        while (offset < bytes.size) {
            val length = minOf(pieceSize, bytes.size - offset)
            val written = connection.bulkTransfer(endpoint, bytes, offset, length, PIECE_TIMEOUT_MS)
            if (written != length) {
                Log.e(LOG_TAG, "USB write stopped at byte $offset of ${bytes.size}, bulkTransfer returned $written")
                return PrintOutcome.failed("USB printer did not take the job (stopped at byte $offset of ${bytes.size})")
            }
            offset += length
        }
        return PrintOutcome.delivered(readStatus(connection, endpoint, statusTimeoutMs))
    }

    private fun readStatus(connection: UsbDeviceConnection, out: UsbEndpoint, timeoutMs: Int): Int? {
        val input = mInEndPoint ?: return null
        val request = PrintOutcome.OFFLINE_CAUSE_STATUS_REQUEST
        if (connection.bulkTransfer(out, request, request.size, timeoutMs) != request.size) return null
        val buffer = ByteArray(input.maxPacketSize.coerceAtLeast(1))
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val left = (deadline - System.currentTimeMillis()).toInt()
            if (left <= 0) return null
            val read = connection.bulkTransfer(input, buffer, buffer.size, left)
            if (read < 0) return null
            for (i in 0 until read) {
                val value = buffer[i].toInt() and 0xFF
                if (PrintOutcome.isOfflineCauseStatus(value)) return value
            }
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var mInstance: USBPrinterService? = null
        private const val LOG_TAG = "ESC POS Printer"
        private const val ACTION_USB_PERMISSION = "com.flutter_pos_printer.USB_PERMISSION"

        // Constants that indicate the current connection state
        const val STATE_USB_NONE = 0 // we're doing nothing
        const val STATE_USB_CONNECTING = 2 // now initiating an outgoing connection
        const val STATE_USB_CONNECTED = 3 // now connected to a remote device

        private val printLock = Any()
        private const val PIECE_TIMEOUT_MS = 60000

        fun getInstance(handler: Handler): USBPrinterService {
            if (mInstance == null) {
                mInstance = USBPrinterService(handler)
            }
            return mInstance!!
        }
    }
}