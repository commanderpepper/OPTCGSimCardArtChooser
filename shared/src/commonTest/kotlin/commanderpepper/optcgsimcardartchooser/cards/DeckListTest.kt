package commanderpepper.optcgsimcardartchooser.cards

import kotlin.test.Test
import kotlin.test.assertEquals

class DeckListTest {

    @Test
    fun readsTheSimsExportFormat() {
        // A real deck exported from OPTCGSim.
        val export = """
            1xOP09-061
            4xEB05-033
            4xOP09-069
            4xPRB02-012
            4xOP09-076
            4xST18-001
            3xST18-002
            4xST26-005
            4xOP05-119
            4xOP17-095
            4xOP15-078
            4xOP05-076
            4xOP09-078
            3xOP18-077
        """.trimIndent()
        assertEquals(
            listOf(
                "OP09-061", "EB05-033", "OP09-069", "PRB02-012", "OP09-076", "ST18-001", "ST18-002",
                "ST26-005", "OP05-119", "OP17-095", "OP15-078", "OP05-076", "OP09-078", "OP18-077",
            ),
            DeckList.parse(export),
        )
    }

    @Test
    fun acceptsOtherCommonWritingStyles() {
        val text = """
            4 OP01-001
            4x OP01-002 Trafalgar Law
            OP01-003 x4
            op01-004
            2×P-001
            Leader: OP01-005

            // a comment line without a card
        """.trimIndent()
        assertEquals(listOf("OP01-001", "OP01-002", "OP01-003", "OP01-004", "P-001", "OP01-005"), DeckList.parse(text))
    }

    @Test
    fun quantityIsNotMistakenForPartOfTheId() {
        // In "4xP-001" the "x" must not be read as part of a prefix like "XP".
        assertEquals(listOf("P-001", "ST01-001"), DeckList.parse("4xP-001\n4xST01-001"))
    }

    @Test
    fun duplicatesKeepTheirFirstPosition() {
        assertEquals(listOf("OP01-001", "OP01-002"), DeckList.parse("2xOP01-001\n1xOP01-002\n2xOP01-001"))
    }

    @Test
    fun blankOrCardlessTextGivesNothing() {
        assertEquals(emptyList(), DeckList.parse(""))
        assertEquals(emptyList(), DeckList.parse("my deck\n4x something"))
    }
}
