package com.ghostchu.btn.sparkle.module.ping.ability.impl;

import com.ghostchu.btn.sparkle.module.ping.ability.AbstractCronJobEndpointAbility;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public class SubmitHistoriesAbility extends AbstractCronJobEndpointAbility {
    public SubmitHistoriesAbility(
            @Value("${service.ping.ability.submithistories.interval}")
            long interval,
            @Value("${service.ping.ability.submithistories.endpoint}")
            String endpoint,
            @Value("${service.ping.ability.submithistories.random-initial-delay}")
            long randomInitialDelay
    ) {
        super(interval, randomInitialDelay, endpoint);
    }
}
