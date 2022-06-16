package com.google.firebase.firestore

import android.util.Log
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.remote.FirebaseClientGrpcMetadataProvider
import com.google.firebase.firestore.remote.GrpcMetadataProvider
import com.google.firebase.ktx.Firebase
import org.junit.Test

private const val TAG = "TestLog"

class ComponentRegTest {


    @Test
    fun check_connection_to_firestore(){
        val db = Firebase.firestore
        Log.d(TAG, db.toString())
    }

    @Test
    fun check_component_inside_of_firestore(){
        val db = Firebase.firestore
        Log.d(TAG, db.authProvider.toString())
    }

    @Test
    fun check_current_registrar_inside_of_firestore(){
        val db = Firebase.firestore
        val reg = db.instanceRegistry as FirestoreMultiDbComponent
        val myContext = reg.context
        Log.d(TAG, myContext.toString())

        val myMetaProvider = reg.metadataProvider
        val GrpcMetadataProvider = myMetaProvider as FirebaseClientGrpcMetadataProvider
        val hb =GrpcMetadataProvider.heartBeatInfoProvider.get() // I can really get a HearBeatController Here!!!
        Log.d(TAG, hb.toString())

        val DummyMetaDataProvider = reg.firebaseClientDummyMetaDataProvider
        val dummyHeartBeat = DummyMetaDataProvider.heartBeatInfoProvider.get()
        Log.d(TAG, dummyHeartBeat.toString())
        Log.d(TAG, "=".repeat(20))
        val dummyDummyHeartBeat = DummyMetaDataProvider.dummyheartBeatInfoProvider.get()
        Log.d(TAG, dummyDummyHeartBeat.toString()) // this is null??? Why?
    }

    @Test
    fun check_registrar_inside_of_firestore(){
        val db = Firebase.firestore
        val reg = db.instanceRegistry as FirestoreMultiDbComponent
        val myInterface = reg.dummyHeartBeatInfoProvider.get() // this is the sub class I got!
        val str = myInterface.print("success")
        Log.d(TAG, myInterface.toString())
        Log.d(TAG, str.toString())
    }
}