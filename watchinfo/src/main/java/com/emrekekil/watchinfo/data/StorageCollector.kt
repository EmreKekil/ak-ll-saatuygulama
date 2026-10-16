package com.emrekekil.watchinfo.data

import android.app.admin.DevicePolicyManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import com.emrekekil.watchinfo.R
import com.emrekekil.watchinfo.model.InfoGroup
import com.emrekekil.watchinfo.model.infoGroups

object StorageCollector {

    data class Usage(val used: Long, val total: Long) {
        val fraction: Float get() = if (total > 0) used.toFloat() / total else 0f
    }

    fun usage(): Usage {
        val stat = StatFs(Environment.getDataDirectory().path)
        return Usage(stat.totalBytes - stat.availableBytes, stat.totalBytes)
    }

    // Depolama değerleri Android'in Ayarlar ekranıyla aynı olması için 1000 tabanlı gösterilir.
    fun summary(): String {
        val usage = usage()
        return "${Format.bytes(usage.used, si = true)} / ${Format.bytes(usage.total, si = true)}"
    }

    /** /proc/self/mounts içinde verilen bağlama noktasının dosya sistemi türü. */
    private fun fileSystemOf(mountPoint: String): String? =
        SysFs.read("/proc/self/mounts").orEmpty().lineSequence()
            .map { it.split(' ') }
            .firstOrNull { it.size > 2 && it[1] == mountPoint }
            ?.get(2)

    fun collect(context: Context): List<InfoGroup> {
        val data = StatFs(Environment.getDataDirectory().path)
        val used = data.totalBytes - data.availableBytes
        val fraction = if (data.totalBytes > 0) used.toDouble() / data.totalBytes else 0.0
        val capacity = try {
            context.getSystemService(StorageStatsManager::class.java).getTotalBytes(StorageManager.UUID_DEFAULT)
        } catch (_: Exception) {
            null
        }
        val system = StatFs(Environment.getRootDirectory().path)
        val encryption = when (context.getSystemService(DevicePolicyManager::class.java).storageEncryptionStatus) {
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER -> R.string.value_encryption_fbe
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE,
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY -> R.string.value_encryption_full
            DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED -> R.string.value_not_supported
            else -> R.string.value_off
        }

        return infoGroups {
            group(context.getString(R.string.group_internal_storage)) {
                item(context.getString(R.string.label_storage_capacity), capacity?.let { Format.bytes(it, si = true) })
                item(context.getString(R.string.label_storage_user), Format.bytes(data.totalBytes, si = true))
                item(
                    context.getString(R.string.label_storage_used),
                    "${Format.bytes(used, si = true)} (${Format.percent(fraction)})",
                    progress = fraction.toFloat(),
                    alert = fraction >= 0.9,
                )
                item(context.getString(R.string.label_storage_free), Format.bytes(data.availableBytes, si = true))
                item(context.getString(R.string.label_file_system), fileSystemOf("/data"))
                item(context.getString(R.string.label_encryption), context.getString(encryption))
            }
            group(context.getString(R.string.group_system_partition)) {
                item(context.getString(R.string.label_storage_user), Format.bytes(system.totalBytes, si = true))
                item(context.getString(R.string.label_file_system), fileSystemOf("/"))
            }
        }
    }
}
