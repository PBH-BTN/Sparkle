package com.ghostchu.btn.sparkle.util.compare;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

public enum StringCompareMethod implements CriteriaBuilderSupport<String>{
    CONTAINS,
    NOT_CONTAINS,
    EQUALS,
    NOT_EQUALS,
    STARTS_WITH,
    NOT_STARTS_WITH,
    ENDS_WITH,
    NOT_ENDS_WITH;

    @Override
    public Predicate criteriaBuilder(CriteriaBuilder criteriaBuilder, Expression<String> expression, String value) {
        return switch (this){
            case CONTAINS -> criteriaBuilder.like(expression, "%"+value+"%");
            case NOT_CONTAINS -> criteriaBuilder.notLike(expression, "%"+value+"%");
            case EQUALS -> criteriaBuilder.equal(expression, value);
            case NOT_EQUALS -> criteriaBuilder.notEqual(expression, value);
            case STARTS_WITH -> criteriaBuilder.like(expression, value+"%");
            case NOT_STARTS_WITH -> criteriaBuilder.notLike(expression, value+"%");
            case ENDS_WITH -> criteriaBuilder.like(expression, "%"+value);
            case NOT_ENDS_WITH -> criteriaBuilder.notLike(expression, "%"+value);
        };
    }
}
