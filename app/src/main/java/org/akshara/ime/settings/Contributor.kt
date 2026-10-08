package org.akshara.ime.settings

import android.content.Context
import org.akshara.ime.R
import org.json.JSONArray

/** A person shown under Settings → About → Contributors. */
data class Contributor(val name: String, val role: String, val link: String?) {
    companion object {
        /** Reads `res/raw/contributors.json`: `[{ "name", "role", "link" (URL or null) }]`, in display order. */
        fun load(context: Context): List<Contributor> {
            val json = context.resources.openRawResource(R.raw.contributors).bufferedReader().use { it.readText() }
            return parse(json)
        }

        fun parse(json: String): List<Contributor> {
            val array = JSONArray(json)
            return (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                Contributor(
                    name = item.getString("name"),
                    role = item.getString("role"),
                    link = item.optString("link").takeIf { !item.isNull("link") && it.isNotBlank() }
                )
            }
        }
    }
}
