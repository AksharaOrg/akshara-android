package org.akshara.ime.settings

import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.ScrollView
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

    @Test fun homeListsSettingsPages() {
        val home = root()
        assertTrue(hasText(home, activity.getString(R.string.app_name)))
        for (page in listOf(R.string.enable_keyboard, R.string.select_keyboard, R.string.page_sinhala, R.string.page_preferences, R.string.page_correction, R.string.theme,
            R.string.page_emoji, R.string.page_clipboard, R.string.category_privacy, R.string.website_title, R.string.about_title)) {
            assertTrue(activity.getString(page), rowWithTitle(home, activity.getString(page)) != null)
        }
        assertTrue(switches(home).none { it.visibility == View.VISIBLE })   // toggles live on the pages, not the home list
    }

    @Test fun pagesUsePlatformSwitchesAndGroupedSections() {
        openPage(R.string.page_correction)
        val page = root()
        assertTrue(switches(page).isNotEmpty())
        assertTrue(switches(page).all { it.javaClass == Switch::class.java })
        assertTrue(hasText(page, activity.getString(R.string.category_punctuation)))
        assertTrue(hasText(page, activity.getString(R.string.double_space_period)))
    }

    @Test fun sinhalaPageShowsSpellingOptionsOnlyWithV2() {
        openPage(R.string.page_sinhala)
        assertTrue(hasText(root(), activity.getString(R.string.english_one_word)))
        assertTrue(hasText(root(), activity.getString(R.string.v2_rakaransaya_u)))
        rowWithTitle(root(), activity.getString(R.string.smart_phonetic_v2))!!.performClick()
        assertFalse(KeyboardPreferences(activity).smartPhoneticV2)
        assertFalse(hasText(root(), activity.getString(R.string.v2_rakaransaya_u)))
    }

    @Test fun contributorsPageListsEveryone() {
        openPage(R.string.about_title)
        rowWithTitle(root(), activity.getString(R.string.credits_title))!!.performClick()
        val people = Contributor.load(activity)
        assertEquals(listOf("Lahiru Himesh Madusanka", "Srilal Siriwardhana", "Thimira Thenuwara"), people.map { it.name })
        people.forEach { assertTrue(it.name, rowWithTitle(root(), it.name) != null) }
    }

    @Test fun contributorLinkIsOptional() {
        val people = Contributor.parse("""[{"name":"A","role":"r","link":null},{"name":"B","role":"r","link":"https://b"}]""")
        assertEquals(listOf(null, "https://b"), people.map { it.link })
    }

    @Test fun noticesCreditTheResearch() {
        openPage(R.string.about_title)
        rowWithTitle(root(), activity.getString(R.string.notices_title))!!.performClick()
        assertTrue(hasText(root(), activity.getString(R.string.notices_research_body)))
    }

    @Test fun themeSettingAppliesToSettings() {
        val night = android.content.res.Configuration.UI_MODE_NIGHT_MASK
        for ((theme, mode) in listOf("dark" to android.content.res.Configuration.UI_MODE_NIGHT_YES, "light" to android.content.res.Configuration.UI_MODE_NIGHT_NO)) {
            KeyboardPreferences(activity).theme = theme
            val themed = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
            assertEquals(theme, mode, themed.resources.configuration.uiMode and night)
        }
    }

    @Test fun backReturnsToThePreviousPage() {
        openPage(R.string.category_privacy)
        rowWithTitle(root(), activity.getString(R.string.privacy_title))!!.performClick()
        assertTrue(hasText(root(), activity.getString(R.string.privacy_on_device_body)))
        @Suppress("DEPRECATION") activity.onBackPressed()
        assertTrue(hasText(root(), activity.getString(R.string.clear_learning_title)))
        @Suppress("DEPRECATION") activity.onBackPressed()
        assertTrue(rowWithTitle(root(), activity.getString(R.string.page_sinhala)) != null)
    }

    @Test fun aboutOpensIosMatchingPages() {
        val home = activity.findViewById<View>(android.R.id.content)
        rowWithTitle(home, activity.getString(R.string.about_title))!!.performClick()
        val about = activity.findViewById<View>(android.R.id.content)
        assertTrue(hasText(about, activity.getString(R.string.privacy_title)))
        assertTrue(hasText(about, activity.getString(R.string.notices_title)))
        assertTrue(hasText(about, activity.getString(R.string.credits_title)))
        assertTrue(hasText(about, activity.getString(R.string.diagnostics_title)))
        assertTrue(hasText(about, activity.getString(R.string.about_copyright_value)))
        assertTrue(hasText(about, activity.getString(R.string.about_license_value)))
        rowWithTitle(about, activity.getString(R.string.privacy_title))!!.performClick()
        assertTrue(hasText(activity.findViewById(android.R.id.content), activity.getString(R.string.privacy_on_device_body)))
    }

    @Test fun togglingSuggestionsPersists() {
        openPage(R.string.page_correction)
        val row = rowWithTitle(root(), activity.getString(R.string.suggestions))!!
        assertTrue(KeyboardPreferences(activity).suggestions)
        row.performClick()
        assertFalse(KeyboardPreferences(activity).suggestions)
    }

    @Test fun listChoiceUpdatesSummary() {
        assertTrue(hasText(rowWithTitle(root(), activity.getString(R.string.page_sinhala))!!, "Smart Phonetic, grammar-correct"))
        openPage(R.string.page_sinhala)
        assertTrue(hasText(rowWithTitle(root(), activity.getString(R.string.input_mode))!!, "Smart Phonetic"))
        KeyboardPreferences(activity).mode = InputMode.WIJESEKARA
        controller.pause().resume()
        val updated = rowWithTitle(activity.findViewById(android.R.id.content), activity.getString(R.string.input_mode))!!
        assertTrue(hasText(updated, "Wijesekara"))
        assertEquals(InputMode.WIJESEKARA, KeyboardPreferences(activity).mode)
    }

    @Test fun emojiChoicePersistsAndPreferencesKeepFeedbackControls() {
        openPage(R.string.page_emoji)
        rowWithTitle(root(), activity.getString(R.string.emoji_button))!!.performClick()
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        dialog.listView.performItemClick(dialog.listView.getChildAt(1), 1, 1)
        assertEquals(EmojiButtonPlacement.KEYBOARD, KeyboardPreferences(activity).emojiButtonPlacement)
        assertTrue(hasText(root(), "Before comma key"))
        @Suppress("DEPRECATION") activity.onBackPressed()
        openPage(R.string.page_preferences)
        for (title in listOf(R.string.key_sounds, R.string.haptics, R.string.key_hints, R.string.show_with_hardware_keyboard)) {
            assertTrue(hasText(root(), activity.getString(title)))
        }
        @Suppress("DEPRECATION") activity.onBackPressed()
        assertTrue(hasText(root(), activity.getString(R.string.enable_keyboard)))
    }

    @Test fun navigatingBackRestoresScrollAndKeepsOnlyTheCurrentPage() {
        layoutPage()
        activity.findViewById<ScrollView>(R.id.settings_scroll).scrollTo(0, 160)
        val homeY = activity.findViewById<ScrollView>(R.id.settings_scroll).scrollY
        assertTrue(homeY > 0)
        openPage(R.string.page_preferences)
        layoutPage()
        activity.findViewById<ScrollView>(R.id.settings_scroll).scrollTo(0, 100)
        val preferencesY = activity.findViewById<ScrollView>(R.id.settings_scroll).scrollY
        assertTrue(preferencesY > 0)
        @Suppress("DEPRECATION") activity.onBackPressed()
        layoutPage()
        assertEquals(homeY, activity.findViewById<ScrollView>(R.id.settings_scroll).scrollY)
        openPage(R.string.page_preferences)
        layoutPage()
        assertEquals(preferencesY, activity.findViewById<ScrollView>(R.id.settings_scroll).scrollY)
        assertEquals(1, activity.findViewById<ViewGroup>(R.id.settings_pages).childCount)
    }

    @Test fun rapidBackAndOpenDoesNotLeaveAnOldPageOrDisableControls() {
        layoutPage()
        openPage(R.string.page_sinhala)
        layoutPage()
        @Suppress("DEPRECATION") activity.onBackPressed()
        openPage(R.string.page_correction)
        layoutPage()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(300, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals(1, activity.findViewById<ViewGroup>(R.id.settings_pages).childCount)
        assertFalse(hasText(root(), activity.getString(R.string.v2_archaic)))
        rowWithTitle(root(), activity.getString(R.string.suggestions))!!.performClick()
        assertFalse(KeyboardPreferences(activity).suggestions)
    }

    @Test fun forwardAndBackPagesAnimateFromOppositeEdges() {
        layoutPage()
        openPage(R.string.page_correction)
        layoutPage()
        assertTrue(activity.findViewById<ScrollView>(R.id.settings_scroll).translationX > 0f)
        @Suppress("DEPRECATION") activity.onBackPressed()
        layoutPage()
        val home = activity.findViewById<ScrollView>(R.id.settings_scroll)
        assertTrue(home.translationX < 0f)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(300, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals(0f, home.translationX, 0.01f)
    }

    private fun layoutPage() {
        root().measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
        root().layout(0, 0, 480, 640)
        root().viewTreeObserver.dispatchOnPreDraw()
    }

    private fun root(): View = activity.findViewById(android.R.id.content)
    private fun openPage(title: Int) = rowWithTitle(root(), activity.getString(title))!!.performClick()

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
