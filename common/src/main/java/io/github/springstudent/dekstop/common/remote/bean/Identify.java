package io.github.springstudent.dekstop.common.remote.bean;

import io.github.springstudent.dekstop.common.bean.AtomicPositiveInteger;

public abstract class Identify {
    private static AtomicPositiveInteger atomicPositiveInteger = new AtomicPositiveInteger();

    protected Integer id;

    protected Integer genId() {
        return atomicPositiveInteger.getAndIncrement();
    }

    public Integer getId() {
        return id;
    }
}
