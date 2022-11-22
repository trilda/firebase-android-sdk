package com.google.firebase.appdistribution.impl;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.appdistribution.UpdateTask;
import com.google.firebase.concurrent.FirebaseExecutors;
import java.util.concurrent.Executor;

class SequentialReference<T> {

  interface SequentialReferenceConsumer<T> {
    void consume(T value);
  }

  interface SequentialReferenceTransformer<T> {
    T transform(T value);
  }

  interface SequentialReferenceTaskTransformer<T, U> {
    Task<U> transform(T value);
  }

  interface SequentialReferenceUpdateTaskTransformer<T> {
    UpdateTask transform(T value);
  }

  private final Executor sequentialExecutor;

  private T value;

  SequentialReference(Executor baseExecutor) {
    sequentialExecutor = FirebaseExecutors.newSequentialExecutor(baseExecutor);
  }

  SequentialReference(Executor baseExecutor, T initialValue) {
    this(baseExecutor);
    value = initialValue;
  }

  T get() {
    return value;
  }

  void set(T newValue) {
    sequentialExecutor.execute(
        () -> {
          value = newValue;
        });
  }

  void consume(SequentialReferenceConsumer<T> listener) {
    sequentialExecutor.execute(
        () -> {
          listener.consume(value);
        });
  }

  void transform(SequentialReferenceTransformer<T> transformer) {
    sequentialExecutor.execute(
        () -> {
          value = transformer.transform(value);
        });
  }

  <U> Task<U> applyTask(SequentialReferenceTaskTransformer<T, U> transformer) {
    TaskCompletionSource<U> taskCompletionSource = new TaskCompletionSource<>();
    sequentialExecutor.execute(
        () ->
          transformer.transform(value)
              .addOnSuccessListener(taskCompletionSource::setResult)
              .addOnFailureListener(taskCompletionSource::setException));
    return taskCompletionSource.getTask();
  }

  UpdateTask applyUpdateTask(SequentialReferenceUpdateTaskTransformer<T> transformer) {
    UpdateTaskImpl updateTask = new UpdateTaskImpl();
    sequentialExecutor.execute(
        () ->
            transformer.transform(value)
                .addOnProgressListener(updateTask::updateProgress)
                .addOnSuccessListener(result -> updateTask.setResult())
                .addOnFailureListener(updateTask::setException));
    return updateTask;
  }
}
