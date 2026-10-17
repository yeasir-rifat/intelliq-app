package com.intelliq.rifatplex.data

/** The black / white modules of a QR code without any quiet zone. [size] x [size]. */
class QrMatrix(val size: Int, private val bits: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = bits[y * size + x]

    /** Horizontal runs of black modules as (row, firstColumn, length): one rectangle each when drawn. */
    fun runs(): List<IntArray> {
        val out = ArrayList<IntArray>()
        for (y in 0 until size) {
            var x = 0
            while (x < size) {
                if (!get(x, y)) {
                    x++
                    continue
                }
                var e = x
                while (e < size && get(e, y)) e++
                out.add(intArrayOf(y, x, e - x))
                x = e
            }
        }
        return out
    }
}
