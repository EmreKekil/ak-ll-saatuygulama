package com.emrekekil.qibla.geo

import java.io.File

/** Test kaynaklarını ve uygulama varlıklarını okuma yardımcıları. */
object TestData {
    fun resource(name: String): String =
        requireNotNull(TestData::class.java.classLoader!!.getResource(name)) { "Kaynak yok: $name" }.readText()

    /** Gradle testleri modül klasöründe çalıştırır; varlıklar oradan okunur. */
    fun asset(name: String): String = File("src/main/assets/$name").readText()

    /** Başlık satırlı basit CSV; tırnak içindeki virgülleri korur. */
    fun csv(text: String): List<Map<String, String>> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        val header = split(lines.first())
        return lines.drop(1).map { line -> header.zip(split(line)).toMap() }
    }

    private fun split(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        for (ch in line) {
            when {
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> {
                    cells += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        cells += current.toString()
        return cells
    }
}
