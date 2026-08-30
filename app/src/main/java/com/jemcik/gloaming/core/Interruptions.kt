package com.jemcik.gloaming.core

import android.content.res.Resources
import android.icu.text.ListFormatter
import com.jemcik.gloaming.R

/**
 * The DND policy, in the app's own words.
 *
 * Both screens describe the same set of exceptions - the home card summarises
 * them, the detail screen edits them - so the vocabulary lives here rather than
 * being written twice and drifting.
 *
 * Everything here takes [Resources] and returns finished strings. It used to
 * return English fragments and let the callers glue them with " and " and a
 * comma, which is the classic way to make a screen untranslatable: conjunction
 * placement differs between languages, and an item dropped into a sentence may
 * need a different grammatical form than the same item standing alone. So the
 * items carry their own in-sentence wording, and the joining is done by
 * [ListFormatter], which knows each locale's own conjunction and punctuation.
 *
 * Values are ZenPolicy's own: PEOPLE_TYPE_* is 1 anyone, 2 contacts, 3 starred,
 * 4 none; CONVERSATION_SENDERS_* is 1 anyone, 2 important, 3 none.
 */
object Interruptions {

    const val PEOPLE_ANYONE = 1
    const val PEOPLE_CONTACTS = 2
    const val PEOPLE_STARRED = 3
    const val PEOPLE_NONE = 4

    const val CONV_ANYONE = 1
    const val CONV_IMPORTANT = 2
    const val CONV_NONE = 3

    fun peopleLabel(res: Resources, v: Int): String = res.getString(
        when (v) {
            PEOPLE_ANYONE -> R.string.people_anyone
            PEOPLE_CONTACTS -> R.string.people_contacts
            PEOPLE_STARRED -> R.string.people_starred
            else -> R.string.people_none
        }
    )

    fun convLabel(res: Resources, v: Int): String = res.getString(
        when (v) {
            CONV_ANYONE -> R.string.conv_all
            CONV_IMPORTANT -> R.string.conv_priority
            else -> R.string.state_blocked
        }
    )

    /**
     * What can still reach you, most important first. Alarms lead because they
     * are always allowed - see [Prefs.allowAlarms], which the UI never clears.
     *
     * [short] drops the scope from calls - "calls", not "calls from starred
     * contacts" - for the home card, which has one line. It is a separate string
     * rather than a substring of the long one: cutting at " from " only works in
     * a language that happens to use that word.
     */
    fun allowed(
        res: Resources,
        calls: Int, messages: Int, conversations: Int,
        repeatCallers: Boolean, reminders: Boolean, events: Boolean, media: Boolean,
        short: Boolean = false
    ): List<String> = buildList {
        add(res.getString(R.string.item_alarms))
        if (calls != PEOPLE_NONE) add(
            res.getString(
                when {
                    short || calls == PEOPLE_ANYONE -> R.string.item_calls
                    calls == PEOPLE_CONTACTS -> R.string.item_calls_from_contacts
                    else -> R.string.item_calls_from_starred
                }
            )
        )
        if (messages != PEOPLE_NONE) add(res.getString(R.string.item_messages))
        if (conversations != CONV_NONE) add(res.getString(R.string.item_conversations))
        if (repeatCallers) add(res.getString(R.string.item_repeat_callers))
        if (reminders) add(res.getString(R.string.item_reminders))
        if (events) add(res.getString(R.string.item_events))
        if (media) add(res.getString(R.string.item_media))
    }

    /* Taking the values rather than Prefs keeps the editing screen honest: it
       holds its own state and writes through, so reading Prefs back mid-edit
       would describe the last save rather than what is on screen. */
    fun allowed(res: Resources, p: Prefs, short: Boolean = false): List<String> = allowed(
        res,
        p.allowCalls, p.allowMessages, p.allowConversations,
        p.allowRepeatCallers, p.allowReminders, p.allowEvents, p.allowMedia,
        short
    )

    /**
     * The locale's own list punctuation and conjunction - "a, b and c".
     *
     * Takes the locale from the RESOURCES, not from Locale.getDefault(). Those
     * are the same thing right up until someone uses the per-app language
     * picker, and then the app is Ukrainian while the JVM default is English -
     * which produced «Будильники, дзвінки, and ще 6», an English conjunction in
     * the middle of a Ukrainian sentence.
     */
    private fun join(res: Resources, items: List<String>): String =
        ListFormatter.getInstance(res.configuration.locales[0]).format(items)

    /** In the locale the strings were loaded in, not the JVM default. */
    private fun capitalise(res: Resources, s: String): String =
        s.replaceFirstChar { it.titlecase(res.configuration.locales[0]) }

    /**
     * Cuts a long list down to [keep] items plus an "and N more" tail, then
     * joins it. The tail is a plural, because "1 more" and "5 more" are not the
     * same word everywhere.
     */
    private fun trimmed(res: Resources, all: List<String>, keep: Int): String {
        if (all.size <= keep + 1) return join(res, all)
        val rest = all.size - keep
        return join(res, all.take(keep) + res.getQuantityString(R.plurals.item_and_more, rest, rest))
    }

    /** "Alarms, messages and 2 more" - for the home card, which has one line. */
    fun shortSummary(res: Resources, all: List<String>): String = capitalise(
        res,
        if (all.size == 1) res.getString(R.string.summary_only, all[0])
        else trimmed(res, all, 2)
    )

    /**
     * The lead for the detail screen. Capped: with everything allowed the full
     * list runs to four lines and reads as an inventory, and the rows below it
     * already are one. A summary that long is not a summary.
     */
    fun sentence(res: Resources, all: List<String>): String =
        // Capitalised AFTER formatting. Capitalising the list first and then
        // dropping it into the sentence puts a capital mid-sentence in any
        // language that does not open with the list - Russian's natural order
        // here is "Разрешены будильники...", which is what a candidate wrote.
        capitalise(res, res.getString(R.string.sentence_allowed, trimmed(res, all, 3)))
}
