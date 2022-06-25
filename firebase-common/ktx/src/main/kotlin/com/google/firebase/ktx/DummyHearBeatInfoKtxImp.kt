package com.google.firebase.ktx

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentContainer
import com.google.firebase.components.ComponentFactory
import com.google.firebase.components.Dependency
import com.google.firebase.heartbeatinfo.DummyHeartBeatInfo
import com.google.firebase.heartbeatinfo.DummyHeartBeatInfo.DummyHeartBeat
import com.google.firebase.heartbeatinfo.HeartBeatConsumer
import com.google.firebase.platforminfo.UserAgentPublisher

class DummyHearBeatInfoKtxImp : DummyHeartBeatInfo {

    override fun getDummyHeartBeatCode(heartBeatTag: String): DummyHeartBeat {
        return DummyHeartBeat.SDK
    }

    override fun print(value: String): String? {
        println("This is a Kotlin Implementation")
        return "This is a sub class  in Kotlin -> $value"
    }

    fun component(): Component<DummyHearBeatInfoKtxImp?> {
        return Component.builder(
            DummyHearBeatInfoKtxImp::class.java,
            DummyHeartBeatInfo::class.java
        )
//            .add(Dependency.required(Context::class.java))
//            .add(Dependency.required(FirebaseApp::class.java))
//            .add(Dependency.setOf(HeartBeatConsumer::class.java))
//            .add(Dependency.requiredProvider(UserAgentPublisher::class.java))
            .factory(ComponentFactory { c: ComponentContainer? -> DummyHearBeatInfoKtxImp() })
            .build()
    }
}