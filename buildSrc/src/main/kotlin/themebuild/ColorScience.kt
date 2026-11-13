package themebuild

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The colour measurements the palette audit relies on. Each one follows a published definition so the
 * numbers can be checked against reference implementations:
 *
 * - WCAG 2.x contrast ratio (W3C, "relative luminance" and "contrast ratio" definitions).
 * - APCA-W3 0.0.98G-4g lightness contrast (Lc), the candidate method for WCAG 3.
 * - CIEDE2000 colour difference (CIE 142-2001; implementation notes from Sharma, Wu & Dalal 2005).
 * - Colour-vision-deficiency simulation, Machado, Oliveira & Fernandes 2009, severity 1.0
 *   (full dichromacy), applied in linear RGB.
 *
 * Colours are sRGB triples in 0..1.
 */
object ColorScience {

    /** Parses `#RRGGBB`; alpha colours are rejected because contrast against a blend is undefined here. */
    fun parse(hex: String): DoubleArray {
        require(Regex("#[0-9A-Fa-f]{6}").matches(hex)) { "expected an opaque #RRGGBB colour, got '$hex'" }
        return DoubleArray(3) { i -> hex.substring(1 + 2 * i, 3 + 2 * i).toInt(16) / 255.0 }
    }

    private fun toLinear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun toGamma(c: Double) = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055

    fun relativeLuminance(rgb: DoubleArray): Double {
        val (r, g, b) = rgb.map(::toLinear)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** WCAG 2.x contrast ratio, 1..21. */
    fun wcagContrast(a: DoubleArray, b: DoubleArray): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** APCA lightness contrast Lc of [text] on [background]; negative for light text on dark backgrounds. */
    fun apcaLc(text: DoubleArray, background: DoubleArray): Double {
        fun screenY(rgb: DoubleArray): Double {
            val y = 0.2126729 * rgb[0].pow(2.4) + 0.7151522 * rgb[1].pow(2.4) + 0.0721750 * rgb[2].pow(2.4)
            return if (y > 0.022) y else y + (0.022 - y).pow(1.414)
        }
        val yText = screenY(text)
        val yBackground = screenY(background)
        if (abs(yBackground - yText) < 0.0005) return 0.0
        return if (yBackground > yText) {
            val s = (yBackground.pow(0.56) - yText.pow(0.57)) * 1.14
            if (s < 0.1) 0.0 else (s - 0.027) * 100
        } else {
            val s = (yBackground.pow(0.65) - yText.pow(0.62)) * 1.14
            if (s > -0.1) 0.0 else (s + 0.027) * 100
        }
    }

    /** CIE L*a*b* under D65. */
    fun lab(rgb: DoubleArray): DoubleArray {
        val (r, g, b) = rgb.map(::toLinear)
        val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
        val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
        val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883
        val delta = 6.0 / 29
        fun f(t: Double) = if (t > delta * delta * delta) cbrt(t) else t / (3 * delta * delta) + 4.0 / 29
        return doubleArrayOf(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
    }

    /** CIEDE2000 difference between two colours (about 1 = just noticeable side by side). */
    fun deltaE2000(first: DoubleArray, second: DoubleArray): Double {
        val (l1, a1, b1) = lab(first).toList()
        val (l2, a2, b2) = lab(second).toList()
        val cBar = (hypot(a1, b1) + hypot(a2, b2)) / 2
        val g = 0.5 * (1 - sqrt(cBar.pow(7) / (cBar.pow(7) + 25.0.pow(7))))
        val a1p = (1 + g) * a1
        val a2p = (1 + g) * a2
        val c1p = hypot(a1p, b1)
        val c2p = hypot(a2p, b2)
        fun hue(b: Double, ap: Double) = if (b == 0.0 && ap == 0.0) 0.0 else (deg(atan2(b, ap)) + 360) % 360
        val h1p = hue(b1, a1p)
        val h2p = hue(b2, a2p)

        val dLp = l2 - l1
        val dCp = c2p - c1p
        val dhp = when {
            c1p * c2p == 0.0 -> 0.0
            abs(h2p - h1p) <= 180 -> h2p - h1p
            h2p - h1p > 180 -> h2p - h1p - 360
            else -> h2p - h1p + 360
        }
        val dHp = 2 * sqrt(c1p * c2p) * sin(rad(dhp / 2))

        val lBarP = (l1 + l2) / 2
        val cBarP = (c1p + c2p) / 2
        val hBarP = when {
            c1p * c2p == 0.0 -> h1p + h2p
            abs(h1p - h2p) <= 180 -> (h1p + h2p) / 2
            h1p + h2p < 360 -> (h1p + h2p + 360) / 2
            else -> (h1p + h2p - 360) / 2
        }
        val t = 1 - 0.17 * cos(rad(hBarP - 30)) + 0.24 * cos(rad(2 * hBarP)) +
            0.32 * cos(rad(3 * hBarP + 6)) - 0.20 * cos(rad(4 * hBarP - 63))
        val dTheta = 30 * exp(-((hBarP - 275) / 25).pow(2))
        val rC = 2 * sqrt(cBarP.pow(7) / (cBarP.pow(7) + 25.0.pow(7)))
        val sL = 1 + 0.015 * (lBarP - 50).pow(2) / sqrt(20 + (lBarP - 50).pow(2))
        val sC = 1 + 0.045 * cBarP
        val sH = 1 + 0.015 * cBarP * t
        val rT = -sin(rad(2 * dTheta)) * rC
        return sqrt(
            (dLp / sL).pow(2) + (dCp / sC).pow(2) + (dHp / sH).pow(2) + rT * (dCp / sC) * (dHp / sH),
        )
    }

    /** Dichromacy types, with the Machado et al. 2009 severity-1.0 matrices (linear RGB). */
    enum class Cvd(val label: String, private val m: Array<DoubleArray>) {
        PROTAN(
            "protanopia",
            arrayOf(
                doubleArrayOf(0.152286, 1.052583, -0.204868),
                doubleArrayOf(0.114503, 0.786281, 0.099216),
                doubleArrayOf(-0.003882, -0.048116, 1.051998),
            ),
        ),
        DEUTAN(
            "deuteranopia",
            arrayOf(
                doubleArrayOf(0.367322, 0.860646, -0.227968),
                doubleArrayOf(0.280085, 0.672501, 0.047413),
                doubleArrayOf(-0.011820, 0.042940, 0.968881),
            ),
        ),
        TRITAN(
            "tritanopia",
            arrayOf(
                doubleArrayOf(1.255528, -0.076749, -0.178779),
                doubleArrayOf(-0.078411, 0.930809, 0.147602),
                doubleArrayOf(0.004733, 0.691367, 0.303900),
            ),
        ),
        ;

        fun simulate(rgb: DoubleArray): DoubleArray {
            val linear = rgb.map(::toLinear)
            return DoubleArray(3) { row ->
                val v = (0..2).sumOf { col -> m[row][col] * linear[col] }
                toGamma(v.coerceIn(0.0, 1.0))
            }
        }
    }

    private fun rad(degrees: Double) = degrees * PI / 180

    private fun deg(radians: Double) = radians * 180 / PI
}
