package com.example.cortebe.domain.judge.runner;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public final class ExecutionClock {
    private final long budgetMs;
    private final long startupMs;
    private final LongSupplier nano;
    private final long created;
    private final AtomicLong started = new AtomicLong(Long.MIN_VALUE);
    private final AtomicLong stopped = new AtomicLong(Long.MIN_VALUE);

    public ExecutionClock(long budgetMs, long startupMs) {
        this(budgetMs, startupMs, System::nanoTime);
    }

    public ExecutionClock(long budgetMs, long startupMs, LongSupplier nano) {
        this.budgetMs = budgetMs;
        this.startupMs = startupMs;
        this.nano = nano;
        created = nano.getAsLong();
    }

    public void start() { started.compareAndSet(Long.MIN_VALUE, nano.getAsLong()); }
    public void stop() { stopped.compareAndSet(Long.MIN_VALUE, nano.getAsLong()); }
    public boolean started() { return started.get() != Long.MIN_VALUE; }
    public long elapsedMillis() {
        long end = stopped.get() == Long.MIN_VALUE ? nano.getAsLong() : stopped.get();
        return started() ? Math.max(0, TimeUnit.NANOSECONDS.toMillis(end - started.get())) : 0;
    }
    public boolean expired() { return started() && elapsedMillis() >= budgetMs; }
    public boolean startupExpired() { return !started() && TimeUnit.NANOSECONDS.toMillis(nano.getAsLong() - created) >= startupMs; }
}
