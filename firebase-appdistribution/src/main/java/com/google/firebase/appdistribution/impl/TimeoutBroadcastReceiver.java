package com.google.firebase.appdistribution.impl;

import static com.google.firebase.appdistribution.impl.FirebaseAppDistributionNotificationsManager.TIMEOUT_INTENT;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import javax.inject.Inject;

public class TimeoutBroadcastReceiver extends BroadcastReceiver {
  private static final String TAG = "TimeoutBroadcastReceiver";

  @Inject
  FirebaseAppDistributionNotificationsManager notificationsManager;

  @Override
  public void onReceive(Context context, Intent intent) {
    AppDistroComponent.getInstance().inject(this);

    String action = intent.getAction();

    if ( action.equals(TIMEOUT_INTENT) ) {
      notificationsManager.cancelFeedbackNotification();
    }
  }
}
