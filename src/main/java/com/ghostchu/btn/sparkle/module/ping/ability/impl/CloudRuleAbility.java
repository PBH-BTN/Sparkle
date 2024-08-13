package com.ghostchu.btn.sparkle.module.ping.ability.impl;

import com.ghostchu.btn.sparkle.module.ping.ability.AbstractCronJobEndpointAbility;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CloudRuleAbility extends AbstractCronJobEndpointAbility {

    public CloudRuleAbility(
            @Value("${service.ping.ability.cloudrule.interval}")
            long interval,
            @Value("${service.ping.ability.cloudrule.endpoint}")
            String endpoint,
            @Value("${service.ping.ability.cloudrule.random-initial-delay}")
            long randomInitialDelay
    ) {
        super(interval, randomInitialDelay, endpoint);
    }
}
