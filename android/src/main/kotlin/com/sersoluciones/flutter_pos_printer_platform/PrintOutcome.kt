package com.sersoluciones.flutter_pos_printer_platform

// See README.md, "Print results". lib/src/print_result.dart reads these maps.
object PrintOutcome {
    val OFFLINE_CAUSE_STATUS_REQUEST = byteArrayOf(0x10, 0x04, 0x02)

    // ESC/POS DLE EOT 2 reply: bits 1 and 4 are always 1, bits 0 and 7 always 0.
    fun isOfflineCauseStatus(value: Int): Boolean = value and 0x93 == 0x12

    fun failed(reason: String): Map<String, Any?> = mapOf("outcome" to "failed", "reason" to reason)

    fun sent(status: Int?, reason: String?): Map<String, Any?> =
        mapOf("outcome" to "sent", "status" to status, "reason" to reason)

    fun delivered(status: Int?): Map<String, Any?> = mapOf("outcome" to "delivered", "status" to status)
}
