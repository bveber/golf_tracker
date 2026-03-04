package com.golftracker.util

import org.junit.Assert.*
import org.junit.Test

class GirCalculatorTest {

    // ─── Standard Scenarios ──────────────────────────────────────────

    @Test
    fun `par 3 reach green in 1 is GIR`() {
        // score=3, putts=2 → approach strokes = 1 ≤ par-2 = 1 ✓
        assertTrue(GirCalculator.isGir(score = 3, par = 3, putts = 2))
    }

    @Test
    fun `par 4 reach green in 2 is GIR`() {
        // score=4, putts=2 → approach = 2 ≤ par-2 = 2 ✓
        assertTrue(GirCalculator.isGir(score = 4, par = 4, putts = 2))
    }

    @Test
    fun `par 5 reach green in 3 is GIR`() {
        // score=5, putts=2 → approach = 3 ≤ par-2 = 3 ✓
        assertTrue(GirCalculator.isGir(score = 5, par = 5, putts = 2))
    }

    // ─── Common Non-GIR Scenarios ────────────────────────────────────

    @Test
    fun `par 4 miss green chip and 2 putt for bogey is not GIR`() {
        // score=5, putts=2 → approach = 3, par-2 = 2 → 3 > 2 ✗
        assertFalse(GirCalculator.isGir(score = 5, par = 4, putts = 2))
    }

    @Test
    fun `par 3 miss green chip and 1 putt for par is not GIR`() {
        // score=3, putts=1 → approach = 2, par-2 = 1 → 2 > 1 ✗
        assertFalse(GirCalculator.isGir(score = 3, par = 3, putts = 1))
    }

    // ─── Boundary / Edge Cases ───────────────────────────────────────

    @Test
    fun `exactly on regulation boundary is GIR`() {
        // par 4: approach strokes must be ≤ 2. score=6 putts=4 → approach=2 ✓
        assertTrue(GirCalculator.isGir(score = 6, par = 4, putts = 4))
    }

    @Test
    fun `one stroke over regulation is not GIR`() {
        // par 4: approach must be ≤ 2. score=7 putts=4 → approach=3 ✗
        assertFalse(GirCalculator.isGir(score = 7, par = 4, putts = 4))
    }

    @Test
    fun `score of 0 unplayed hole returns false`() {
        assertFalse(GirCalculator.isGir(score = 0, par = 4, putts = 0))
    }

    @Test
    fun `hole in one on par 3 is GIR`() {
        // score=1, putts=0 → approach=1, par-2=1 → 1≤1 ✓
        assertTrue(GirCalculator.isGir(score = 1, par = 3, putts = 0))
    }

    @Test
    fun `hole in one on par 4 albatross is GIR`() {
        // score=1, putts=0 → approach=1, par-2=2 → 1≤2 ✓
        assertTrue(GirCalculator.isGir(score = 1, par = 4, putts = 0))
    }

    @Test
    fun `reach green in 1 on par 5 then 4 putt is still GIR`() {
        // score=5, putts=4 → approach=1, par-2=3 → 1≤3 ✓
        assertTrue(GirCalculator.isGir(score = 5, par = 5, putts = 4))
    }

    @Test
    fun `birdie with 1 putt on par 4 reached green in 2 is GIR`() {
        // score=3, putts=1 → approach=2, par-2=2 → 2≤2 ✓
        assertTrue(GirCalculator.isGir(score = 3, par = 4, putts = 1))
    }

    @Test
    fun `double bogey with 2 putts on par 4 is not GIR`() {
        // score=6, putts=2 → approach=4, par-2=2 → 4>2 ✗
        assertFalse(GirCalculator.isGir(score = 6, par = 4, putts = 2))
    }
}
