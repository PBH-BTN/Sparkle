package com.ghostchu.btn.sparkle.module.ping.ability.impl;

import com.ghostchu.btn.sparkle.module.ping.ability.AbstractCronJobEndpointAbility;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@EqualsAndHashCode(callSuper = true)
@Data
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
