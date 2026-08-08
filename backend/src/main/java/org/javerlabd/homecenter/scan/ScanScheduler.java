package org.javerlabd.homecenter.scan;

import org.javerlabd.homecenter.config.HomeCenterProperties;
import org.javerlabd.homecenter.source.NoActiveSourceException;
import org.javerlabd.homecenter.source.SmbAccessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled and startup scan across all enabled sources. A missing source or unavailable
 * Samba must not bring down the server; the media center must run even while storage is offline.
 */
@Component
@Slf4j
public class ScanScheduler {

    private final ScanService scanService;
    private final boolean scanOnStartup;

    public ScanScheduler(ScanService scanService, HomeCenterProperties properties) {
        this.scanService = scanService;
        this.scanOnStartup = properties.scan().onStartup();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        scanService.closeInterruptedRuns();
        if (scanOnStartup) {
            triggerQuietly(ScanTrigger.STARTUP);
        }
    }

    @Scheduled(cron = "${homecenter.scan.cron}")
    public void onSchedule() {
        triggerQuietly(ScanTrigger.SCHEDULED);
    }

    private void triggerQuietly(ScanTrigger trigger) {
        try {
            scanService.triggerAll(trigger);
        } catch (NoActiveSourceException ex) {
            log.info("Sken ({}) preskočený — nie je nastavený žiadny zapnutý Samba zdroj", trigger);
        } catch (ScanAlreadyRunningException ex) {
            log.info("Sken ({}) preskočený — predchádzajúci ešte beží", trigger);
        } catch (SmbAccessException ex) {
            log.warn("Sken ({}) sa nepodarilo spustiť: {}", trigger, ex.getMessage());
        }
    }
}
