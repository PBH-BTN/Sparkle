package com.ghostchu.btn.sparkle.module.analyse.impl;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysedRuleRepository extends SparkleCommonRepository<AnalysedRule, Long> {
    //List<AnalysedRule> findByModule(String module);
    List<AnalysedRule> findByModuleOrderByIpAsc(String module);
    long deleteAllByModule(String module);
    List<AnalysedRule> findAll();

    default void replaceAll(String module, List<AnalysedRule> rules) {
        deleteAllByModule(module);
        saveAll(rules);
    }
}
