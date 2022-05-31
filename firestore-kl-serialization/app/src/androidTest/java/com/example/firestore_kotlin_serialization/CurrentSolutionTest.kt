package com.example.firestore_kotlin_serialization

import android.util.Log
import com.example.firestore_kotlin_serialization.testutil.IntegrationTestUtil
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.util.CustomClassMapper
import com.google.firebase.ktx.Firebase
import org.junit.Test

class CurrentSolutionTest {

    enum class PaymentType() {
        SUCCESS,
        FAIL,
    }
    data class Payment(val status: PaymentType? = null)

    enum class PaymentTypeInLowerCase() {
        success,
        fail
    }
    data class PaymentInLowerCase(val status: PaymentTypeInLowerCase? = null)

    // Currently, customer has to handle this logic inside of their client side code:
    inline fun DocumentSnapshot.getPayment(): Payment = this.get("status").toString().uppercase().let {
        CustomClassMapper.convertToCustomClass(
            data.apply { this?.set("status", it) },
            Payment::class.java,
            reference
        )
    }

    @Test
    fun currentEnumSolution() {
        val docRefCurrent = IntegrationTestUtil.testDocument("currentSolution")
        val payment = PaymentInLowerCase(PaymentTypeInLowerCase.success)
        docRefCurrent.set(payment)

        val docSnapshot = IntegrationTestUtil.waitFor(docRefCurrent.get())
        Log.d("LogTest", "${docSnapshot.data}")

//        val decodeObj = docSnapshot.toObject<Payment>()
//        java.lang.RuntimeException: Could not deserialize object. Could not find enum value of com.example.firestore_kotlin_serialization.CurrentSolutionTest$PaymentType for value "success" (found in field 'status')
        val decodeObj = docSnapshot.getPayment()
        Log.d("LogTest", "$decodeObj")
    }

    data class TimeTest(val ts: Timestamp? = Timestamp.now())
    data class Student(val name: String? = "mayson")
    @Test
    fun currentTimestampSolution() {
        val db = Firebase.firestore
        val docRefKotlin = db.collection("time").document("timestamp")

        docRefKotlin.set(TimeTest())

        val docSnapshot = IntegrationTestUtil.waitFor(docRefKotlin.get())
        Log.d("LogTest", "${docSnapshot.data}")
        val result = docSnapshot.toObject<TimeTest>()
        Log.d("LogTest", "$result")
    }
}
