package com.example.firestore_kotlin_serialization

import android.util.Log
import com.example.firestore_kotlin_serialization.testutil.IntegrationTestUtil
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.serialization.Serializable
import org.junit.Test

class EnumTest {

    companion object {
        val TAG: String = "LogTest"

        fun print(s: Any) {
            Log.d(TAG, s.toString())
        }
    }
    @Serializable
    enum class PaymentType() {
        SUCCESS,
        FAIL
    }

    @Serializable
    data class Payment(val status: PaymentType, val num: Long)

//    @Serializable
//    data class SomeIntegerClass(val num: Int = 100)

    @Test
    fun saveEnumClassObject() {
//        val encodedMap = encodeToMap(Payment(PaymentType.SUCCESS, 100L))
//        print(encodedMap)
//        docRefKotlin.set(encodedMap)
        val db = Firebase.firestore
        val docRefKotlin = db.collection("time").document("timestamp")
        val docSnapshot: DocumentSnapshot = IntegrationTestUtil.waitFor(docRefKotlin.get())
        Log.d(TAG, ">>>>>>>>>>>>>" + docSnapshot.data.toString())
        val decodeMap = docSnapshot.data?.let { decodeFromNestedMap<Payment>(it, null, null) }
        Log.d(TAG, ">>>>>>>>>>>>>" + decodeMap.toString())

//        val docRefKotlin = IntegrationTestUtil.testDocument("kotlin_set")
//        val docSnapshot = IntegrationTestUtil.waitFor(docRefKotlin.get())
//        val pojoMap = docSnapshot.data
//        val obj = CustomClassMapper.convertToCustomClass(pojoMap, PaymentType::class.java, docSnapshot.reference)
    }

//    @Test
//    fun testDecodeToLong() {
//        val myObject = SomeIntegerClass(99)
//        val docRefKotlin = IntegrationTestUtil.testDocument("integer")
//        docRefKotlin.set(myObject)
//        val docSnapshot = IntegrationTestUtil.waitFor(docRefKotlin.get())
//        print(" data saved in firestore is ${docSnapshot.data}")
//
//        val myDecodeObj = docSnapshot.get<SomeIntegerClass>()
//        print(" decoded obj from firestore is $myDecodeObj")
//    }

    data class TimeTestClass(@ServerTimestamp var time: Timestamp? = null, val number: Long = 150)

    @Test
    fun testServerTimeStamp() {
        val db = Firebase.firestore
        val docRefKotlin = db.collection("time").document("timestamp")

//        val map = mapOf<String, Any>("time" to FieldValue.serverTimestamp())
//        docRefKotlin.set(TimeTestClass())
        val docSnapshot: DocumentSnapshot = IntegrationTestUtil.waitFor(docRefKotlin.get())
        Log.d("LogTest", ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
        Log.d("LogTest", "$docSnapshot")
        Log.d("LogTest", "this is the raw data ${docSnapshot.data}")
        val myObject = docSnapshot.toObject<TimeTestClass>()
        Log.d("LogTest", "this is the decoded data ${docSnapshot.toObject<TimeTestClass>()}")
        Log.d("LogTest", "===================================")
        var ttt: Timestamp? = docSnapshot.getTimestamp("time", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        Log.d("LogTest", "$ttt")
    }
}
