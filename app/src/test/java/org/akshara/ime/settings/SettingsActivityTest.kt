package org.akshara.ime.settings

import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.akshara.ime.R
import org.akshara.ime.engine.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class SettingsActivityTest {
    private lateinit var controller: ActivityController<SettingsActivity>
    private lateinit var activity: SettingsActivity

    @Before fun open() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences(KeyboardPreferences.FILE, 0).edit().clear().commit()
        controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        activity = controller.get()
    }

    @Test fun usesPlatformSwitchesAndGroupedSections() {
        val root = activity.findViewById<View>(android.R.id.content)
        val switches = switches(root)
        assertTrue(switches.isNotEmpty())
        assertTrue(switches.all { it.javaClass == Switch::class.java })
        assertTrue(hasText(root, activity.getString(R.string.category_typing)))
        assertTrue(hasText(root, activity.getString(R.string.app_name)))
    }

    @Test fun togglingSuggestionsPersists() {
        val row = rowWithTitle(activity.findViewById(android.R.id.content), activity.getString(R.string.suggestions))!!
        assertTrue(KeyboardPreferences(activity).suggestions)
        row.performClick()
        assertFalse(KeyboardPreferences(activity).suggestions)
    }

    @Test fun listChoiceUpdatesSummary() {
        val root = activity.findViewById<View>(android.R.id.content)
        assertTrue(hasText(rowWithTitle(root, activity.getString(R.string.input_mode))!!, "Smart Phonetic"))
        KeyboardPreferences(activity).mode = InputMode.WIJESEKARA
        controller.pause().resume()
        val updated = rowWithTitle(activity.findViewById(android.R.id.content), activity.getString(R.string.input_mode))!!
        assertTrue(hasText(updated, "Wijesekara"))
        assertEquals(InputMode.WIJESEKARA, KeyboardPreferences(activity).mode)
    }

    private fun switches(view: View): List<Switch> {
        if (view is Switch) return listOf(view)
        if (view is ViewGroup) return (0 until view.childCount).flatMap { switches(view.getChildAt(it)) }
        return emptyList()
    }
    private fun hasText(view: View, value: String): Boolean {
        if (view is TextView && view.text.toString() == value) return true
        if (view is ViewGroup) for (i in 0 until view.childCount) if (hasText(view.getChildAt(i), value)) return true
        return false
    }
    private fun rowWithTitle(view: View, title: String): View? {
        if (view is ViewGroup) {
            val matches = (0 until view.childCount).map { view.getChildAt(it) }.any {
                it is TextView && it.id == R.id.settings_item_title && it.text.toString() == title
            }
            if (matches) return if (view.isClickable) view else (view.parent as? View) ?: view
            for (i in 0 until view.childCount) rowWithTitle(view.getChildAt(i), title)?.let { return it }
        }
        return null
    }
}
