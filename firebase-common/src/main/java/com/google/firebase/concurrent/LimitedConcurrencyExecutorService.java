package com.google.firebase.concurrent;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class LimitedConcurrencyExecutorService extends LimitedConcurrencyExecutor
    implements ExecutorService {
  private final ExecutorService delegate;

  LimitedConcurrencyExecutorService(ExecutorService delegate, int concurrency) {
    super(delegate, concurrency);
    this.delegate = delegate;
  }

  @Override
  public void shutdown() {
    throw new UnsupportedOperationException("Shutting down is not allowed.");
  }

  @Override
  public List<Runnable> shutdownNow() {
    throw new UnsupportedOperationException("Shutting down is not allowed.");
  }

  @Override
  public boolean isShutdown() {
    return delegate.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return delegate.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return delegate.awaitTermination(timeout, unit);
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    FutureTask<T> ft = new FutureTask<>(task);
    execute(ft);
    return ft;
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    return submit(
        () -> {
          task.run();
          return result;
        });
  }

  @Override
  public Future<?> submit(Runnable task) {
    return submit(
        () -> {
          task.run();
          return null;
        });
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
      throws InterruptedException {
    return delegate.invokeAll(tasks);
  }

  @Override
  public <T> List<Future<T>> invokeAll(
      Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException {
    return delegate.invokeAll(tasks, timeout, unit);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
      throws ExecutionException, InterruptedException {
    return delegate.invokeAny(tasks);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws ExecutionException, InterruptedException, TimeoutException {
    return delegate.invokeAny(tasks, timeout, unit);
  }
}
