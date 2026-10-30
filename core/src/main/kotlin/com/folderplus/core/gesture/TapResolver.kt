package com.folderplus.core.gesture

/**
 * Distinguishes a single tap from a double tap on a home-screen widget.
 *
 * App widgets only deliver click events, so a double tap has to be detected by
 * timing two consecutive clicks. The caller feeds [onTap] on every click and
 * acts on the returned [Decision]:
 *
 *  - [Decision.WaitForSecondTap]: schedule a check after [windowMillis]; if no
 *    second tap arrives the pending tap becomes a single tap ([resolvePending]).
 *  - [Decision.DoubleTap]: fire the double-tap action immediately and cancel
 *    the scheduled single-tap.
 *  - [Decision.SingleTap]: no double-tap action is configured for this target,
 *    so a tap resolves instantly with zero latency.
 */
class TapResolver(private val windowMillis: Long = DEFAULT_WINDOW_MILLIS) {

    sealed interface Decision {
        data object SingleTap : Decision
        data object DoubleTap : Decision
        data object WaitForSecondTap : Decision
    }

    private val pending = HashMap<Int, Long>()

    /**
     * @param targetId identifies the widget instance (appWidgetId).
     * @param nowMillis current time (injected for tests).
     * @param doubleTapEnabled whether the target has a double-tap action at all.
     */
    fun onTap(targetId: Int, nowMillis: Long, doubleTapEnabled: Boolean): Decision {
        if (!doubleTapEnabled) {
            pending.remove(targetId)
            return Decision.SingleTap
        }
        val first = pending[targetId]
        return if (first != null && nowMillis - first in 0..windowMillis) {
            pending.remove(targetId)
            Decision.DoubleTap
        } else {
            pending[targetId] = nowMillis
            Decision.WaitForSecondTap
        }
    }

    /**
     * Called when the wait timer fires. Returns true if a tap is still pending
     * for the target (so the single-tap action should run now) and clears it.
     */
    fun resolvePending(targetId: Int, nowMillis: Long): Boolean {
        val first = pending[targetId] ?: return false
        if (nowMillis - first < windowMillis) return false
        pending.remove(targetId)
        return true
    }

    fun hasPending(targetId: Int): Boolean = pending.containsKey(targetId)

    fun clear() = pending.clear()

    companion object {
        /** Slightly above the typical 300 ms double-tap timeout feels reliable on widgets. */
        const val DEFAULT_WINDOW_MILLIS = 350L
    }
}
