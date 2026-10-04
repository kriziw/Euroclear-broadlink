package io.github.kriziw.bl3372setup.runxin

/** Product documentation is deliberately separate from executable wire profiles. */
enum class CatalogueEvidence { LOCAL_PROTOCOL, WIFI_DIRECTORY, INSPECTED_MANUAL, DIRECTORY }

data class DocumentedController(
    val name: String,
    val aliases: List<String> = emptyList(),
    val evidence: CatalogueEvidence,
    val sourceUrl: String,
    val protocolProfileId: String? = null,
) {
    fun matches(query: String): Boolean = query.trim().let { term ->
        name.contains(term, ignoreCase = true) || aliases.any { it.contains(term, ignoreCase = true) }
    }
}

/**
 * Reviewed 2026-10-04 against Runxin's official manual directory and 0WRX.466.598.
 * Directory-only entries establish product names, not capabilities or network commands.
 * No catalogue name/alias is ever interpreted as a field-1 controller identity.
 */
object ControllerCatalogue {
    private const val DIRECTORY = "https://run-xin.com/en/category126.htm"
    private const val F105_MANUAL = "https://manufacturervalve.com/pdf/download-center_22.pdf"

    private fun directory(names: List<String>) = names.map {
        DocumentedController(it, evidence = CatalogueEvidence.DIRECTORY, sourceUrl = DIRECTORY)
    }

    val entries: List<DocumentedController> = buildList {
        add(DocumentedController(
            "F79D", evidence = CatalogueEvidence.LOCAL_PROTOCOL,
            sourceUrl = "https://github.com/Danirv/ypsilon-local/blob/main/docs/f79d.md",
            protocolProfileId = ControllerProfiles.F79D.id,
        ))
        listOf("F79A", "F79B", "F82A", "F82B").forEach {
            add(DocumentedController("$it LCD Wi-Fi", evidence = CatalogueEvidence.WIFI_DIRECTORY, sourceUrl = DIRECTORY))
        }
        // Exact old/new aliases on the inspected manufacturer's manual cover.
        listOf(
            "F105AD" to "82602EH", "F105BD" to "82602FH",
            "F105AH" to "82602ED", "F105BH" to "82602FD",
            "F105AHW" to "86602ED", "F105BHW" to "86602FD", "F136BHW" to "82603FD",
        ).forEach { (name, alias) ->
            add(DocumentedController(name, listOf(alias), CatalogueEvidence.INSPECTED_MANUAL, F105_MANUAL))
        }
        // Names explicitly listed by the manufacturer; variants remain distinct.
        addAll(directory(listOf(
            "F71D", "F67D", "F65D", "F63D", "F69D", "F68D", "F79AD", "F82AD",
            "F65C", "F69C", "F147A1", "F147A3", "F133A1", "F133A3",
            "F111A1", "F111A3", "F95A1", "F95A3", "F95D1", "F95D3",
            "F130A3", "F130B3", "F92A3", "F92B3", "F96A1", "F96A3",
            "F112A1", "F112A3", "F77A1", "F77A3", "F99A1", "F99A3", "F99D1", "F99D3",
            "F74A1", "F74A3", "F74B1", "F74B3", "F116A1", "F116A3", "F117A1", "F117A3",
            "F63C1", "F63G", "F68C3", "F68G", "F65B1", "F65G", "F69A3", "F69G",
            "F118A", "F118AR", "F118B", "F118BR", "F98A", "F88A", "F73",
            "F116Q", "F117Q", "F68Q", "F69Q", "F71Q1", "F67Q1",
            "F65P1", "F63P1", "F69P1", "F68P1", "F71P1", "F67P1",
        )))
    }

    fun search(query: String): List<DocumentedController> = entries.filter { it.matches(query) }
}
