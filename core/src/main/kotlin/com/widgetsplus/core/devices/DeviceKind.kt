package com.widgetsplus.core.devices

import java.util.Locale

/** What a device is, for its drawing or icon. Order is the order devices appear in the widget. */
enum class DeviceKind { EARBUDS, HEADPHONES, WATCH, KEYBOARD, MOUSE, GAMEPAD, SPEAKER, CAR, OTHER }

/**
 * Recognises common devices by their Bluetooth name. Speakers with a microphone often announce
 * themselves as a headset, so well-known speaker ranges are named here; Sony's WH/WF prefixes
 * tell its headphones and earbuds apart. Returns null when the name says nothing; the device's
 * Bluetooth class decides then.
 */
object DeviceNames {
    private val earbuds = Regex("""\b(buds?|pods?|earbuds?|earphones?|linkbuds)\b|\bwf-""")
    private val headphones = Regex("""\bwh-|\bult wear\b""")
    private val watch = Regex("""watch""") // "Galaxy Watch6", "Smartwatch"
    private val gamepad = Regex("""\b(controller|gamepad|joy-?con|dualsense|dualshock)\b|xbox|8bitdo""")
    private val keyboard = Regex("""keyboard|\bkeys\b""")
    private val mouse = Regex("""mouse|mx master|trackpad""")
    private val speaker = Regex(
        listOf(
            """speaker|soundbar|sound bar""",
            // Ultimate Ears: BOOM, MEGABOOM, WONDERBOOM, HYPERBOOM, EPICBOOM; JBL Boombox; Soundcore Boom
            """boom""",
            """\bue roll\b""",
            """\bjbl (flip|charge|clip|go|xtreme|pulse|partybox|boombox|bar|encore|authentics)""",
            """soundlink""",                                   // Bose
            """\bsonos (roam|move|era|five)""",
            """\b(emberton|stanmore|acton|woburn|middleton|kilburn|tufton|willen)\b""", // Marshall
            """\bbeats pill\b""",
            """\bsoundcore (motion|flare|select|\d)""",       // Anker
            """\b(stormbox|xsound)\b""",                       // Tribit
            """beosound|\bbeoplay [apm]\d""",                  // Bang & Olufsen
            """harman kardon|\bonyx studio\b""",
            // Sony: SRS-XB13, SRS-XE300, ULT FIELD 1, ULT TOWER 10, HT-S2000 soundbars
            """\bsrs-|\bult (field|tower)\b|\bht-[a-z]""",
        ).joinToString("|"),
    )

    fun kindOf(name: String): DeviceKind? {
        val n = name.lowercase(Locale.ROOT)
        return when {
            earbuds.containsMatchIn(n) -> DeviceKind.EARBUDS
            headphones.containsMatchIn(n) -> DeviceKind.HEADPHONES
            watch.containsMatchIn(n) -> DeviceKind.WATCH
            gamepad.containsMatchIn(n) -> DeviceKind.GAMEPAD
            speaker.containsMatchIn(n) -> DeviceKind.SPEAKER
            keyboard.containsMatchIn(n) -> DeviceKind.KEYBOARD
            mouse.containsMatchIn(n) -> DeviceKind.MOUSE
            else -> null
        }
    }
}
