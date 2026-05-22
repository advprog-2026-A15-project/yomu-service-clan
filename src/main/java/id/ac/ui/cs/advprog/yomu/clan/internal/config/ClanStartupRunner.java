package id.ac.ui.cs.advprog.yomu.clan.internal.config;

import id.ac.ui.cs.advprog.yomu.clan.internal.service.ClanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Recalculate semua tier clan saat startup agar threshold baru langsung berlaku
 * tanpa perlu trigger manual.
 */
@Component
public class ClanStartupRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(ClanStartupRunner.class);

    private final ClanService clanService;

    public ClanStartupRunner(ClanService clanService) {
        this.clanService = clanService;
    }

    @Override
    public void run(ApplicationArguments args) {
        logger.info("Recalculating all clan tiers on startup...");
        try {
            clanService.recalculateAllTiers();
            logger.info("Clan tier recalculation completed.");
        } catch (Exception e) {
            logger.warn("Clan tier recalculation failed on startup: {}", e.getMessage());
        }
    }
}
