// Ported from Danirv/ypsilon-local `runxin/framing.py` (Apache License 2.0); see THIRD_PARTY_NOTICES.md.
package io.github.kriziw.bl3372setup.runxin

import java.io.IOException

class RunxinProtocolException(message: String) : IOException(message)

/**
 * The Runxin "WaterDevice" frame envelope: an outer `5A 5C … A5` frame carrying an inner
 * `DF FD … DE` frame, each with an additive 8-bit checksum in its penultimate byte.
 * Field data travels as three-byte groups `[field id, byte 1, byte 2]`.
 */
object RunxinFrames {
    const val QUERY = 0x09
    const val WRITE = 0x19
    const val QUERY_RESPONSE = 0xC9
    const val WRITE_RESPONSE = 0xD9

    private const val OUTER_HEADER_SIZE = 17
    private const val OUTER_END = 0xA5
    private const val INNER_END = 0xDE

    fun build(opcode: Int, payload: List<Int>): ByteArray {
        require(opcode in 0..0xFF && payload.all { it in 0..0xFF })
        val header = intArrayOf(0x5A, 0x5C, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0x12, 0, 0)
        val inner = intArrayOf(0xDF, 0xFD, 0, opcode, *payload.toIntArray(), 0, INNER_END)
        header[15] = inner.size
        inner[2] = inner.size
        fillSum(inner)
        val frame = header + inner + intArrayOf(0, OUTER_END)
        frame[2] = frame.size
        fillSum(frame)
        return ByteArray(frame.size) { frame[it].toByte() }
    }

    fun query(fields: List<Int>): ByteArray {
        require(fields.isNotEmpty())
        return build(QUERY, fields)
    }

    fun write(groups: List<Int>): ByteArray {
        require(groups.isNotEmpty() && groups.size % 3 == 0)
        return build(WRITE, groups)
    }

    /** Validates both layers and returns the field groups of a 0xC9/0xD9 reply, keyed by id. */
    fun parseResponse(frame: ByteArray): Map<Int, Pair<Int, Int>> {
        val f = frame.map { it.toInt() and 0xFF }
        if (f.size < 25) throw RunxinProtocolException("frame too short")
        if (f[0] != 0x5A || f[1] != 0x5C) throw RunxinProtocolException("missing 5A 5C")
        if (f[2] != f.size) throw RunxinProtocolException("outer length mismatch")
        if (f.last() != OUTER_END) throw RunxinProtocolException("missing A5")
        if (f[f.size - 2] != f.dropLast(2).sum() and 0xFF) throw RunxinProtocolException("outer checksum mismatch")

        val start = (OUTER_HEADER_SIZE until f.size - 1).firstOrNull { f[it] == 0xDF && f[it + 1] == 0xFD }
            ?: throw RunxinProtocolException("missing DF FD")
        if (start + 3 > f.size) throw RunxinProtocolException("truncated inner frame")
        val inner = f.subList(start, minOf(f.size, start + f[start + 2]))
        if (inner.size != f[start + 2]) throw RunxinProtocolException("truncated inner frame")
        if (inner.last() != INNER_END) throw RunxinProtocolException("missing DE")
        if (inner[inner.size - 2] != inner.dropLast(2).sum() and 0xFF) {
            throw RunxinProtocolException("inner checksum mismatch")
        }

        val opcode = inner[3]
        if (opcode != QUERY_RESPONSE && opcode != WRITE_RESPONSE) {
            throw RunxinProtocolException("unexpected response opcode 0x%02x".format(opcode))
        }
        val data = inner.subList(4, inner.size - 2)
        if (data.size % 3 != 0) {
            // A write acknowledgement may carry a non-field payload; the frame itself is valid.
            if (opcode == WRITE_RESPONSE) return emptyMap()
            throw RunxinProtocolException("field data not divisible by three")
        }
        return data.chunked(3).associate { (id, a, b) -> id to (a to b) }
    }

    private fun fillSum(frame: IntArray) {
        frame[frame.size - 2] = frame.take(frame.size - 2).sum() and 0xFF
    }
}
