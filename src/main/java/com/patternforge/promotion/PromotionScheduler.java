package com.patternforge.promotion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that runs the promotion pipeline periodically.
 * Promotes conversational patterns to project standard (promotion_count >= 5)
 * and project standards to global standard (used in 3+ distinct projects).
 *
 * <p>Interval is configurable via {@code patternforge.promotion.interval-ms} (default 1 hour).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PromotionScheduler {

    private final PatternPromotionService patternPromotionService;

    @Scheduled(fixedRateString = "${patternforge.promotion.interval-ms:3600000}")
    public void runPromotionPipeline() {
        log.info("Running scheduled promotion pipeline");
        try {
            int promoted = patternPromotionService.checkAndPromotePatterns();
            if (promoted > 0) {
                log.info("Scheduled promotion complete: {} patterns promoted", promoted);
            } else {
                log.debug("Scheduled promotion complete: no patterns eligible for promotion");
            }
        } catch (Exception exception) {
            log.error("Scheduled promotion pipeline failed", exception);
        }
    }
}
