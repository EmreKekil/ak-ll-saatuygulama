package com.emrekekil.watchinfo.model

/**
 * Tek bir bilgi satırı.
 *
 * @param progress 0..1 arası doluluk; verilirse satırın altında ince bir çubuk çizilir.
 * @param alert Değer dikkat gerektiriyorsa (ör. düşük pil, dolu depolama) true.
 */
data class InfoItem(
    val label: String,
    val value: String,
    val progress: Float? = null,
    val alert: Boolean = false,
)

/** Başlıklı bilgi grubu. Başlık null ise grup başlıksız gösterilir. */
data class InfoGroup(val title: String?, val items: List<InfoItem>)

/** Bilgi gruplarını okunaklı biçimde kurmak için küçük bir DSL. */
fun infoGroups(block: InfoGroupsBuilder.() -> Unit): List<InfoGroup> =
    InfoGroupsBuilder().apply(block).groups

class InfoGroupsBuilder {
    internal val groups = mutableListOf<InfoGroup>()

    /** Boş kalan gruplar eklenmez; böylece cihazın desteklemediği bölümler kendiliğinden gizlenir. */
    fun group(title: String? = null, block: InfoItemsBuilder.() -> Unit) {
        val items = InfoItemsBuilder().apply(block).items
        if (items.isNotEmpty()) groups += InfoGroup(title, items)
    }
}

class InfoItemsBuilder {
    internal val items = mutableListOf<InfoItem>()

    /** Değer null veya boşsa satır eklenmez. */
    fun item(label: String, value: String?, progress: Float? = null, alert: Boolean = false) {
        if (!value.isNullOrBlank()) {
            items += InfoItem(label, value, progress?.coerceIn(0f, 1f), alert)
        }
    }
}
