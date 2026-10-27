package com.voicetyping.directtest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The autostart table is the only part of [BackgroundAccess] that can be checked off-device: the
 * intents themselves need a real Context. These tests guard the brand mapping and the shape of
 * the component names, which is where a typo would silently cost a user their floating mic.
 */
class VendorAutostartTargetsTest {

    private val brandsWithLists = listOf(
        "Xiaomi", "Redmi", "POCO", "OPPO", "realme", "vivo", "iQOO",
        "HUAWEI", "HONOR", "OnePlus", "samsung", "Infinix", "TECNO",
        "asus", "Meizu", "LeTV", "HMD Global",
    )

    @Test fun everyKnownBrandOffersSomewhereToGo() {
        for (brand in brandsWithLists) {
            val targets = BackgroundAccess.autostartTargets(brand)
            val actions = BackgroundAccess.autostartActions(brand)
            assertTrue("no autostart target for $brand", targets.isNotEmpty() || actions.isNotEmpty())
        }
    }

    @Test fun phonesWithoutAVendorListGetNothingToOpen() {
        for (brand in listOf("Google", "motorola", "Nothing", "")) {
            assertEquals(emptyList<Pair<String, String>>(), BackgroundAccess.autostartTargets(brand))
            assertEquals(emptyList<String>(), BackgroundAccess.autostartActions(brand))
        }
    }

    @Test fun subBrandsResolveToTheSameSkinAsTheirParent() {
        for ((sub, parent) in listOf("Redmi" to "Xiaomi", "POCO" to "Xiaomi", "realme" to "OPPO",
            "iQOO" to "vivo", "HONOR" to "HUAWEI", "TECNO" to "Infinix")) {
            assertEquals(
                "$sub should use the $parent table",
                BackgroundAccess.autostartTargets(parent),
                BackgroundAccess.autostartTargets(sub),
            )
        }
    }

    @Test fun miuiIsTriedByActionFirstSoPackageVisibilityCannotBlockIt() {
        assertEquals(listOf("miui.intent.action.OP_AUTO_START"), BackgroundAccess.autostartActions("Xiaomi"))
    }

    @Test fun componentNamesAreFullyQualifiedAndNotRepeated() {
        for (brand in brandsWithLists) {
            val targets = BackgroundAccess.autostartTargets(brand)
            assertEquals("duplicate target for $brand", targets.distinct(), targets)
            for ((packageName, className) in targets) {
                assertTrue("$packageName is not a package name", packageName.contains('.'))
                assertTrue("$className is not fully qualified", className.startsWith("com."))
            }
        }
    }

    @Test fun caseAndSpacingInTheManufacturerStringDoNotMatter() {
        val expected = BackgroundAccess.autostartTargets("xiaomi")
        for (spelling in listOf("Xiaomi", "XIAOMI", " xiaomi ")) {
            assertEquals(expected, BackgroundAccess.autostartTargets(spelling))
        }
    }
}
