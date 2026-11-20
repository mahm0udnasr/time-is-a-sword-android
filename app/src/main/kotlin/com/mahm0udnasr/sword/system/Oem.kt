package com.mahm0udnasr.sword.system

import android.os.Build
import com.mahm0udnasr.sword.R

/** Brand-specific battery advice (step 9.5): these phones stop background services unless told not to. */
object Oem {
    fun guide(manufacturer: String = Build.MANUFACTURER): Int = when (manufacturer.lowercase()) {
        "samsung" -> R.string.oem_samsung
        "xiaomi", "redmi", "poco" -> R.string.oem_xiaomi
        "oppo", "realme", "oneplus" -> R.string.oem_oppo
        "vivo", "iqoo" -> R.string.oem_vivo
        "huawei", "honor" -> R.string.oem_huawei
        else -> R.string.oem_other
    }
}
