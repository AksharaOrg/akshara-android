package org.akshara.ime.settings

import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.ui.test.hasText as hasNodeText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.akshara.ime.R
import org.akshara.ime.engine.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class SettingsActivityTest {
    /** Settings rows are Compose; page chrome (toolbar, headers, copy) is still Views. */
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<SettingsActivity>
    private lateinit var activity: SettingsActivity

    @Before fun open() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences(KeyboardPreferences.FILE, 0).edit().clear().commit()
        controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        activity = controller.get()
    }

    @Test fun homeListsSettingsPages() {
        assertTrue(hasText(activity.getString(R.string.app_name)))
        for (page in listOf(R.string.enable_keyboard, R.string.select_keyboard, R.string.page_sinhala, R.string.page_preferences, R.string.page_correction, R.string.theme,
            R.string.page_emoji, R.string.page_clipboard, R.string.category_privacy, R.string.website_title, R.string.about_title)) {
            assertTrue(activity.getString(page), hasRow(activity.getString(page)))
        }
        assertTrue(switches().isEmpty())   // toggles live on the pages, not the home list
    }

    @Test fun pagesUseMaterialSwitchesAndGroupedSections() {
        openPage(R.string.page_correction)
        assertTrue(switches().isNotEmpty())
        assertTrue(hasText(activity.getString(R.string.category_punctuation)))
        assertTrue(hasText(activity.getString(R.string.double_space_period)))
    }

    @Test fun sinhalaPageShowsSpellingOptionsOnlyWithV2() {
        openPage(R.string.page_sinhala)
        assertTrue(hasText(activity.getString(R.string.english_one_word)))
        assertTrue(hasText(activity.getString(R.string.v2_rakaransaya_u)))
        clickRow(activity.getString(R.string.smart_phonetic_v2))
        assertFalse(KeyboardPreferences(activity).smartPhoneticV2)
        assertFalse(hasText(activity.getString(R.string.v2_rakaransaya_u)))
    }

    @Test fun contributorsPageListsEveryone() {
        openPage(R.string.about_title)
        clickRow(activity.getString(R.string.credits_title))
        val people = Contributor.load(activity)
        assertEquals(listOf("Lahiru Himesh Madusanka", "Srilal Siriwardhana", "Thimira Thenuwara"), people.map { it.name })
        people.forEach { assertTrue(it.name, hasRow(it.name)) }
    }

    @Test fun contributorLinkIsOptional() {
        val people = Contributor.parse("""[{"name":"A","role":"r","link":null},{"name":"B","role":"r","link":"https://b"}]""")
        assertEquals(listOf(null, "https://b"), people.map { it.link })
    }

    @Test fun noticesCreditTheResearch() {
        openPage(R.string.about_title)
        clickRow(activity.getString(R.string.notices_title))
        assertTrue(hasText(activity.getString(R.string.notices_research_body)))
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
        clickRow(activity.getString(R.string.privacy_title))
        assertTrue(hasText(activity.getString(R.string.privacy_on_device_body)))
        @Suppress("DEPRECATION") activity.onBackPressed()
        assertTrue(hasText(activity.getString(R.string.clear_learning_title)))
        @Suppress("DEPRECATION") activity.onBackPressed()
        assertTrue(hasRow(activity.getString(R.string.page_sinhala)))
    }

    @Test fun aboutOpensIosMatchingPages() {
        clickRow(activity.getString(R.string.about_title))
        assertTrue(hasText(activity.getString(R.string.privacy_title)))
        assertTrue(hasText(activity.getString(R.string.notices_title)))
        assertTrue(hasText(activity.getString(R.string.credits_title)))
        assertTrue(hasText(activity.getString(R.string.diagnostics_title)))
        assertTrue(hasText(activity.getString(R.string.about_copyright_value)))
        assertTrue(hasText(activity.getString(R.string.about_license_value)))
        clickRow(activity.getString(R.string.privacy_title))
        assertTrue(hasText(activity.getString(R.string.privacy_on_device_body)))
    }

    @Test fun togglingSuggestionsPersists() {
        openPage(R.string.page_correction)
        assertTrue(KeyboardPreferences(activity).suggestions)
        clickRow(activity.getString(R.string.suggestions))
        assertFalse(KeyboardPreferences(activity).suggestions)
    }

    @Test fun listChoiceUpdatesSummary() {
        assertTrue(rowShows(activity.getString(R.string.page_sinhala), "Smart Phonetic, grammar-correct"))
        openPage(R.string.page_sinhala)
        assertTrue(rowShows(activity.getString(R.string.input_mode), "Smart Phonetic"))
        KeyboardPreferences(activity).mode = InputMode.WIJESEKARA
        controller.pause().resume()
        assertTrue(rowShows(activity.getString(R.string.input_mode), "Wijesekara"))
        assertEquals(InputMode.WIJESEKARA, KeyboardPreferences(activity).mode)
    }

    @Test fun emojiChoicePersistsAndPreferencesKeepFeedbackControls() {
        openPage(R.string.page_emoji)
        clickRow(activity.getString(R.string.emoji_button))
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        dialog.listView.performItemClick(dialog.listView.getChildAt(1), 1, 1)
        assertEquals(EmojiButtonPlacement.KEYBOARD, KeyboardPreferences(activity).emojiButtonPlacement)
        assertTrue(hasText("Before comma key"))
        @Suppress("DEPRECATION") activity.onBackPressed()
        openPage(R.string.page_preferences)
        for (title in listOf(R.string.key_sounds, R.string.haptics, R.string.key_hints, R.string.show_with_hardware_keyboard)) {
            assertTrue(hasText(activity.getString(title)))
        }
        @Suppress("DEPRECATION") activity.onBackPressed()
        assertTrue(hasText(activity.getString(R.string.enable_keyboard)))
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
        assertFalse(hasText(activity.getString(R.string.v2_archaic)))
        clickRow(activity.getString(R.string.suggestions))
        assertFalse(KeyboardPreferences(activity).suggestions)
    }

    @Test fun forwardAndBackPagesAnimateFromOppositeEdges() {
        layoutPage()
        tapRowWithoutSettling(activity.getString(R.string.page_correction))
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
    private fun openPage(title: Int) = clickRow(activity.getString(title))

    private fun clickRow(title: String) {
        compose.onAllNodesWithText(title).onFirst().performClick()
        compose.waitForIdle()
    }
    /**
     * Taps a row with a raw touch and runs only up to the navigation it posts, not the slide's later frames,
     * so a test can see the page mid-way (Compose's own click helpers wait for animations to end).
     */
    private fun tapRowWithoutSettling(title: String) {
        val node = { compose.onAllNodesWithText(title).onFirst().fetchSemanticsNode().boundsInWindow }
        activity.findViewById<ScrollView>(R.id.settings_scroll).scrollBy(0, (node().center.y - 320f).toInt())
        layoutPage()
        val center = node().center
        val decor = root()
        val time = android.os.SystemClock.uptimeMillis()
        for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
            val event = android.view.MotionEvent.obtain(time, time, action, center.x, center.y, 0)
            decor.dispatchTouchEvent(event)
            event.recycle()
        }
        // Run queued work one task at a time, stopping as soon as the tap's navigation has run
        val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        val before = activity.findViewById<ScrollView>(R.id.settings_scroll)
        var guard = 0
        while (activity.findViewById<ScrollView>(R.id.settings_scroll) === before && guard++ < 50) looper.runOneTask()
    }
    private fun hasRow(title: String) = compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
    /** A row (its merged semantics) that shows both its title and [value]. */
    private fun rowShows(title: String, value: String) =
        compose.onAllNodes(hasNodeText(title) and hasNodeText(value)).fetchSemanticsNodes().isNotEmpty()
    private fun switches() = compose.onAllNodes(isToggleable()).fetchSemanticsNodes()
    private fun hasText(value: String) = viewHasText(root(), value) || hasRow(value)
    private fun viewHasText(view: View, value: String): Boolean {
        if (view is TextView && view.text.toString() == value) return true
        if (view is ViewGroup) for (i in 0 until view.childCount) if (viewHasText(view.getChildAt(i), value)) return true
        return false
    }
}
