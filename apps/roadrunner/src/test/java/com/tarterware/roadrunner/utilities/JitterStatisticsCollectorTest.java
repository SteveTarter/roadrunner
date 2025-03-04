package com.tarterware.roadrunner.utilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JitterStatisticsCollectorTest
{

    @Test
    void testInvalidCapacity()
    {
        // Capacity must be at least 1
        assertThrows(IllegalArgumentException.class, () -> new JitterStatisticsCollector(0));
        assertThrows(IllegalArgumentException.class, () -> new JitterStatisticsCollector(-5));
    }

    @Test
    void testInitialState()
    {
        // When no measurement has been recorded, statistics should be zero (or default)
        JitterStatisticsCollector collector = new JitterStatisticsCollector(3);
        assertEquals(0, collector.getCount());
        assertEquals(0.0, collector.getMean(), 0.0001);
        assertEquals(0.0, collector.getStandardDeviation(), 0.0001);
        assertEquals(0.0, collector.getMin(), 0.0001);
        assertEquals(0.0, collector.getMax(), 0.0001);
    }

    @Test
    void testSingleMeasurement()
    {
        JitterStatisticsCollector collector = new JitterStatisticsCollector(3);
        collector.recordMeasurement(10.0);
        // With only one measurement, mean and min/max are equal, and std dev is 0.
        assertEquals(1, collector.getCount());
        assertEquals(10.0, collector.getMean(), 0.0001);
        assertEquals(0.0, collector.getStandardDeviation(), 0.0001);
        assertEquals(10.0, collector.getMin(), 0.0001);
        assertEquals(10.0, collector.getMax(), 0.0001);
    }

    @Test
    void testMultipleMeasurementsWithinCapacity()
    {
        JitterStatisticsCollector collector = new JitterStatisticsCollector(3);
        collector.recordMeasurement(10.0);
        collector.recordMeasurement(20.0);
        collector.recordMeasurement(30.0);
        // Expected values:
        // Mean: (10 + 20 + 30) / 3 = 20
        // Standard deviation: sqrt(((10-20)² + (20-20)² + (30-20)²) / (3-1)) =
        // sqrt((100 + 0 + 100)/2) = 10
        // Min: 10, Max: 30
        assertEquals(3, collector.getCount());
        assertEquals(20.0, collector.getMean(), 0.0001);
        assertEquals(10.0, collector.getStandardDeviation(), 0.0001);
        assertEquals(10.0, collector.getMin(), 0.0001);
        assertEquals(30.0, collector.getMax(), 0.0001);
    }

    @Test
    void testCircularBufferReplacement()
    {
        // With capacity 3, add 4 measurements so that the first value is replaced.
        JitterStatisticsCollector collector = new JitterStatisticsCollector(3);
        collector.recordMeasurement(10.0); // buffer: [10]
        collector.recordMeasurement(20.0); // buffer: [10, 20]
        collector.recordMeasurement(30.0); // buffer: [10, 20, 30]
        collector.recordMeasurement(40.0); // buffer becomes: [40, 20, 30] (10 replaced by 40)

        // Expected values are calculated over the current buffer: [40, 20, 30]
        double expectedMean = (40.0 + 20.0 + 30.0) / 3.0; // 30.0
        double expectedStd = Math
                .sqrt(((40.0 - expectedMean) * (40.0 - expectedMean) + (20.0 - expectedMean) * (20.0 - expectedMean)
                        + (30.0 - expectedMean) * (30.0 - expectedMean)) / (3 - 1));
        double expectedMin = 20.0;
        double expectedMax = 40.0;

        assertEquals(3, collector.getCount());
        assertEquals(expectedMean, collector.getMean(), 0.0001);
        assertEquals(expectedStd, collector.getStandardDeviation(), 0.0001);
        assertEquals(expectedMin, collector.getMin(), 0.0001);
        assertEquals(expectedMax, collector.getMax(), 0.0001);
    }

    @Test
    void testCopyConstructorWithSmallerCapacity()
    {
        // Create an original collector with capacity 4.
        JitterStatisticsCollector original = new JitterStatisticsCollector(4);
        original.recordMeasurement(10.0);
        original.recordMeasurement(20.0);
        original.recordMeasurement(30.0);
        original.recordMeasurement(40.0);
        // Create a new collector with a smaller capacity (3). The copy constructor
        // should copy the last 3 measurements.
        JitterStatisticsCollector copy = new JitterStatisticsCollector(original, 3);
        // Expected window: [20, 30, 40]
        double expectedMean = (20.0 + 30.0 + 40.0) / 3.0; // 30.0
        double expectedStd = Math
                .sqrt(((20.0 - expectedMean) * (20.0 - expectedMean) + (30.0 - expectedMean) * (30.0 - expectedMean)
                        + (40.0 - expectedMean) * (40.0 - expectedMean)) / (3 - 1));
        double expectedMin = 20.0;
        double expectedMax = 40.0;

        assertEquals(3, copy.getCount());
        assertEquals(expectedMean, copy.getMean(), 0.0001);
        assertEquals(expectedStd, copy.getStandardDeviation(), 0.0001);
        assertEquals(expectedMin, copy.getMin(), 0.0001);
        assertEquals(expectedMax, copy.getMax(), 0.0001);
    }

    @Test
    void testCopyConstructorWithLargerCapacity()
    {
        // Create an original collector with capacity 3.
        JitterStatisticsCollector original = new JitterStatisticsCollector(3);
        original.recordMeasurement(10.0);
        original.recordMeasurement(20.0);
        original.recordMeasurement(30.0);
        // Create a new collector with a larger capacity (5). The copy should contain
        // the existing measurements.
        JitterStatisticsCollector copy = new JitterStatisticsCollector(original, 5);
        // Expected window: [10, 20, 30]
        double expectedMean = (10.0 + 20.0 + 30.0) / 3.0; // 20.0
        double expectedStd = Math
                .sqrt(((10.0 - expectedMean) * (10.0 - expectedMean) + (20.0 - expectedMean) * (20.0 - expectedMean)
                        + (30.0 - expectedMean) * (30.0 - expectedMean)) / (3 - 1));
        double expectedMin = 10.0;
        double expectedMax = 30.0;

        assertEquals(3, copy.getCount());
        assertEquals(expectedMean, copy.getMean(), 0.0001);
        assertEquals(expectedStd, copy.getStandardDeviation(), 0.0001);
        assertEquals(expectedMin, copy.getMin(), 0.0001);
        assertEquals(expectedMax, copy.getMax(), 0.0001);
    }
}
