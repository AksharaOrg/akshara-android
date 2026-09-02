package org.akshara.ime.engine

import org.junit.Assert.*
import org.junit.Test

class CompositionSessionTest {
    @Test fun typePreviewBackspaceAndCancel() {
        val session = CompositionSession()
        assertEquals("ක්", session.type("k", InputMode.PHONETIC))
        assertEquals("ක", session.type("a", InputMode.PHONETIC))
        assertEquals("ක්", session.backspace(InputMode.PHONETIC))
        session.clear(); assertFalse(session.active); assertEquals("", session.source)
    }
    @Test fun backspaceRewindsConjunctAndKombuwa() {
        val session = CompositionSession()
        session.type("kya", InputMode.PHONETIC)
        assertEquals("ක්‍ය", session.rendered)
        session.backspace(InputMode.PHONETIC)
        assertTrue(session.rendered.isNotEmpty())
        session.clear()
        session.replace(SinhalaEngine.normalizeSls("ෙක"))
        assertEquals("කෙ", session.rendered)
    }
    @Test fun modeChangeCanCommitRenderedThenClear() {
        val session = CompositionSession(); session.type("amma", InputMode.PHONETIC)
        val committed = session.rendered; session.clear()
        assertEquals("අම්ම", committed); assertFalse(session.active)
    }
}
