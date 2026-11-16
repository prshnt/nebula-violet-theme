package themebuild

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import themebuild.ColorScience.Cvd

/** Checks each measurement against the reference values published with its definition. */
class ColorScienceTest {

    /**
     * All 34 pairs from Sharma, Wu & Dalal (2005), "The CIEDE2000 color-difference formula:
     * implementation notes, supplementary test data, and mathematical observations", Table 1.
     * They cover the hue-wraparound and zero-chroma edge cases.
     */
    @ParameterizedTest(name = "pair {index}: ΔE00 = {6}")
    @CsvSource(
        "50.0000,  2.6772, -79.7751, 50.0000,   0.0000, -82.7485,  2.0425",
        "50.0000,  3.1571, -77.2803, 50.0000,   0.0000, -82.7485,  2.8615",
        "50.0000,  2.8361, -74.0200, 50.0000,   0.0000, -82.7485,  3.4412",
        "50.0000, -1.3802, -84.2814, 50.0000,   0.0000, -82.7485,  1.0000",
        "50.0000, -1.1848, -84.8006, 50.0000,   0.0000, -82.7485,  1.0000",
        "50.0000, -0.9009, -85.5211, 50.0000,   0.0000, -82.7485,  1.0000",
        "50.0000,  0.0000,   0.0000, 50.0000,  -1.0000,   2.0000,  2.3669",
        "50.0000, -1.0000,   2.0000, 50.0000,   0.0000,   0.0000,  2.3669",
        "50.0000,  2.4900,  -0.0010, 50.0000,  -2.4900,   0.0009,  7.1792",
        "50.0000,  2.4900,  -0.0010, 50.0000,  -2.4900,   0.0010,  7.1792",
        "50.0000,  2.4900,  -0.0010, 50.0000,  -2.4900,   0.0011,  7.2195",
        "50.0000,  2.4900,  -0.0010, 50.0000,  -2.4900,   0.0012,  7.2195",
        "50.0000, -0.0010,   2.4900, 50.0000,   0.0009,  -2.4900,  4.8045",
        "50.0000, -0.0010,   2.4900, 50.0000,   0.0010,  -2.4900,  4.8045",
        "50.0000, -0.0010,   2.4900, 50.0000,   0.0011,  -2.4900,  4.7461",
        "50.0000,  2.5000,   0.0000, 50.0000,   0.0000,  -2.5000,  4.3065",
        "50.0000,  2.5000,   0.0000, 73.0000,  25.0000, -18.0000, 27.1492",
        "50.0000,  2.5000,   0.0000, 61.0000,  -5.0000,  29.0000, 22.8977",
        "50.0000,  2.5000,   0.0000, 56.0000, -27.0000,  -3.0000, 31.9030",
        "50.0000,  2.5000,   0.0000, 58.0000,  24.0000,  15.0000, 19.4535",
        "50.0000,  2.5000,   0.0000, 50.0000,   3.1736,   0.5854,  1.0000",
        "50.0000,  2.5000,   0.0000, 50.0000,   3.2972,   0.0000,  1.0000",
        "50.0000,  2.5000,   0.0000, 50.0000,   1.8634,   0.5757,  1.0000",
        "50.0000,  2.5000,   0.0000, 50.0000,   3.2592,   0.3350,  1.0000",
        "60.2574, -34.0099, 36.2677, 60.4626, -34.1751,  39.4387,  1.2644",
        "63.0109, -31.0961, -5.8663, 62.8187, -29.7946,  -4.0864,  1.2630",
        "61.2901,  3.7196,  -5.3901, 61.4292,   2.2480,  -4.9620,  1.8731",
        "35.0831, -44.1164,  3.7933, 35.0232, -40.0716,   1.5901,  1.8645",
        "22.7233, 20.0904, -46.6940, 23.0331,  14.9730, -42.5619,  2.0373",
        "36.4612, 47.8580,  18.3852, 36.2715,  50.5065,  21.2231,  1.4146",
        "90.8027, -2.0831,   1.4410, 91.1528,  -1.6435,   0.0447,  1.4441",
        "90.9257, -0.5406,  -0.9208, 88.6381,  -0.8985,  -0.7239,  1.5381",
        " 6.7747, -0.2908,  -2.4247,  5.8714,  -0.0985,  -2.2286,  0.6377",
        " 2.0776,  0.0795,  -1.1350,  0.9033,  -0.0636,  -0.5514,  0.9082",
    )
    fun `CIEDE2000 matches Sharma et al test data`(
        l1: Double, a1: Double, b1: Double, l2: Double, a2: Double, b2: Double, expected: Double,
    ) {
        val first = doubleArrayOf(l1, a1, b1)
        val second = doubleArrayOf(l2, a2, b2)
        assertEquals(expected, ColorScience.deltaE2000Lab(first, second), 1e-4)
        assertEquals(expected, ColorScience.deltaE2000Lab(second, first), 1e-4, "must be symmetric")
    }

