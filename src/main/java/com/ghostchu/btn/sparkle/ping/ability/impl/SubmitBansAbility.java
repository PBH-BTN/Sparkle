package com.ghostchu.btn.sparkle.ping.ability.impl;

import com.ghostchu.btn.sparkle.ping.ability.AbstractCronJobEndpointAbility;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SubmitBansAbility extends AbstractCronJobEndpointAbility {
    public SubmitBansAbility(
            @Value("${service.ping.ability.submitbans.interval}")
            long interval,
            @Value("${service.ping.ability.submitbans.endpoint}")
            String endpoint,
            @Value("${service.ping.ability.submitbans.random-initial-delay}")
            long randomInitialDelay
    ) {
        super(interval, randomInitialDelay, endpoint);
    }
}
