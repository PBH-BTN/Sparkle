package com.ghostchu.btn.sparkle.util.compare;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import java.io.Serializable;

public enum NumberCompareMethod implements CriteriaBuilderSupport<Number>, Serializable {
    LESS_THAN,
    LESS_THAN_EQUAL,
    GREATER_THAN,
    GREATER_THAN_EQUAL,
    EQUAL;

    @Override
    public Predicate criteriaBuilder(CriteriaBuilder criteriaBuilder, Expression<Number> expression, Number value) {
        return switch (this){
            case LESS_THAN -> criteriaBuilder.lt(expression, value);
            case LESS_THAN_EQUAL -> criteriaBuilder.le(expression, value);
            case GREATER_THAN -> criteriaBuilder.gt(expression, value);
            case GREATER_THAN_EQUAL -> criteriaBuilder.ge(expression, value);
            case EQUAL -> criteriaBuilder.equal(expression, value);
        };
    }
}
