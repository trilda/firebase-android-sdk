// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf.metrics;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import com.google.android.gms.common.util.VisibleForTesting;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.provider.FirebasePerfProvider;
import com.google.firebase.perf.session.PerfSession;
import com.google.firebase.perf.session.SessionManager;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.TraceMetric;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A class to capture the Android AppStart Trace information. The first time activity goes through
 * onCreate()->onStart()->onResume() sequence is captured as app start timer and a TraceMetric log
 * is sent to server.
 *
 * <p>The first time any activity (activityC) enters onCreate() method we record an onCreateTime.
 * The first time any activity (activityS) enters onStart() method we record an onStartTime. The
 * first time any activity (activityR) enters onResume() method we record an onResumeTime and this
 * activity is recorded as AppStartActivity, this is end of AppStart trace..
 *
 * <p>In reality activityC, activityS and activityR do not need to be the same activity.
 *
 * @hide
 */
public class AppStartTrace implements ActivityLifecycleCallbacks {

  private static final long MAX_LATENCY_BEFORE_UI_INIT = TimeUnit.MINUTES.toMicros(1);

  // Core pool size 0 allows threads to shut down if they're idle
  private static final int CORE_POOL_SIZE = 0;
  private static final int MAX_POOL_SIZE = 1; // Only need single thread
  private static final Handler sMainThreadHandler = new Handler(Looper.getMainLooper());

  private static volatile AppStartTrace instance;
  private static ExecutorService executorService;

  private boolean isRegisteredForLifecycleCallbacks = false;
  private final TransportManager transportManager;
  private final Clock clock;
  private Context appContext;
  /**
   * The first time onCreate() of any activity is called, the activity is saved as launchActivity.
   */
  private WeakReference<Activity> launchActivity;
  /**
   * The first time onResume() of any activity is called, the activity is saved as appStartActivity
   */
  private WeakReference<Activity> appStartActivity;

  /**
   * If the time difference between app starts and creation of any Activity is larger than
   * MAX_LATENCY_BEFORE_UI_INIT, set mTooLateToInitUI to true and we don't send AppStart Trace.
   */
  private boolean isTooLateToInitUI = false;

  private Timer appStartTime = null;
  private Timer onCreateTime = null;
  private Timer onStartTime = null;
  private Timer onResumeTime = null;
  private Timer onDrawTime = null;
  private long onDrawElapsedRealTime;
  private Timer fullyDrawnTime = null;

  private PerfSession startSession;
  private boolean isStartedFromBackground = false;

  /**
   * Called from onCreate() method of an activity by instrumented byte code.
   *
   * @param activity Activity class name.
   */
  @Keep
  public static void setLauncherActivityOnCreateTime(String activity) {
    // no-op, for backward compatibility with old version plugin.
  }

  /**
   * Called from onStart() method of an activity by instrumented byte code.
   *
   * @param activity Activity class name.
   */
  @Keep
  public static void setLauncherActivityOnStartTime(String activity) {
    // no-op, for backward compatibility with old version plugin.
  }
  /**
   * Called from onResume() method of an activity by instrumented byte code.
   *
   * @param activity Activity class name.
   */
  @Keep
  public static void setLauncherActivityOnResumeTime(String activity) {
    // no-op, for backward compatibility with old version plugin.
  }

  public static AppStartTrace getInstance() {
    return instance != null ? instance : getInstance(TransportManager.getInstance(), new Clock());
  }

  static AppStartTrace getInstance(TransportManager transportManager, Clock clock) {
    if (instance == null) {
      synchronized (AppStartTrace.class) {
        if (instance == null) {
          instance =
              new AppStartTrace(
                  transportManager,
                  clock,
                  new ThreadPoolExecutor(
                      CORE_POOL_SIZE,
                      MAX_POOL_SIZE,
                      /* keepAliveTime= */ MAX_LATENCY_BEFORE_UI_INIT + 10,
                      TimeUnit.SECONDS,
                      new LinkedBlockingQueue<>()));
        }
      }
    }
    return instance;
  }

  AppStartTrace(
      @NonNull TransportManager transportManager,
      @NonNull Clock clock,
      @NonNull ExecutorService executorService) {
    this.transportManager = transportManager;
    this.clock = clock;
    this.executorService = executorService;
  }

  private void postAfterDraw(Runnable recordFunction) {

  }

  private void recordFirstOnDraw() {
    if (this.onDrawElapsedRealTime != 0) {
      return;
    }
    this.onDrawTime = clock.getTime();
    this.onDrawElapsedRealTime = SystemClock.elapsedRealtime();

    // Start when ContentProvider is loaded
    executorService.execute(this::logColdStartFromClassLoading);
    // Start right before app is about to get loaded to memory. No app code has executed yet.
    executorService.execute(this::logColdStartFromBindApplication);

    if (isRegisteredForLifecycleCallbacks) {
      // After AppStart trace is logged, we can unregister this callback.
      unregisterActivityLifecycleCallbacks();
    }
  }

