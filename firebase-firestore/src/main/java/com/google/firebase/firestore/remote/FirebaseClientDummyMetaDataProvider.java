package com.google.firebase.firestore.remote;

import androidx.annotation.NonNull;

import com.google.firebase.heartbeatinfo.DummyHeartBeatInfo;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.inject.Provider;

public class FirebaseClientDummyMetaDataProvider {
  public final Provider<HeartBeatInfo> heartBeatInfoProvider;
  public final Provider<DummyHeartBeatInfo> dummyheartBeatInfoProvider;

  public FirebaseClientDummyMetaDataProvider(
      @NonNull Provider<HeartBeatInfo> heartBeatInfoProvider,
      @NonNull Provider<DummyHeartBeatInfo> dummyheartBeatInfoProvider) {

    this.heartBeatInfoProvider = heartBeatInfoProvider;
    this.dummyheartBeatInfoProvider = dummyheartBeatInfoProvider;
  }
}
