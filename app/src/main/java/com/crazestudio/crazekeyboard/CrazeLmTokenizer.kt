package com.crazestudio.crazekeyboard

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer
import java.util.PriorityQueue

/**
 * Pure-Kotlin reader/encoder for CrazeLM's exact SentencePiece BPE model.
 *
 * Memory/speed design (the vocabulary has 100,000 pieces):
 *  - all piece strings live in one UTF-8 byte blob (~1 MB) instead of 100K String objects plus a
 *    HashMap<String, Int> with boxed values (~10+ MB);
 *  - pieces are found through an open-addressing int table keyed by a hash of their bytes;
 *  - encoding runs BPE on byte ranges of the input with a priority queue, so it does no string
 *    concatenation and costs O(n log n) instead of rescanning every adjacent pair after each merge.
 * The merge order is identical to the original greedy rule (best score first, leftmost on ties).
 */
internal class CrazeLmTokenizer private constructor(
    private val blob: ByteArray,
    private val offsets: IntArray,       // piece i = blob[offsets[i] until offsets[i + 1]]
    private val scores: FloatArray,
    private val table: IntArray,         // open addressing; stores piece id + 1, 0 = empty
    private val byteIds: IntArray
) {
    private val mask = table.size - 1
    val vocabularySize: Int get() = scores.size

    private class Merge(val left: Int, val right: Int, val score: Float, val leftVersion: Int, val rightVersion: Int)

    fun piece(id: Int): String {
        if (id < 0 || id >= scores.size) return "<unk>"
        return String(blob, offsets[id], offsets[id + 1] - offsets[id], Charsets.UTF_8)
    }

    /** The word of a "▁word" piece with the marker removed, or null for any other piece. */
    fun wordPiece(id: Int): String? {
        if (id < 0 || id >= scores.size) return null
        val start = offsets[id]
        val length = offsets[id + 1] - start
        if (length <= 3 || blob[start] != 0xE2.toByte() || blob[start + 1] != 0x96.toByte() ||
            blob[start + 2] != 0x81.toByte()) return null
        return String(blob, start + 3, length - 3, Charsets.UTF_8)
    }

    private fun hash(source: ByteArray, start: Int, end: Int): Int {
        var h = -0x7ee3623b // FNV-1a offset basis
        for (i in start until end) h = (h xor (source[i].toInt() and 0xff)) * 16777619
        return h xor (h ushr 16)
    }

    private fun sameBytes(id: Int, source: ByteArray, start: Int, length: Int): Boolean {
        val o = offsets[id]
        if (offsets[id + 1] - o != length) return false
        for (k in 0 until length) if (blob[o + k] != source[start + k]) return false
        return true
    }

    /** Id of the normal piece equal to source[start, end), or -1. */
    private fun find(source: ByteArray, start: Int, end: Int): Int {
        val length = end - start
        var slot = hash(source, start, end) and mask
        while (true) {
            val entry = table[slot]
            if (entry == 0) return -1
            val id = entry - 1
            if (sameBytes(id, source, start, length)) return id
            slot = (slot + 1) and mask
        }
    }

    private fun register(id: Int) {
        val start = offsets[id]
        val length = offsets[id + 1] - start
        var slot = hash(blob, start, start + length) and mask
        while (true) {
            val entry = table[slot]
            if (entry == 0 || sameBytes(entry - 1, blob, start, length)) {
                table[slot] = id + 1   // a later duplicate replaces an earlier one, like the old HashMap
                return
            }
            slot = (slot + 1) and mask
        }
    }

    fun encode(raw: String): IntArray {
        val normalized = collapseWhitespace(Normalizer.normalize(raw, Normalizer.Form.NFKC))
        if (normalized.isEmpty()) return IntArray(0)
        val prepared = buildString(normalized.length + 8) {
            append('▁')
            normalized.forEach { ch -> if (ch == ' ') append('▁') else append(ch) }
        }
        val source = prepared.toByteArray(Charsets.UTF_8)

        // One initial segment per code point (UTF-8 lead byte starts a segment).
        var count = 0
        for (b in source) if ((b.toInt() and 0xC0) != 0x80) count++
        val segStart = IntArray(count)
        val segEnd = IntArray(count)
        var index = 0
        for (i in source.indices) {
            if ((source[i].toInt() and 0xC0) != 0x80) {
                if (index > 0) segEnd[index - 1] = i
                segStart[index++] = i
            }
        }
        segEnd[count - 1] = source.size

        val next = IntArray(count) { if (it + 1 < count) it + 1 else -1 }
        val prev = IntArray(count) { it - 1 }
        val alive = BooleanArray(count) { true }
        val version = IntArray(count)
        val queue = PriorityQueue<Merge>(count + 8) { a, b ->
            val byScore = b.score.compareTo(a.score)        // higher score first
            if (byScore != 0) byScore else a.left.compareTo(b.left)   // leftmost on ties
        }
        fun offer(left: Int, right: Int) {
            val id = find(source, segStart[left], segEnd[right])
            if (id >= 0) queue.add(Merge(left, right, scores[id], version[left], version[right]))
        }
        for (i in 0 until count - 1) offer(i, i + 1)
        while (true) {
            val merge = queue.poll() ?: break
            val l = merge.left
            val r = merge.right
            // Entries made stale by an earlier merge are skipped.
            if (!alive[l] || !alive[r] || next[l] != r ||
                version[l] != merge.leftVersion || version[r] != merge.rightVersion) continue
            segEnd[l] = segEnd[r]
            alive[r] = false
            val after = next[r]
            next[l] = after
            if (after >= 0) prev[after] = l
            version[l]++
            if (prev[l] >= 0) offer(prev[l], l)
            if (after >= 0) offer(l, after)
        }

        val result = ArrayList<Int>(count)
        var node = 0
        while (node >= 0) {
            val id = find(source, segStart[node], segEnd[node])
            if (id >= 0) result.add(id)
            else for (k in segStart[node] until segEnd[node]) {
                val byteId = byteIds[source[k].toInt() and 0xff]
                result.add(if (byteId < 0) 1 else byteId)
            }
            node = next[node]
        }
        return result.toIntArray()
    }

    private fun collapseWhitespace(value: String): String {
        val out = StringBuilder(value.length)
        var pendingSpace = false
        for (ch in value) {
            if (Character.isWhitespace(ch) || Character.isSpaceChar(ch)) {
                if (out.isNotEmpty()) pendingSpace = true
            } else {
                if (pendingSpace) out.append(' ')
                pendingSpace = false
                out.append(ch)
            }
        }
        return out.toString().trim()
    }

    companion object {
        private const val EXPECTED_PIECES = 100_000

        private fun hexValue(b: Byte): Int {
            val c = b.toInt()
            return when (c) {
                in 48..57 -> c - 48
                in 65..70 -> c - 55
                in 97..102 -> c - 87
                else -> -1
            }
        }

        fun load(file: File): CrazeLmTokenizer {
            require(file.isFile && file.length() > 0L) { "CrazeLM tokenizer file is missing." }
            val data = file.readBytes()
            val offsets = IntArray(EXPECTED_PIECES + 1)
            val scores = FloatArray(EXPECTED_PIECES)
            val types = ByteArray(EXPECTED_PIECES)
            val packed = ByteArray(data.size)
            var used = 0
            var count = 0
            val reader = ProtoReader(data)
            while (!reader.done()) {
                val (field, wire) = reader.readTag()
                if (field == 1 && wire == 2) {
                    val length = reader.varint()
                    require(length >= 0 && length <= (data.size - reader.position).toLong()) {
                        "Invalid SentencePiece record length $length at byte ${reader.position}."
                    }
                    val end = reader.position + length.toInt()
                    require(count < EXPECTED_PIECES) { "Tokenizer has more than $EXPECTED_PIECES pieces." }
                    offsets[count] = used
                    used = parsePiece(data, reader.position, end, packed, used, count, scores, types)
                    count++
                    reader.setPosition(end)
                } else reader.skipField(field, wire)
            }
            require(count == EXPECTED_PIECES) { "Expected 100,000 tokenizer pieces, found $count." }
            offsets[count] = used
            val blob = packed.copyOf(used)

            var normalCount = 0
            val byteIds = IntArray(256) { -1 }
            for (id in 0 until count) {
                if (types[id].toInt() == 1) normalCount++
                val o = offsets[id]
                // Byte-fallback pieces look like "<0xAB>".
                if (offsets[id + 1] - o == 6 && blob[o] == '<'.code.toByte() && blob[o + 1] == '0'.code.toByte() &&
                    blob[o + 2] == 'x'.code.toByte() && blob[o + 5] == '>'.code.toByte()) {
                    val hi = hexValue(blob[o + 3])
                    val lo = hexValue(blob[o + 4])
                    if (hi >= 0 && lo >= 0) byteIds[hi * 16 + lo] = id
                }
            }
            require(normalCount > 0) { "Tokenizer contains no normal BPE pieces." }
            require(byteIds.all { it >= 0 }) { "Tokenizer is missing UTF-8 byte fallback pieces." }
            var tableSize = 1
            while (tableSize < normalCount * 2 + 2) tableSize = tableSize shl 1
            val tokenizer = CrazeLmTokenizer(blob, offsets, scores, IntArray(tableSize), byteIds)
            for (id in 0 until count) if (types[id].toInt() == 1) tokenizer.register(id)

            // Fixed vectors generated by Python SentencePiece from this exact release.
            require(tokenizer.encode("I am going to").contentEquals(intArrayOf(332, 823, 2536, 293))) {
                "SentencePiece parity check failed for the CrazeLM reference prompt."
            }
            require(tokenizer.encode("hello world").contentEquals(intArrayOf(81353, 1291))) {
                "SentencePiece parity check failed for the CrazeLM word-boundary prompt."
            }
            return tokenizer
        }

        /** Copies the piece text into [packed] at [position] and fills score/type; returns the new position. */
        private fun parsePiece(
            data: ByteArray, start: Int, end: Int, packed: ByteArray, position: Int,
            index: Int, scores: FloatArray, types: ByteArray
        ): Int {
            val reader = ProtoReader(data, start, end)
            var next = position
            var tokenLength = 0
            var score = 0f
            var type = 1
            while (!reader.done()) {
                val (field, wire) = reader.readTag()
                when {
                    field == 1 && wire == 2 -> {
                        val len = reader.varint()
                        require(len >= 0 && len <= (end - reader.position).toLong()) {
                            "Invalid piece string length $len at byte ${reader.position}."
                        }
                        val byteLength = len.toInt()
                        System.arraycopy(data, reader.position, packed, position, byteLength)
                        tokenLength = byteLength
                        next = position + byteLength
                        reader.setPosition(reader.position + byteLength)
                    }
                    field == 2 && wire == 5 -> {
                        require(reader.position + 4 <= end) { "Truncated SentencePiece score." }
                        score = ByteBuffer.wrap(data, reader.position, 4)
                            .order(ByteOrder.LITTLE_ENDIAN).float
                        reader.setPosition(reader.position + 4)
                    }
                    field == 3 && wire == 0 -> type = reader.varint().toInt()
                    else -> reader.skipField(field, wire)
                }
            }
            require(tokenLength > 0) { "SentencePiece entry has no token string." }
            scores[index] = score
            types[index] = type.toByte()
            return next
        }
    }

    private class ProtoReader(
        private val bytes: ByteArray, start: Int = 0, private val limit: Int = bytes.size
    ) {
        var position: Int = start
            private set

        init {
            require(start >= 0 && limit >= start && limit <= bytes.size) {
                "Invalid protobuf reader bounds."
            }
        }

        fun done(): Boolean = position == limit

        fun varint(): Long {
            var result = 0L
            for (index in 0 until 10) {
                require(position < limit) { "Truncated protobuf varint at byte $position." }
                val b = bytes[position++].toInt() and 0xff
                if (index == 9) {
                    require(b <= 1) { "Protobuf varint overflows 64 bits at byte ${position - 1}." }
                }
                result = result or ((b and 0x7f).toLong() shl (index * 7))
                if ((b and 0x80) == 0) return result
            }
            throw IllegalArgumentException("Invalid protobuf varint at byte $position.")
        }

        fun readTag(): Pair<Int, Int> {
            val offset = position
            val raw = varint()
            require(raw in 1..0x7fffffffL) { "Invalid protobuf tag $raw at byte $offset." }
            val field = (raw ushr 3).toInt()
            val wire = (raw and 7).toInt()
            require(field > 0) { "Invalid protobuf field number at byte $offset." }
            require(wire in 0..5) {
                "Invalid/reserved protobuf wire type $wire at byte $offset (tag=$raw). " +
                    "The data may be truncated, corrupted, or parsed from the wrong offset."
            }
            return field to wire
        }

        fun skipField(fieldNumber: Int, wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> advance(8)
                2 -> {
                    val length = varint()
                    require(length >= 0 && length <= (limit - position).toLong()) {
                        "Invalid protobuf length $length at byte $position."
                    }
                    advance(length.toInt())
                }
                3 -> {
                    // Groups are deprecated but valid in the protobuf wire format.
                    while (true) {
                        require(position < limit) { "Truncated protobuf group for field $fieldNumber." }
                        val (nestedField, nestedWire) = readTagAllowEndGroup()
                        if (nestedWire == 4) {
                            require(nestedField == fieldNumber) {
                                "Mismatched protobuf END_GROUP: expected field $fieldNumber, got $nestedField."
                            }
                            break
                        }
                        skipField(nestedField, nestedWire)
                    }
                }
                4 -> throw IllegalArgumentException(
                    "Unexpected protobuf END_GROUP for field $fieldNumber at byte $position."
                )
                5 -> advance(4)
                else -> throw IllegalArgumentException("Unsupported protobuf wire type $wire.")
            }
        }

        private fun readTagAllowEndGroup(): Pair<Int, Int> {
            val offset = position
            val raw = varint()
            require(raw in 1..0x7fffffffL) { "Invalid protobuf tag $raw at byte $offset." }
            val field = (raw ushr 3).toInt()
            val wire = (raw and 7).toInt()
            require(field > 0) { "Invalid protobuf field number at byte $offset." }
            require(wire in 0..5) {
                "Invalid/reserved protobuf wire type $wire at byte $offset (tag=$raw)."
            }
            return field to wire
        }

        private fun advance(count: Int) {
            require(count >= 0 && count <= limit - position) {
                "Truncated protobuf field at byte $position (need $count bytes)."
            }
            position += count
        }

        fun setPosition(value: Int) {
            require(value >= position && value <= limit) { "Invalid protobuf seek to $value." }
            position = value
        }
    }
}
