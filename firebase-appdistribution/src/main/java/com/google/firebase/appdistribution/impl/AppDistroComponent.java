// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.appdistribution.impl;

import android.content.Context;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.appdistribution.FirebaseAppDistribution;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import dagger.Binds;
import dagger.BindsInstance;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import java.util.concurrent.Executor;
import javax.inject.Named;
import javax.inject.Singleton;

/** @hide */
@Component(modules = AppDistroComponent.MainModule.class)
@Singleton
interface AppDistroComponent {

  FirebaseAppDistribution getAppDistribution();

  FirebaseAppDistributionLifecycleNotifier getLifecycleNotifier();

  ActivityInjector getActivityInjector();

  @Component.Builder
  interface Builder {

    @BindsInstance
    Builder setApplicationContext(Context context);

    @BindsInstance
    Builder setOptions(FirebaseOptions options);

    @BindsInstance
    Builder setApp(FirebaseApp app);

    @BindsInstance
    Builder setFis(Provider<FirebaseInstallationsApi> fis);

    @BindsInstance
    Builder setBlockingExecutor(@Blocking Executor executor);

    AppDistroComponent build();
  }

  @Module
  interface MainModule {
    @Binds
    FirebaseAppDistribution bindAppDistro(FirebaseAppDistributionImpl impl);

    @Provides
    @Named("appName")
    static String providesAppName(FirebaseApp app) {
      return app.getName();
    }
  }
}
