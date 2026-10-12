package org.akshara.ime.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code the keyboard runs when it opens and while typing, so Android compiles it at install time
 * instead of interpreting it on first use.
 *
 * The keyboard has no screen of its own, so this opens a message in Google Messages and types into it: letters
 * through the keys (touch handling and the press preview), whole words (composition and suggestions), Space,
 * a suggestion, Shift, the symbols layer, a long-press and the emoji board.
 *
 * Run on a connected device or emulator with Google Messages: ./gradlew :app:generateBaselineProfile
 * Key positions are fractions of the screen for the standard keyboard height.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test fun typing() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        device.pressHome()
        shell("ime enable $IME")
        shell("ime set $IME")
        shell("am start -a android.intent.action.SENDTO -d sms:5551234")
        val field = device.wait(Until.findObject(By.text("Text message")), 10_000)
        if (field != null) field.click() else tap(0.28f, 0.934f)
        device.waitForIdle()
        Thread.sleep(2_000)

        repeat(2) {
            "kohomada".forEach { key(it) }
            key(' ')
            shell("input text mama%sgedara%syanawa%s")
            tap(0.5f, ROW_RAIL)                     // the centre suggestion
            tap(0.06f, ROW_Z)                       // Shift
            "ada".forEach { key(it) }
            key(' ')
            device.swipe(px(0.85f), py(ROW_Z), px(0.85f), py(ROW_Z), 60)   // long-press m
            device.waitForIdle()
            tap(0.06f, ROW_SPACE)                   // ?123
            tap(0.45f, ROW_Q)                       // a digit
            tap(0.06f, ROW_SPACE)                   // back to letters
            tap(0.05f, ROW_RAIL)                    // emoji board
            Thread.sleep(800)
            device.swipe(px(0.5f), py(0.85f), px(0.5f), py(0.72f), 20)
            device.pressBack()
            shell("input keycombination KEYCODE_CTRL_LEFT KEYCODE_A")
            shell("input keyevent KEYCODE_DEL")
        }
        device.pressBack()
        device.pressBack()
    }

    private fun key(char: Char) {
        val (x, y) = when (char) {
            ' ' -> 0.48f to ROW_SPACE
            in "qwertyuiop" -> (("qwertyuiop".indexOf(char) + 0.5f) / 10) to ROW_Q
            in "asdfghjkl" -> (("asdfghjkl".indexOf(char) + 1f) / 10) to ROW_A
            else -> (("zxcvbnm".indexOf(char) + 2f) / 10) to ROW_Z
        }
        tap(x, y)
    }

    private fun tap(x: Float, y: Float) {
        device.click(px(x), py(y))
        Thread.sleep(80)
    }

    private fun px(fraction: Float) = (device.displayWidth * fraction).toInt()
    private fun py(fraction: Float) = (device.displayHeight * fraction).toInt()
    private fun shell(command: String) = device.executeShellCommand(command)

    private companion object {
        const val PACKAGE = "lk.org.akshara.keyboard"
        const val IME = "$PACKAGE/org.akshara.ime.ime.AksharaInputMethodService"
        const val ROW_RAIL = 0.658f
        const val ROW_Q = 0.721f
        const val ROW_A = 0.783f
        const val ROW_Z = 0.846f
        const val ROW_SPACE = 0.910f
    }
}
