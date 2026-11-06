package com.widgetsplus.core.widget

/**
 * The order of a folder widget's category cards in its card stack (Android's StackView, looping).
 *
 * When the widget is updated, StackView refills the cards it has in play, but with looping on it
 * leaves empty any card that sits past the end of the list in its own count (it counts on round
 * the loop as it's swiped). So after an update, or a swipe or two later, the stack could show no
 * card at all until another swipe. When the stack is on card 0 as the update arrives, every card
 * in play is within the list and all of them are refilled.
 *
 * So the stack lists the tabs starting from the one last used (still in the loop's order, only
 * starting there), and every update shows card 0: the stack stays on that category and nothing
 * is left empty. Modelled and tested in CardLoopTest.
 */
object CardLoop {
    /**
     * The tabs as the stack lists them: from the one whose key is [lastKey] (the first tab when
     * that's not a tab any more) round the loop.
     */
    fun <T> order(tabs: List<T>, keyOf: (T) -> String, lastKey: String?): List<T> {
        val start = tabs.indexOfFirst { keyOf(it) == lastKey }.coerceAtLeast(0)
        return tabs.drop(start) + tabs.take(start)
    }
}
