package io.github.rajumark.hoverfly.gatekeeper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GatekeeperTest {
    private val gk = ParityTest.testGatekeeper()

    @Test
    fun cleanMessagesPass() {
        for (t in listOf("hey are we still meeting at 6?", "thanks a lot, the delivery was super quick",
            "bhai kal milte hai office ke baad", "कल सुबह मिलते हैं", "semma movie da", "merci pour ton aide")) {
            val v = gk.check(t)
            assertFalse("$t -> ${v.score}", v.isToxic)
            assertNull(v.topCategory)
        }
    }

    @Test
    fun toxicMessagesAreFlagged() {
        for (t in listOf("you are a fucking idiot", "chup kar bsdk", "tujhe jaan se maar dunga", "चुप कर साले कमीने",
            "ferme ta gueule connard")) {
            assertTrue(t, gk.isToxic(t))
        }
        assertEquals(Category.THREAT, gk.check("tujhe jaan se maar dunga").topCategory)
    }

    @Test
    fun blankIsNeverToxic() {
        for (t in listOf("", "   ", "\n\t")) {
            val v = gk.check(t, Sensitivity.STRICT)
            assertFalse(v.isToxic)
            assertEquals(0f, v.score)
            assertEquals(Category.entries.toSet(), v.categories.keys)
        }
    }

    @Test
    fun sensitivityOrdersTheThresholds() {
        assertTrue(Sensitivity.STRICT.threshold < Sensitivity.BALANCED.threshold)
        assertTrue(Sensitivity.BALANCED.threshold < Sensitivity.RELAXED.threshold)
        val s = gk.score("you are stupid")
        assertEquals(s >= 0.25f, gk.check("you are stupid", Sensitivity.STRICT).isToxic)
        assertEquals(s >= 0.9f, gk.check("you are stupid", 0.9f).isToxic)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBadThreshold() {
        gk.check("hi", 1.5f)
    }

    @Test(expected = IllegalStateException::class)
    fun closedInstanceThrows() {
        val g = ParityTest.testGatekeeper()
        g.close()
        g.check("hello")
    }

    @Test
    fun latency() {
        val texts = listOf("hey are we still meeting at 6?", "chup kar bsdk", "you are a fucking idiot",
            "bhai tu toh kamaal hai 🔥", "कल सुबह मिलते हैं", "this product is bad, the battery died in 2 days")
        repeat(500) { gk.check(texts[it % texts.size]) }
        val n = 5000
        val t0 = System.nanoTime()
        repeat(n) { gk.check(texts[it % texts.size]) }
        val ms = (System.nanoTime() - t0) / 1e6 / n
        println("JVM latency: %.3f ms per message".format(ms))
        assertTrue("too slow: $ms ms", ms < 5.0)
    }
}
