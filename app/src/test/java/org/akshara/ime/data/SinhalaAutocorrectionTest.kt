package org.akshara.ime.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SinhalaAutocorrectionTest {
    @Test fun verifiedWordsStayUntouchedAndOneEditMistakesCorrect() {
        val service = SinhalaAutocorrection(ApplicationProvider.getApplicationContext<android.content.Context>())
        assertNull(service.correction("ගෙදර"))
        assertEquals("ගෙදර", service.correction("ගෙදරා"))
    }
}
