package com.ghostchu.btn.sparkle.module.ping.ability.impl;

import com.ghostchu.btn.sparkle.module.ping.ability.AbstractCronJobAbility;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ReconfigureAbility extends AbstractCronJobAbility {
    private final String version = UUID.randomUUID().toString();

    public ReconfigureAbility(
            @Value("${service.ping.ability.submitbans.interval}")
            long interval,
            @Value("${service.ping.ability.submitbans.random-initial-delay}")
            long randomInitialDelay
    ) {
        super(interval, randomInitialDelay);
    }
}
