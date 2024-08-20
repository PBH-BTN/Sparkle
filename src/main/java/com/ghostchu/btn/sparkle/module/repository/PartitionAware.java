package com.ghostchu.btn.sparkle.module.repository;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.hibernate.annotations.PartitionKey;

@MappedSuperclass
@FilterDef(
        name = PartitionAware.PARTITION_KEY,
        parameters = @ParamDef(
                name = PartitionAware.PARTITION_KEY,
                type = String.class
        )
)
@Filter(
        name = PartitionAware.PARTITION_KEY,
        condition = "partition_key = :partitionKey"
)
public abstract class PartitionAware<T extends PartitionAware> {

    public static final String PARTITION_KEY = "partitionKey";

    @Column(name = "partition_key")
    @PartitionKey
    private String partitionKey;

    public String getPartitionKey() {
        return partitionKey;
    }

    public T setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
        return (T) this;
    }

}