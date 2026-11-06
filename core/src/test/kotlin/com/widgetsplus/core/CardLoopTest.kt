package com.widgetsplus.core

import com.widgetsplus.core.widget.CardLoop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class CardLoopTest {

    /**
     * A model of how Android's StackView (AdapterViewAnimator underneath) keeps its cards: which
     * card each frame holds, nothing about drawing. It follows the platform's showOnly,
     * setDisplayedChild, getWindowSize and refreshChildren (the refill after an update), with
     * StackView's 5 cards in play and the front card one in from the start of the window.
     */
    private class StackModel(private val loop: Boolean = true) {
        private val activeViews = 5
        private val activeOffset = 1
        private var items: List<String> = emptyList()
        private class Frame(var card: String?)
        private val frames = HashMap<Int, Frame>()
        private val previous = mutableListOf<Int>()
        private var whichChild = 0
        private var windowStart = 0
        private var windowEnd = -1
        private var windowStartUnbounded = 0
        private var dataChanged = false
        private var oldCount = 0

        private val count get() = items.size
        private fun numActive() = minOf(count + 1, activeViews)
        private fun windowSize() = if (count <= numActive() && loop) count * activeViews else count
        private fun modulo(pos: Int, size: Int) = if (size > 0) (size + pos % size) % size else 0

        /** The card on the front of the stack; null when its frame is empty. */
        val front: String? get() = frames[modulo(whichChild, windowSize())]?.card

        fun setAdapter(list: List<String>) {
            items = list
            whichChild = 0
            showOnly(0)
        }

        fun next() = setDisplayedChild(whichChild + 1)
        fun previousCard() = setDisplayedChild(whichChild - 1)

        fun setDisplayedChild(which: Int) {
            val size = windowSize()
            whichChild = when {
                which >= size -> if (loop) 0 else size - 1
                which < 0 -> if (loop) size - 1 else 0
                else -> which
            }
            showOnly(whichChild)
        }

        /** The adapter gets new data (RemoteCollectionItemsAdapter.setData + notifyDataSetChanged). */
        fun setData(list: List<String>) {
            oldCount = count
            items = list
            dataChanged = true
        }

        /** The next layout pass after a data change (checkForAndHandleDataChanged). */
        fun layout() {
            if (!dataChanged) return
            dataChanged = false
            if (whichChild >= windowSize()) {
                whichChild = 0
                showOnly(0)
            } else if (oldCount != count) {
                showOnly(whichChild)
            }
            refreshChildren()
        }

        private fun refreshChildren() {
            for (i in windowStart..windowEnd) {
                val index = modulo(i, windowSize())
                // The platform compares the window position, not the card's place in the list.
                val updated = if (i < count) items[modulo(i, count)] else null
                frames[index]?.card = updated
            }
        }

        private fun showOnly(childIndex: Int) {
            if (count == 0) return
            previous.forEach { frames.remove(it) }
            previous.clear()
            val startUnbounded = childIndex - activeOffset
            val endUnbounded = startUnbounded + numActive() - 1
            var start = maxOf(0, startUnbounded)
            var end = minOf(count - 1, endUnbounded)
            if (loop) {
                start = startUnbounded
                end = endUnbounded
            }
            val size = windowSize()
            val rangeStart = modulo(start, size)
            val rangeEnd = modulo(end, size)
            val wrap = rangeStart > rangeEnd
            for (index in frames.keys) {
                val remove = if (!wrap) index < rangeStart || index > rangeEnd else index in (rangeEnd + 1) until rangeStart
                if (remove) previous += index
            }
            if (!(start == windowStart && end == windowEnd && startUnbounded == windowStartUnbounded)) {
                for (i in start..end) {
                    val index = modulo(i, size)
                    val inOldRange = frames.containsKey(index) && index !in previous
                    if (!inOldRange) frames[index] = Frame(items[modulo(i, count)])
                }
                windowStart = start
                windowEnd = end
                windowStartUnbounded = startUnbounded
            }
        }
    }

    /** What used to happen: one card per tab, kept where it was on an update. */
    @Test fun aFewTabsOnTheirOwnLoseTheFrontCardAfterAnUpdate() {
        val tabs = listOf("Google", "Utility")
        val stack = StackModel()
        stack.setAdapter(tabs)
        stack.next()
        stack.next()
        assertEquals("Google", stack.front)
        stack.setData(tabs)
        stack.layout()
        assertNull(stack.front)
        // A swipe doesn't bring it back; the one after does.
        stack.next()
        assertNull(stack.front)
        stack.next()
        assertEquals("Google", stack.front)
    }

    @Test fun orderStartsFromTheLastUsedTabInTheLoopsOrder() {
        val tabs = listOf("a", "b", "c")
        assertEquals(listOf("b", "c", "a"), CardLoop.order(tabs, { it }, "b"))
        assertEquals(listOf("c", "a", "b"), CardLoop.order(tabs, { it }, "c"))
        assertEquals(tabs, CardLoop.order(tabs, { it }, "gone"))
        assertEquals(tabs, CardLoop.order(tabs, { it }, null))
        assertEquals(emptyList<String>(), CardLoop.order(emptyList<String>(), { it }, "a"))
    }

    /**
     * Swipes either way, app taps (an update that starts from the tapped tab) and other updates
     * (starting from the last tapped tab), in any order: the front card is always there and is
     * always the tab the loop says it should be.
     */
    @Test fun theFrontCardIsNeverEmptyAndFollowsTheLoop() {
        for (tabCount in 2..9) {
            val tabs = (0 until tabCount).map { "tab$it" }
            repeat(40) { seed ->
                val random = Random(seed * 31 + tabCount)
                val stack = StackModel()
                var lastUsed: String? = null
                var expected = tabs[0]
                stack.setAdapter(CardLoop.order(tabs, { it }, lastUsed))
                fun step(by: Int) = tabs[(tabs.indexOf(expected) + by + tabCount) % tabCount]
                repeat(300) {
                    when (random.nextInt(5)) {
                        0, 1 -> { stack.next(); expected = step(1) }
                        2 -> { stack.previousCard(); expected = step(-1) }
                        else -> {
                            // An update: a tap remembers the card on show; then the widget's
                            // update gives the new list and shows card 0, and the stack refills.
                            if (random.nextBoolean()) lastUsed = expected
                            stack.setData(CardLoop.order(tabs, { it }, lastUsed))
                            stack.setDisplayedChild(0)
                            stack.layout()
                            expected = lastUsed ?: tabs[0]
                        }
                    }
                    assertEquals("tabs=$tabCount seed=$seed", expected, stack.front)
                }
            }
        }
    }
}
