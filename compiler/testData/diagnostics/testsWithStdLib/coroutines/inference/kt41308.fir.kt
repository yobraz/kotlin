// ISSUE: KT-41308, KT-47830

fun main() {
    sequence {
        val list: List<String>? = null
        val outputList = list ?: listOf()
        yieldAll(outputList)
    }
}