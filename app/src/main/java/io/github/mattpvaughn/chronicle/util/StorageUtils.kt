package io.github.mattpvaughn.chronicle.util

import android.annotation.SuppressLint
import android.os.StatFs
import java.io.File

@SuppressLint("ObsoleteSdkInt")
fun File.bytesAvailable(): Long {
    val stat = StatFs(this.path)
    return stat.blockSizeLong * stat.availableBlocksLong
}
