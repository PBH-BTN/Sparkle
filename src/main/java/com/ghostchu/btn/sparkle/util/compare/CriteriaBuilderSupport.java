package com.ghostchu.btn.sparkle.util.compare;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

public interface CriteriaBuilderSupport<T> {

    Predicate criteriaBuilder (CriteriaBuilder criteriaBuilder, Expression<T> expression, T value);
}
