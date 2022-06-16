package com.google.firebase.heartbeatinfo;

import androidx.annotation.NonNull;

public interface DummyHeartBeatInfo {
  enum DummyHeartBeat {
    NONE(0),
    SDK(1),
    GLOBAL(2),
    COMBINED(3);

    private final int code;

    DummyHeartBeat(int code) {
      this.code = code;
    }

    public int getCode() {
      return this.code;
    }
  }

  @NonNull
  DummyHeartBeat getDummyHeartBeatCode(@NonNull String heartBeatTag);

  public String print(String value);
}
