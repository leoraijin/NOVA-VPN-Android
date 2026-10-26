package com.nova.vpn.android.data

internal data class SubscriptionUsage(val used: Long?, val total: Long?, val expires: Long?) {
    companion object {
        fun parse(header: String?): SubscriptionUsage {
            val values = header.orEmpty().take(2048).split(';').mapNotNull { field ->
                val pair = field.trim().split('=', limit = 2)
                if (pair.size != 2) null else pair[1].trim().toLongOrNull()?.takeIf { it >= 0 }
                    ?.let { pair[0].trim().lowercase(java.util.Locale.ROOT) to it }
            }.toMap()
            val used = if (values["upload"] != null && values["download"] != null)
                runCatching { Math.addExact(values.getValue("upload"), values.getValue("download")) }.getOrNull() else null
            return SubscriptionUsage(used, values["total"], values["expire"]?.takeIf { it > 0 && it <= 253402300799L })
        }
    }
}