  public void reportFullyDrawn() {
    if (!isStartedFromBackground
        && Thread.currentThread() == Looper.getMainLooper().getThread()
        && fullyDrawnTime == null) {
      this.fullyDrawnTime = clock.getTime();
      executorService.execute(this::logColdStartupFullyDrawn);
    }
  }

  /** Called from FirebasePerfProvider to register this callback. */
  public synchronized void registerActivityLifecycleCallbacks(@NonNull Context context) {
    // Make sure the callback is registered only once.
    if (isRegisteredForLifecycleCallbacks) {
      return;
    }
    Context appContext = context.getApplicationContext();
    if (appContext instanceof Application) {
      ((Application) appContext).registerActivityLifecycleCallbacks(this);
      isRegisteredForLifecycleCallbacks = true;
      this.appContext = appContext;
    }
  }

  /** Unregister this callback after AppStart trace is logged. */
  public synchronized void unregisterActivityLifecycleCallbacks() {
    if (!isRegisteredForLifecycleCallbacks) {
      return;
    }
    ((Application) appContext).unregisterActivityLifecycleCallbacks(this);
    isRegisteredForLifecycleCallbacks = false;
  }

  @Override
  public synchronized void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    if (isStartedFromBackground || onCreateTime != null // An activity already called onCreate()
    ) {
      return;
    }



    launchActivity = new WeakReference<Activity>(activity);
    onCreateTime = clock.getTime();

