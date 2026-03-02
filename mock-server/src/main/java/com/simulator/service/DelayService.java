package com.simulator.service;

import com.simulator.model.RequestMapping;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class DelayService {

    private final Random random = new Random();

    public void applyDelay(RequestMapping.DelaySpec delaySpec) {
        if (delaySpec == null) {
            return;
        }

        if (shouldTriggerError(delaySpec)) {
            return;
        }

        long delayMs = calculateDelay(delaySpec);
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean shouldTriggerError(RequestMapping.DelaySpec delaySpec) {
        if (delaySpec == null || delaySpec.getErrorRatePercent() == null || delaySpec.getErrorRatePercent() <= 0) {
            return false;
        }
        return random.nextInt(100) < delaySpec.getErrorRatePercent();
    }

    private long calculateDelay(RequestMapping.DelaySpec delaySpec) {
        if ("variable".equals(delaySpec.getMode())) {
            int min = delaySpec.getVariableMinMs() != null ? delaySpec.getVariableMinMs() : 0;
            int max = delaySpec.getVariableMaxMs() != null ? delaySpec.getVariableMaxMs() : min;
            if (max <= min) {
                return min;
            }
            return min + random.nextInt(max - min);
        } else if ("fixed".equals(delaySpec.getMode())) {
            return delaySpec.getFixedMs() != null ? delaySpec.getFixedMs() : 0;
        }
        return 0;
    }
}