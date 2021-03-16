package io.github.mattpvaughn.chronicle.util

import android.os.StatFs
import java.io.File

fun File.bytesAvailable(): Long {
    // In the case of SD card removal or setting as on-board memory
    if (!this.exists()) {
        return 0
    }
    val stat = StatFs(this.path)
    return stat.blockSizeLong * stat.availableBlocksLong
}
