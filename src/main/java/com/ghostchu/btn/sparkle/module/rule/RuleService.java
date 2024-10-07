package com.ghostchu.btn.sparkle.module.rule;

import com.ghostchu.btn.sparkle.module.rule.internal.Rule;
import com.ghostchu.btn.sparkle.module.rule.internal.RuleRepository;
import com.ghostchu.btn.sparkle.util.TimeUtil;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;

@Service
public class RuleService {
    private final RuleRepository ruleRepository;

    public RuleService(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    /**
     * 获取所有仍在有效期内的规则列表
     *
     * @return 仍然处于有效期内的规则列表
     */
    @Cacheable({"unexpiredRules#600000"})
    public List<RuleDto> getUnexpiredRules() {
        return ruleRepository.findByExpiredAtGreaterThan(OffsetDateTime.now()).stream().map(this::toDto).toList();
    }

    /**
     * 获取所有规则，包括已过期规则
     *
     * @return 所有规则
     */
    public List<RuleDto> getAllRules() {
        List<RuleDto> ruleDtos = new ArrayList<>();
        for (Rule rule : ruleRepository.findAll()) {
            ruleDtos.add(toDto(rule));
        }
        return ruleDtos;
    }

    public List<RuleDto> getRulesMatchingCategory(String category) {
        return ruleRepository.findByCategory(category).stream().map(this::toDto).toList();
    }

    public List<RuleDto> getRulesMatchingType(String type) {
        return ruleRepository.findByType(type).stream().map(this::toDto).toList();
    }

    /**
     * 创建/保存更改 Rule 规则
     *
     * @param ruleDto 新的/更改后的 RuleDto
     * @return RuleDto（已填充 Id）
     */
    public RuleDto saveRule(RuleDto ruleDto) {
        Rule rule = new Rule();
        rule.setId(ruleDto.getId());
        rule.setCategory(ruleDto.getCategory());
        rule.setType(ruleDto.getType());
        rule.setContent(ruleDto.getContent());
        rule.setCreatedAt(TimeUtil.toUTC(ruleDto.getCreatedAt()));
        rule.setExpiredAt(TimeUtil.toUTC(ruleDto.getExpiredAt()));
        return toDto(ruleRepository.save(rule));
    }

    public RuleDto toDto(Rule rule) {
        return RuleDto.builder()
                .id(rule.getId())
                .category(rule.getCategory())
                .content(rule.getContent())
                .type(rule.getType())
                .createdAt(rule.getCreatedAt().getLong(ChronoField.MILLI_OF_DAY))
                .expiredAt(rule.getExpiredAt().getLong(ChronoField.MILLI_OF_DAY))
                .build();
    }
}