    if (FirebasePerfProvider.getAppStartTime().getDurationMicros(onCreateTime)
        > MAX_LATENCY_BEFORE_UI_INIT) {
      isTooLateToInitUI = true;
    }
  }

  @Override
  public synchronized void onActivityStarted(Activity activity) {
    if (isStartedFromBackground
        || onStartTime != null // An activity already called onStart()
        || isTooLateToInitUI) {
      return;
    }
    onStartTime = clock.getTime();
  }

  @Override
  public synchronized void onActivityResumed(Activity activity) {
    if (isStartedFromBackground
        || isTooLateToInitUI) {
      return;
    }

    appStartActivity = new WeakReference<Activity>(activity);

    onResumeTime = clock.getTime();
    this.appStartTime = FirebasePerfProvider.getAppStartTime();
    this.startSession = SessionManager.getInstance().perfSession();
    AndroidLogger.getInstance()
        .debug(
            "onResume(): "
                + activity.getClass().getName()
                + ": "
                + this.appStartTime.getDurationMicros(onResumeTime)
                + " microseconds");

    // Log the app start trace in a non-main thread.
    executorService.execute(this::logAppStartTrace);

    View rootView = activity.findViewById(android.R.id.content);
    FirstOnDrawListener.registerFirstOnDrawListener(rootView, () -> sMainThreadHandler.postAtFrontOfQueue(this::recordFirstOnDraw));
  }

  private void logColdStartFromClassLoading() {
    TraceMetric.Builder metric =
        TraceMetric.newBuilder()
            .setName("cold_start")
            .setClientStartTimeUs(getappStartTime().getMicros())
            .setDurationUs(getappStartTime().getDurationMicros(onDrawTime));
    metric.addPerfSessions(this.startSession.build());

    transportManager.log(metric.build(), ApplicationProcessState.FOREGROUND_BACKGROUND);
  }

  private void logColdStartFromBindApplication() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
      return;
    }
    long duration =
        TimeUnit.MILLISECONDS.toMicros(onDrawElapsedRealTime - Process.getStartElapsedRealtime());
    long startTime = onDrawTime.getMicros() - duration;
    TraceMetric.Builder metric =
        TraceMetric.newBuilder()
            .setName("_experiment_1")
            .setClientStartTimeUs(startTime)
            .setDurationUs(duration);
    metric.addPerfSessions(this.startSession.build());

    transportManager.log(metric.build(), ApplicationProcessState.FOREGROUND_BACKGROUND);
  }

  private void logColdStartupFullyDrawn() {
    TraceMetric.Builder fromClassLoaded =
        TraceMetric.newBuilder()
            .setName("Time_To_Full_Display")
            .setClientStartTimeUs(getappStartTime().getMicros())
            .setDurationUs(getappStartTime().getDurationMicros(fullyDrawnTime));
    fromClassLoaded.addPerfSessions(this.startSession.build());

    transportManager.log(fromClassLoaded.build(), ApplicationProcessState.FOREGROUND_BACKGROUND);
  }

  private void logAppStartTrace() {
    TraceMetric.Builder metric =
        TraceMetric.newBuilder()
            .setName(Constants.TraceNames.APP_START_TRACE_NAME.toString())
            .setClientStartTimeUs(getappStartTime().getMicros())
            .setDurationUs(getappStartTime().getDurationMicros(onResumeTime));
    List<TraceMetric> subtraces = new ArrayList<>(/* initialCapacity= */ 3);

    TraceMetric.Builder traceMetricBuilder =
        TraceMetric.newBuilder()
            .setName(Constants.TraceNames.ON_CREATE_TRACE_NAME.toString())
            .setClientStartTimeUs(getappStartTime().getMicros())
            .setDurationUs(getappStartTime().getDurationMicros(onCreateTime));
    subtraces.add(traceMetricBuilder.build());

    traceMetricBuilder = TraceMetric.newBuilder();
    traceMetricBuilder
        .setName(Constants.TraceNames.ON_START_TRACE_NAME.toString())
        .setClientStartTimeUs(onCreateTime.getMicros())
        .setDurationUs(onCreateTime.getDurationMicros(onStartTime));
    subtraces.add(traceMetricBuilder.build());

    traceMetricBuilder = TraceMetric.newBuilder();
    traceMetricBuilder
        .setName(Constants.TraceNames.ON_RESUME_TRACE_NAME.toString())
        .setClientStartTimeUs(onStartTime.getMicros())
        .setDurationUs(onStartTime.getDurationMicros(onResumeTime));
    subtraces.add(traceMetricBuilder.build());

    metric.addAllSubtraces(subtraces).addPerfSessions(this.startSession.build());

    transportManager.log(metric.build(), ApplicationProcessState.FOREGROUND_BACKGROUND);
  }

  @Override
  public void onActivityPaused(Activity activity) {}

  @Override
  public synchronized void onActivityStopped(Activity activity) {}

  @Override
  public void onActivityDestroyed(Activity activity) {}

  @Override
  public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

  /**
   * See Code Search for reference on how to test this.
   */
  @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
  private static final class FirstOnDrawListener implements ViewTreeObserver.OnDrawListener {
    private final AtomicReference<View> viewReference;
    private final Runnable onDrawCallback;

    private FirstOnDrawListener(View view, Runnable onDrawCallback) {
      this.viewReference = new AtomicReference<>(view);
      this.onDrawCallback = onDrawCallback;
    }

    @Override
    public void onDraw() {
      View view = viewReference.getAndSet(null);
      if (view == null) {
        return;
      }
      // OnDrawListeners cannot be removed within onDraw, so we remove it with a
      // GlobalLayoutListener
      view
          .getViewTreeObserver()
          .addOnGlobalLayoutListener(
              () -> view.getViewTreeObserver().removeOnDrawListener(this));
      onDrawCallback.run();
    }

    public static void registerFirstOnDrawListener(View view, Runnable onDrawCallback) {
      // Handle bug prior to API 26 where OnDrawListener from the floating ViewTreeObserver is not
      // merged into the real ViewTreeObserver.
      // https://android.googlesource.com/platform/frameworks/base/+/9f8ec54244a5e0343b9748db3329733f259604f3
      if (Build.VERSION.SDK_INT >= 26 || (view.getViewTreeObserver().isAlive() && view.getWindowToken() != null)) {
        view.getViewTreeObserver().addOnDrawListener(new FirstOnDrawListener(view, onDrawCallback));
      } else {
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
          @Override
          public void onViewAttachedToWindow(View view) {
            view.getViewTreeObserver().addOnDrawListener(new FirstOnDrawListener(view, onDrawCallback));
            view.removeOnAttachStateChangeListener(this);
          }

          @Override
          public void onViewDetachedFromWindow(View view) {
            view.removeOnAttachStateChangeListener(this);
          }
        });
      }
    }
  }

  /**
   * We use StartFromBackgroundRunnable to detect if app is started from background or foreground.
   * If app is started from background, we do not generate AppStart trace. This runnable is posted
   * to main UI thread from FirebasePerfProvider. If app is started from background, this runnable
   * will be executed before any activity's onCreate() method. If app is started from foreground,
   * activity's onCreate() method is executed before this runnable.
   */
  public static class StartFromBackgroundRunnable implements Runnable {
    private final AppStartTrace trace;

    public StartFromBackgroundRunnable(final AppStartTrace trace) {
      this.trace = trace;
    }

    @Override
    public void run() {
      // if no activity has ever been created.
      if (trace.onCreateTime == null) {
        trace.isStartedFromBackground = true;
      }
    }
  }

  @VisibleForTesting
  @Nullable
  Activity getLaunchActivity() {
    return launchActivity.get();
  }

  @VisibleForTesting
  @Nullable
  Activity getAppStartActivity() {
    return appStartActivity.get();
  }

  @VisibleForTesting
  Timer getappStartTime() {
    return appStartTime;
  }

  @VisibleForTesting
  Timer getOnCreateTime() {
    return onCreateTime;
  }

  @VisibleForTesting
  Timer getOnStartTime() {
    return onStartTime;
  }

  @VisibleForTesting
  Timer getOnResumeTime() {
    return onResumeTime;
  }

  @VisibleForTesting
  void setIsStartFromBackground() {
    isStartedFromBackground = true;
  }
}
