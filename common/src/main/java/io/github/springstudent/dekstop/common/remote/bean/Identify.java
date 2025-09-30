package io.github.springstudent.dekstop.common.remote.bean;

import io.github.springstudent.dekstop.common.bean.AtomicPositiveInteger;

import java.io.Serializable;

public abstract class Identify implements Serializable {
    private static AtomicPositiveInteger atomicPositiveInteger = new AtomicPositiveInteger();

    protected Integer genId() {
        return atomicPositiveInteger.getAndIncrement();
    }

}
