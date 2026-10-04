package io.github.kriziw.bl3372setup

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import java.util.Locale

/** The app's own language choice. [SYSTEM] follows the phone (Hungarian unless it is English). */
enum class LanguageOption(val tag: String?) {
    SYSTEM(null),
    HUNGARIAN("hu"),
    ENGLISH("en");

    companion object {
        /** Maps a stored or system-reported language tag (e.g. `hu-HU`) to an option. */
        fun fromTag(tag: String?): LanguageOption {
            val language = tag?.takeIf { it.isNotBlank() }?.let { Locale.forLanguageTag(it).language }
            return entries.firstOrNull { it.tag != null && it.tag == language } ?: SYSTEM
        }
    }
}

/**
 * In-app language switching without AppCompat.
 *
 * Android 13+ has per-app languages built in ([LocaleManager]); using it keeps the choice in sync
 * with *Settings → Apps → Language* and the system restarts the activity itself. On Android 10–12
 * the choice is stored here and applied to the activity's context in [wrap].
 */
object AppLanguage {
    private const val PREFS = "settings"
    private const val KEY = "language"

    fun current(context: Context): LanguageOption =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LanguageOption.fromTag(localeManager(context).applicationLocales.toLanguageTags())
        } else {
            LanguageOption.fromTag(prefs(context).getString(KEY, null))
        }

    fun set(activity: Activity, option: LanguageOption) {
        if (option == current(activity)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            localeManager(activity).applicationLocales = LocaleList.forLanguageTags(option.tag.orEmpty())
        } else {
            prefs(activity).edit(commit = true) { putString(KEY, option.tag) }
            activity.recreate()
        }
    }

    /** Applies the stored choice to an activity's base context (Android 10–12 only). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = LanguageOption.fromTag(prefs(base).getString(KEY, null)).tag ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return base.createConfigurationContext(config)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun localeManager(context: Context) = context.getSystemService(LocaleManager::class.java)
}
