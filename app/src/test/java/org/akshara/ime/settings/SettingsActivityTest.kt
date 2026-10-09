package org.akshara.ime.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText as hasNodeText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
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
    /** Settings is drawn entirely with Compose. */
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
            assertTrue(activity.getString(page), hasText(activity.getString(page)))
        }
        assertTrue(switches().isEmpty())   // toggles live on the pages, not the home list
    }

    @Test fun theKeyboardCanOpenSettingsOnTheClipboardPage() {
        controller.pause().stop().destroy()
        val intent = android.content.Intent(ApplicationProvider.getApplicationContext(), SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_PAGE, SettingsActivity.PAGE_CLIPBOARD)
        controller = Robolectric.buildActivity(SettingsActivity::class.java, intent).setup()
        activity = controller.get()
        assertTrue(hasText(activity.getString(R.string.clipboard_history)))
        back()
        assertTrue(hasText(activity.getString(R.string.page_sinhala)))   // Back leads Home

        controller.newIntent(intent)
        compose.waitForIdle()
        assertTrue(hasText(activity.getString(R.string.clipboard_preview)))
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
        people.forEach { assertTrue(it.name, hasText(it.name)) }
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
        assertTrue(hasText(activity.getString(R.string.page_sinhala)))
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

    @Test fun symbolHintSwitchPersistsWithoutChangingSinhalaHints() {
        openPage(R.string.page_preferences)
        val title = activity.getString(R.string.symbol_hints)
        clickRow(title)
        assertFalse(KeyboardPreferences(activity).symbolHints)
        assertTrue(KeyboardPreferences(activity).keyHints)
        compose.onNode(hasNodeText(title) and isToggleable()).assertIsOff()
        clickRow(title)
        assertTrue(KeyboardPreferences(activity).symbolHints)
        compose.onNode(hasNodeText(title) and isToggleable()).assertIsOn()
    }

    @Test fun switchesRefreshWhenPreferencesChangeWhileSettingsIsPaused() {
        openPage(R.string.page_correction)
        val title = activity.getString(R.string.suggestions)
        clickRow(title)
        compose.onNode(hasNodeText(title) and isToggleable()).assertIsOff()
        KeyboardPreferences(activity).suggestions = true
        controller.pause().resume()
        compose.waitForIdle()
        compose.onNode(hasNodeText(title) and isToggleable()).assertIsOn()
    }

    @Test @org.robolectric.annotation.Config(qualifiers = "land")
    fun themeSheetCanApplyInLandscape() {
        openPage(R.string.theme)
        clickRow(activity.getString(R.string.theme_light))
        compose.onAllNodesWithText(activity.getString(R.string.theme_apply)).onFirst()
            .performScrollTo().assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals("light", KeyboardPreferences(activity).theme)
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
        clickRow(activity.resources.getStringArray(R.array.emoji_button_entries)[1])   // in the Material 3 choice dialog
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
        scrollTo(3)   // the Typing card, which holds Preferences
        val home = activity.visibleItemIndex()
        assertTrue(home > 0)
        openPage(R.string.page_preferences)
        scrollTo(2)
        val preferences = activity.visibleItemIndex()
        assertTrue(preferences > 0)
        back()
        assertEquals(home, activity.visibleItemIndex())
        assertFalse(hasRow(activity.getString(R.string.category_keys)))   // the Preferences page is gone once Home settles
        openPage(R.string.page_preferences)
        assertEquals(preferences, activity.visibleItemIndex())
    }

    @Test fun rapidBackAndOpenDoesNotLeaveAnOldPageOrDisableControls() {
        openPage(R.string.page_sinhala)
        @Suppress("DEPRECATION") activity.onBackPressed()
        openPage(R.string.page_correction)
        assertFalse(hasText(activity.getString(R.string.v2_archaic)))
        clickRow(activity.getString(R.string.suggestions))
        assertFalse(KeyboardPreferences(activity).suggestions)
    }

    @Test fun forwardAndBackPagesSlideFromOppositeEdges() {
        val (enterForward, exitForward) = slideOffsets(forward = true, width = 1000)
        assertTrue(enterForward > 0 && exitForward < 0)   // deeper: in from the end, old page drifts to the start
        val (enterBack, exitBack) = slideOffsets(forward = false, width = 1000)
        assertTrue(enterBack < 0 && exitBack > 0)         // back: in from the start
        // Navigation itself settles on the new page and back again
        openPage(R.string.page_correction)
        assertTrue(hasText(activity.getString(R.string.category_suggestions)))
        back()
        assertTrue(hasText(activity.getString(R.string.app_name)))
    }

    private fun openPage(title: Int) = clickRow(activity.getString(title))

    private fun clickRow(title: String) {
        reveal(title)
        compose.onAllNodesWithText(title).onFirst().performClick()
        compose.waitForIdle()
    }
    private fun back() {
        @Suppress("DEPRECATION") activity.onBackPressed()
        compose.waitForIdle()
    }
    private fun scrollTo(index: Int) {
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(index)
        compose.waitForIdle()
    }
    private fun hasRow(title: String) = compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
    /** A row (its merged semantics) that shows both its title and [value]. */
    private fun rowShows(title: String, value: String) =
        compose.onAllNodes(hasNodeText(title) and hasNodeText(value)).fetchSemanticsNodes().isNotEmpty()
    private fun switches() = compose.onAllNodes(isToggleable()).fetchSemanticsNodes()
    /** Pages are lazy lists: like a person would, scroll until [text] is on screen. False if the page has no such text. */
    private fun reveal(text: String): Boolean = runCatching {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasNodeText(text))
        compose.waitForIdle()
    }.isSuccess || hasRow(text)
    private fun hasText(value: String) = reveal(value)
}
