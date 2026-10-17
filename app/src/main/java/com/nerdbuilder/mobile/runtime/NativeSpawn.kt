package com.nerdbuilder.mobile.runtime

object NativeSpawn {
    const val STILL_RUNNING = -2
    external fun spawn(argv: Array<String>, env: Array<String>, cwd: String, output: String, usePty: Boolean, rows: Int, cols: Int): IntArray
    external fun waitFor(pid: Int, noHang: Boolean): Int
    external fun kill(pid: Int, signal: Int): Int
    init { System.loadLibrary("nerdspawn") }
}
