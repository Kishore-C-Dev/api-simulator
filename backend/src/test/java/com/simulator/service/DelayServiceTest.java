package com.simulator.service;

import com.simulator.model.RequestMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DelayServiceTest {

    private DelayService delayService;

    @BeforeEach
    void setUp() {
        delayService = new DelayService();
    }

    @Test
    void shouldTriggerError_WhenErrorRateIsZero_ShouldReturnFalse() {
        RequestMapping.DelaySpec delaySpec = new RequestMapping.DelaySpec();
        delaySpec.setErrorRatePercent(0);
        
        assertFalse(delayService.shouldTriggerError(delaySpec));
    }

    @Test
    void shouldTriggerError_WhenErrorRateIsNull_ShouldReturnFalse() {
        RequestMapping.DelaySpec delaySpec = new RequestMapping.DelaySpec();
        delaySpec.setErrorRatePercent(null);
        
        assertFalse(delayService.shouldTriggerError(delaySpec));
    }

    @Test
    void shouldTriggerError_WhenErrorRateIsHundred_ShouldAlwaysReturnTrue() {
        RequestMapping.DelaySpec delaySpec = new RequestMapping.DelaySpec();
        delaySpec.setErrorRatePercent(100);
        
        for (int i = 0; i < 10; i++) {
            assertTrue(delayService.shouldTriggerError(delaySpec));
        }
    }

    @Test
    void shouldTriggerError_WhenDelaySpecIsNull_ShouldReturnFalse() {
        assertFalse(delayService.shouldTriggerError(null));
    }

    @Test
    void applyDelay_WithNullDelaySpec_ShouldNotThrow() {
        assertDoesNotThrow(() -> delayService.applyDelay(null));
    }

    @Test
    void applyDelay_WithFixedDelay_ShouldApplyCorrectDelay() {
        RequestMapping.DelaySpec delaySpec = new RequestMapping.DelaySpec();
        delaySpec.setMode("fixed");
        delaySpec.setFixedMs(100);
        delaySpec.setErrorRatePercent(0);

        long start = System.currentTimeMillis();
        delayService.applyDelay(delaySpec);
        long end = System.currentTimeMillis();
        
        long actualDelay = end - start;
        assertTrue(actualDelay >= 90, "Delay should be at least 90ms, was: " + actualDelay);
        assertTrue(actualDelay <= 150, "Delay should be at most 150ms, was: " + actualDelay);
    }

    @Test
    void applyDelay_WithVariableDelay_ShouldApplyDelayInRange() {
        RequestMapping.DelaySpec delaySpec = new RequestMapping.DelaySpec();
        delaySpec.setMode("variable");
        delaySpec.setVariableMinMs(100);
        delaySpec.setVariableMaxMs(200);
        delaySpec.setErrorRatePercent(0);

        long start = System.currentTimeMillis();
        delayService.applyDelay(delaySpec);
        long end = System.currentTimeMillis();
        
        long actualDelay = end - start;
        assertTrue(actualDelay >= 90, "Delay should be at least 90ms, was: " + actualDelay);
        assertTrue(actualDelay <= 250, "Delay should be at most 250ms, was: " + actualDelay);
    }

    @Test
    void applyDelay_WithErrorRate_ShouldNotApplyDelayWhenErrorTriggered() {
        RequestMapping.DelaySpec delaySpec = new RequestMapping.DelaySpec();
        delaySpec.setMode("fixed");
        delaySpec.setFixedMs(1000); // 1 second delay
        delaySpec.setErrorRatePercent(100); // Always error

        long start = System.currentTimeMillis();
        delayService.applyDelay(delaySpec);
        long end = System.currentTimeMillis();
        
        long actualDelay = end - start;
        assertTrue(actualDelay < 100, "Should not apply delay when error is triggered, actual delay: " + actualDelay);
    }
}