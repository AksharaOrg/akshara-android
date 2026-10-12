package org.akshara.ime.data

import android.content.Context

/**
 * Word lists and emoji data shared by every keyboard in the process.
 *
 * Android recreates the input method service when the user switches keyboards and back, and recreates the
 * keyboard view on theme changes, while the process usually survives. Keeping the loaded data here means it is
 * read once per process instead of once per service or view.
 */
internal object KeyboardData {
    class Holder(context: Context) {
        val learning = LocalLearningStore(context)
        val prediction = PredictionRepository(context, learning)
        val autocorrection = SinhalaAutocorrection(context)
        /** Loaded on first English use; people who type only Sinhala never pay for it. */
        val english = EnglishPredictionRepository(context, learning)
        /** Parsed on first use, off the main thread when the service warms it up. */
        val emoji by lazy { EmojiRepository(context) }
    }

    private var application: Context? = null
    private var holder: Holder? = null

    /** The data for [context]'s application (a new application, as in tests, gets fresh data). */
    @Synchronized fun of(context: Context): Holder {
        val app = context.applicationContext
        if (application !== app || holder == null) {
            application = app
            holder = Holder(app)
        }
        return holder!!
    }
}
