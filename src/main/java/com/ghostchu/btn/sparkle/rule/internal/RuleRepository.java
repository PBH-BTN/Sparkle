package com.ghostchu.btn.sparkle.rule.internal;

import com.ghostchu.btn.sparkle.repository.SparkleCommonRepository;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public interface RuleRepository extends SparkleCommonRepository<Rule, Long> {
    List<Rule> findByCategory(String category);

    List<Rule> findByType(String type);

    List<Rule> findByExpiredAtGreaterThan(Timestamp expiredAt);

}
