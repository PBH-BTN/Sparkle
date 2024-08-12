package com.ghostchu.btn.sparkle.ping.ability.impl;

import com.ghostchu.btn.sparkle.ping.ability.AbstractCronJobEndpointAbility;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SubmitPeersAbility extends AbstractCronJobEndpointAbility {
    public SubmitPeersAbility(
            @Value("${service.ping.ability.submitpeers.interval}")
            long interval,
            @Value("${service.ping.ability.submitpeers.endpoint}")
            String endpoint,
            @Value("${service.ping.ability.submitpeers.random-initial-delay}")
            long randomInitialDelay
    ) {
        super(interval, randomInitialDelay, endpoint);
    }
}
