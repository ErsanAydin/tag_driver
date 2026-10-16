package com.tagtakip.pro.presentation.shift

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelCostCalculatorTest {

    @Test
    fun `tuketim girilmemisse MissingConsumption doner`() {
        assertEquals(FuelCostCalculator.Outcome.MissingConsumption, FuelCostCalculator.calculate(10_000f, null, 40.0))
        assertEquals(FuelCostCalculator.Outcome.MissingConsumption, FuelCostCalculator.calculate(10_000f, 0.0, 40.0))
    }

    @Test
    fun `mesafe yoksa NoDistance doner`() {
        assertEquals(FuelCostCalculator.Outcome.NoDistance, FuelCostCalculator.calculate(0f, 7.0, 40.0))
    }

    @Test
    fun `normal hesaplama dogrudur`() {
        val result = FuelCostCalculator.calculate(100_000f, 7.0, 45.5) as FuelCostCalculator.Outcome.Ready
        assertEquals(100.0, result.distanceKm, 0.001)
        assertEquals(7.0, result.litre, 0.001)
        assertEquals(318.5, result.cost, 0.001)
        assertFalse(result.needsFuelPrice)
    }

    @Test
    fun `fiyat yoksa needsFuelPrice true olur`() {
        val result = FuelCostCalculator.calculate(50_000f, 6.0, null) as FuelCostCalculator.Outcome.Ready
        assertTrue(result.needsFuelPrice)
        assertEquals(3.0, result.litre, 0.001)
    }
}
