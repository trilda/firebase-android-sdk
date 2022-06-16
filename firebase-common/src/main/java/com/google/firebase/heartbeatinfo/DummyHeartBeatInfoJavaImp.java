package com.google.firebase.heartbeatinfo;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseApp;
import com.google.firebase.components.Component;
import com.google.firebase.components.Dependency;
import com.google.firebase.platforminfo.UserAgentPublisher;

public class DummyHeartBeatInfoJavaImp implements DummyHeartBeatInfo {

  // an empty constructor
  DummyHeartBeatInfoJavaImp() {}

  @NonNull
  @Override
  public DummyHeartBeat getDummyHeartBeatCode(@NonNull String heartBeatTag) {
    return DummyHeartBeat.COMBINED;
  }

  @Override
  public String print(String value) {
    System.out.println("This is a Java Implementation");
    return "This is a sub class in Java  -> "+value;
  }

  public static @NonNull Component<DummyHeartBeatInfoJavaImp> component() {
    return Component.builder(DummyHeartBeatInfoJavaImp.class, DummyHeartBeatInfo.class)
        .add(Dependency.required(Context.class))
        .add(Dependency.required(FirebaseApp.class))
        .add(Dependency.setOf(HeartBeatConsumer.class))
        .add(Dependency.requiredProvider(UserAgentPublisher.class))
        .factory(c -> new DummyHeartBeatInfoJavaImp())
        .build();
  }
}
