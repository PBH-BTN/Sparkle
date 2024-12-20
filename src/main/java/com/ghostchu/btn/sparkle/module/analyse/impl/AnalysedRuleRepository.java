package com.ghostchu.btn.sparkle.module.analyse.impl;

import lombok.Cleanup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AnalysedRuleRepository {
    //List<AnalysedRule> findByModule(String module);
    @Autowired
    private RedisTemplate<String, AnalysedRule> redisTemplate;

    public void deleteAllByModule(String module) {
        redisTemplate.unlink("analysed_rules:" + module);
    }

    public Set<AnalysedRule> findAll() {
        Set<AnalysedRule> analysedRules = new HashSet<>();
        @Cleanup
        var it = redisTemplate.opsForSet().scan("analysed_rules:*", ScanOptions.scanOptions().build());
        while (it.hasNext()) {
            analysedRules.add(it.next());
        }
        return analysedRules;
    }

    public Set<AnalysedRule> findAllByModule(String module) {
        return redisTemplate.opsForSet().members("analysed_rules:" + module);
    }

    public void replaceAll(String module, List<AnalysedRule> rules) {
        deleteAllByModule(module);
        redisTemplate.opsForSet().add("analysed_rules:" + module, rules.toArray(new AnalysedRule[0]));
    }
}
