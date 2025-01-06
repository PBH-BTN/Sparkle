package com.ghostchu.btn.sparkle.module.analyse.impl;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AnalysedRuleRepository extends SparkleCommonRepository<AnalysedRule, Long> {
    //List<AnalysedRule> findByModule(String module);
    List<AnalysedRule> findByModuleOrderByIpAsc(String module);

    @Query("SELECT DISTINCT module FROM AnalysedRule")
    List<String> getAllModules();

    long deleteAllByModule(String module);

    List<AnalysedRule> findAll();

    default void replaceAll(String module, Collection<AnalysedRule> rules) {
        deleteAllByModule(module);
        saveAll(rules);
    }
}
