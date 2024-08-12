package com.ghostchu.btn.sparkle.ping.ability;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
public abstract class AbstractCronJobAbility extends AbstractPingAbility {
    private long interval;

    @JsonProperty("random_initial_delay")
    private long randomInitialDelay;
}
