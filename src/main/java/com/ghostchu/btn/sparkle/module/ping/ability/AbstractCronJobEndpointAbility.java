package com.ghostchu.btn.sparkle.module.ping.ability;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
public abstract class AbstractCronJobEndpointAbility extends AbstractCronJobAbility {
    private String endpoint;

    public AbstractCronJobEndpointAbility(long interval, long randomInitialDelay, String endpoint) {
        super(interval, randomInitialDelay);
        this.endpoint = endpoint;
    }
}