    @Test
    fun `sRGB to Lab matches the standard D65 values`() {
        assertLab(100.0, 0.0, 0.0, "#FFFFFF")
        assertLab(0.0, 0.0, 0.0, "#000000")
        assertLab(53.2408, 80.0925, 67.2032, "#FF0000")
        assertLab(87.7347, -86.1827, 83.1793, "#00FF00")
        assertLab(32.2970, 79.1875, -107.8602, "#0000FF")
    }

    @Test
    fun `WCAG contrast matches the W3C definition`() {
        assertEquals(21.0, ColorScience.wcagContrast(rgb("#000000"), rgb("#FFFFFF")), 1e-9)
        assertEquals(1.0, ColorScience.wcagContrast(rgb("#7F3FBF"), rgb("#7F3FBF")), 1e-9)
        // The classic "just under AA" grey on white.
        assertEquals(4.48, ColorScience.wcagContrast(rgb("#777777"), rgb("#FFFFFF")), 0.005)
        assertEquals(4.54, ColorScience.wcagContrast(rgb("#767676"), rgb("#FFFFFF")), 0.005)
        // Order doesn't matter.
        assertEquals(
            ColorScience.wcagContrast(rgb("#123456"), rgb("#ABCDEF")),
            ColorScience.wcagContrast(rgb("#ABCDEF"), rgb("#123456")),
        )
    }

    /** Reference values from the apca-w3 0.0.98G-4g test suite. */
    @ParameterizedTest(name = "{0} on {1}: Lc {2}")
    @CsvSource(
        "#888888, #FFFFFF,  63.056469930209424",
        "#FFFFFF, #888888, -68.54146436644962",
        "#000000, #AAAAAA,  58.146262578561334",
        "#AAAAAA, #000000, -56.24113336839742",
        "#112233, #DDEEFF,  91.66830811481631",
        "#DDEEFF, #112233, -93.06770049484275",
        "#000000, #FFFFFF, 106.04067321268862",
        "#FFFFFF, #000000, -107.88473318309848",
    )
    fun `APCA Lc matches the reference implementation`(text: String, background: String, expected: Double) {
        assertEquals(expected, ColorScience.apcaLc(rgb(text), rgb(background)), 1e-6)
    }

    @Test
    fun `APCA clips contrast too low to read to zero`() {
        assertEquals(0.0, ColorScience.apcaLc(rgb("#808080"), rgb("#808080")))
        assertEquals(0.0, ColorScience.apcaLc(rgb("#7F7F7F"), rgb("#808080")))
    }

    @Test
    fun `dichromacy simulation keeps greys and the extremes`() {
        for (cvd in Cvd.entries) {
            for (grey in listOf("#000000", "#404040", "#808080", "#C0C0C0", "#FFFFFF")) {
                val simulated = cvd.simulate(rgb(grey))
                assertTrue(
                    ColorScience.deltaE2000(simulated, rgb(grey)) < 0.01,
                    "${cvd.label} should leave $grey unchanged, got ${simulated.toList()}",
                )
            }
        }
    }

    @Test
    fun `dichromacy simulation merges the confusion colours of each type`() {
        fun seen(cvd: Cvd, a: String, b: String) = ColorScience.deltaE2000(cvd.simulate(rgb(a)), cvd.simulate(rgb(b)))

        // Red and green look alike to protanopes and deuteranopes, but stay apart for tritanopes.
        val (red, green) = "#D03030" to "#709020"
        for (cvd in listOf(Cvd.PROTAN, Cvd.DEUTAN)) {
            assertTrue(
                seen(cvd, red, green) < seen(Cvd.TRITAN, red, green) / 2,
                "red/green: ${cvd.label} ΔE ${seen(cvd, red, green)}, tritanopia ΔE ${seen(Cvd.TRITAN, red, green)}",
            )
        }
        // Blue and teal look alike to tritanopes, but stay apart for the red-green types.
        val (blue, teal) = "#3A7BD5" to "#39A08A"
        for (cvd in listOf(Cvd.PROTAN, Cvd.DEUTAN)) {
            assertTrue(
                seen(Cvd.TRITAN, blue, teal) < seen(cvd, blue, teal) / 2,
                "blue/teal: tritanopia ΔE ${seen(Cvd.TRITAN, blue, teal)}, ${cvd.label} ΔE ${seen(cvd, blue, teal)}",
            )
        }
    }

    @Test
    fun `parse rejects colours with alpha`() {
        val failure = runCatching { ColorScience.parse("#FFFFFF80") }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException, "got $failure")
    }

    private fun rgb(hex: String) = ColorScience.parse(hex)

    private fun assertLab(l: Double, a: Double, b: Double, hex: String) {
        val lab = ColorScience.lab(rgb(hex))
        assertEquals(l, lab[0], 0.01, "$hex L*")
        assertEquals(a, lab[1], 0.01, "$hex a*")
        assertEquals(b, lab[2], 0.01, "$hex b*")
    }
}
