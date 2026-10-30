package com.folderplus.core

import com.folderplus.core.model.AppRef
import com.folderplus.core.model.Folder
import com.folderplus.core.model.HostedWidget
import com.folderplus.core.model.TapAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelsTest {
    @Test fun appRefFlattenRoundTrip() {
        val ref = AppRef("com.example", "com.example.MainActivity")
        assertEquals(ref, AppRef.unflatten(ref.flatten()))
        assertNull(AppRef.unflatten("noslash"))
        assertNull(AppRef.unflatten("/leading"))
        assertNull(AppRef.unflatten("trailing/"))
    }

    @Test fun launchDefaultFallsBackWhenNoDefaultApp() {
        val f = Folder(name = "Work", tapAction = TapAction.LAUNCH_DEFAULT_APP, doubleTapAction = TapAction.LAUNCH_DEFAULT_APP)
        assertEquals(TapAction.OPEN_FOLDER, f.effectiveTapAction)
        assertEquals(TapAction.NONE, f.effectiveDoubleTapAction)
        val withApp = f.copy(defaultApp = AppRef("a", "b"))
        assertEquals(TapAction.LAUNCH_DEFAULT_APP, withApp.effectiveTapAction)
        assertEquals(TapAction.LAUNCH_DEFAULT_APP, withApp.effectiveDoubleTapAction)
    }

    @Test fun showWidgetFallsBackWithoutHostedWidget() {
        val f = Folder(name = "Work", tapAction = TapAction.SHOW_WIDGET, doubleTapAction = TapAction.SHOW_WIDGET)
        assertEquals(TapAction.OPEN_FOLDER, f.effectiveTapAction)
        assertEquals(TapAction.NONE, f.effectiveDoubleTapAction)
        val withWidget = f.copy(hostedWidget = HostedWidget("com.x/com.x.Provider", 42))
        assertEquals(TapAction.SHOW_WIDGET, withWidget.effectiveTapAction)
        assertEquals(TapAction.SHOW_WIDGET, withWidget.effectiveDoubleTapAction)
    }
}
