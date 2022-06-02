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
// @file:UseSerializers(TimestampSerializer::class)

// @file:UseSerializers(DateSerializer::class, TimestampSerializer::class)

package com.example.firestore_kotlin_serialization
import android.util.Log
import com.example.firestore_kotlin_serialization.annotations.KDocumentId
import com.example.firestore_kotlin_serialization.annotations.KServerTimestamp
import com.example.firestore_kotlin_serialization.annotations.KThrowOnExtraProperties
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import java.util.*
import kotlin.collections.ArrayList

data class FirestoreDocument(val id: String, val documentReference: DocumentReference)

fun debugPrint(s1: String, s2: String) {
//    println(s1 + "___" + s2)
    Log.d(s1, s2)
}

abstract class NestDecoder(
    open val nestedObject: Any = Unit,
    open var documentId: FirestoreDocument? = null,
    open var documentSnapshot: DocumentSnapshot? = null,
) : AbstractDecoder() {

    var elementIndex: Int = 0

    var currentValueNotNull: Boolean = true

    abstract val decodeValueList: List<*>

    // Take care of Enum name decoding
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val decodeValue = decodeValueList.elementAt(elementIndex - 1)!! // success
        debugPrint("LogTest", ">>>>>>>>>>>>> decodeValue is:" + decodeValue)
        val elementsList = enumDescriptor.elementNames.toList().map { it.toLowerCase() }
//        debugPrint("LogTest", ">>>>>>>>>>>>> all the elements fields are:" + )
//        return enumDescriptor.getElementIndex(decodeValue.toString())
        return elementsList.indexOf(decodeValue)
    }

    // Take care of Long to Int decoding
    override fun decodeInt(): Int {
        debugPrint("LogTest", ">>>>>>>>>>>>> I need to decode Int now")
        val decodeValue = decodeValueList.elementAt(elementIndex - 1)!!
        if (decodeValue is Int) {
            return decodeValue
        } else {
            return (decodeValue as Long).toInt()
        }
    }

    fun decodeTimestamp(): Timestamp{
        // if pass a concreate Timestamp to firestore, it save and read back as a Timestamp instance
        // if let @KServerTimestamp annotation generate the timestamp, it read back as a map?!!!

        val decodeValue = decodeValueList.elementAt(elementIndex - 1)!!
        if (decodeValue is Timestamp){
            return decodeValue
        }else{
            val linkedHashMap =decodeValue as Map<*, *>
            val timestamp: Timestamp = Timestamp(linkedHashMap.get("seconds") as Long,
                linkedHashMap.get("nanoseconds") as Int
            )
            return timestamp
        }

    }

    final override fun decodeValue(): Any {
        debugPrint("LogTest", "Decode value is =========== ${decodeValueList.elementAt(elementIndex - 1)!!}=======")
        return decodeValueList.elementAt(elementIndex - 1)!!
    }

    override fun decodeNotNullMark(): Boolean {
        val result = currentValueNotNull
        currentValueNotNull = true
        return result
    }

    final override val serializersModule: SerializersModule = EmptySerializersModule

    final override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        debugPrint("LogTest", "Start decoding $descriptor")
        var innerCompositeObject: Any?

        if (elementIndex == 0) {
            innerCompositeObject = nestedObject
        } else {
            innerCompositeObject = decodeValueList.elementAt(elementIndex - 1)
        }
        debugPrint("LogTest", "Inner Compostite Objects are $innerCompositeObject")
        debugPrint("LogTest", "descriptor type is ${descriptor.kind}")
        when (descriptor.kind) {
            is StructureKind.CLASS -> {
                debugPrint("LogTest", "innerCompositeObject is $innerCompositeObject ")
                debugPrint("LogTest", "innerCompositeObject class is is ${innerCompositeObject?.javaClass} ") // com.google.firebase.Timestamp
                // Need to address TimeStamp, DocumentID class here, as they can not casted to Map
                val innerMap = (innerCompositeObject as? Map<String, Any> ?: mapOf()).toMutableMap()
                debugPrint("LogTest", "elementIndex is $elementIndex with inner map $innerMap")
                if (elementIndex == 0) {
                    for (propertyName in descriptor.elementNames) {
                        val propertyIndex = descriptor.getElementIndex(propertyName)
                        val annotationsOnProperty = descriptor.getElementAnnotations(propertyIndex)
                        // TODO: Loop through all the properties' annotation list to replace @ServerTimestamp
                        if (annotationsOnProperty.any { it is KDocumentId }) {
                            val propertieType = descriptor.getElementDescriptor(propertyIndex).kind
                            if (propertieType is PrimitiveKind.STRING) { // TODO: Need to handle DocumentReference Type as well
                                innerMap[propertyName] = documentId!!.id
                            } else {
                                throw IllegalArgumentException(
                                    "Field is annotated with @DocumentId but is class $propertieType instead of String."
                                )
                            }
                        }

                        if (annotationsOnProperty.any { it is KServerTimestamp }) {
                            debugPrint("LogTest", "found an annotation of KServerTimestamp")
                            val propertieType = descriptor.getElementDescriptor(propertyIndex).kind
                            val serialName = descriptor.getElementDescriptor(propertyIndex).serialName
                            debugPrint("LogTest", "property type is $propertieType, with serialName is $serialName")
                            if (innerMap[propertyName] == null) {
                                debugPrint("LogTest", ">>>>>>>>> set the server timestamp to be {$documentSnapshot?} ")
                                val realServerTimestamp = documentSnapshot?.getTimestamp(propertyName, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                                val testttt = documentSnapshot?.getTimestamp("time", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                                // TODO: Add a servertimestamp behavior overwrite here
                                //  also need to provide a way to generate path, and use it to replace propertyName
                                debugPrint("LogTest", ">>>>>>>>> set the server timestamp to be {$realServerTimestamp} ")
                                if (realServerTimestamp != null) {
                                    innerMap[propertyName] = mapOf("seconds" to realServerTimestamp.seconds, "nanoseconds" to realServerTimestamp.nanoseconds)
                                }
                            }
                            debugPrint("LogTest", innerMap.toString())
                        }
                    }
                }
                return NestedMapDecoder(innerMap, documentId, documentSnapshot)
            }
            is StructureKind.LIST -> {
                val innerList = innerCompositeObject as? List<Any> ?: listOf()
                return NestedListDecoder(innerList)
            }
            else -> {
                throw Exception(
                    "Incorrect format of nested data provided: <$innerCompositeObject>"
                )
            }
        }
    }
}

class NestedListDecoder(
    override val nestedObject: List<*>,
) : NestDecoder() {
    private val list = nestedObject
    override val decodeValueList = list

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == list.size) return CompositeDecoder.DECODE_DONE
        return elementIndex++
    }
}

class NestedMapDecoder(
    override val nestedObject: Map<*, *>,
    override var documentId: FirestoreDocument?,
    override var documentSnapshot: DocumentSnapshot?
) : NestDecoder() {
    private val map = nestedObject
    override val decodeValueList = ArrayList(map.values)

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        debugPrint("LogTest", ">>>>>>>> This is inside of a nested map decoder, tryint to get index for $descriptor")
        debugPrint("LogTest", ">>>>>>>> Element index now is $elementIndex, map max size is ${map.size}")
        if (elementIndex == map.size) return CompositeDecoder.DECODE_DONE
        val throwOnExtraProperties: Boolean =
            descriptor.annotations.any { it is KThrowOnExtraProperties }
        while (true) {
            if (elementIndex == map.size) return CompositeDecoder.DECODE_DONE
            val decodeElementName = map.keys.elementAt(elementIndex).toString()
            val decodeElementValue = decodeValueList.elementAt(elementIndex)
            val decodeElementIndex = descriptor.getElementIndex(decodeElementName)
            debugPrint("LogTest", ">>>>>>>> decodeElementName, value, and index is: ${listOf(decodeElementName, decodeElementValue, decodeElementIndex)}")
            currentValueNotNull =
                decodeElementValue != null
            elementIndex++
            if (decodeElementIndex != CompositeDecoder.UNKNOWN_NAME) {
                debugPrint("LogTest", "decodeElementIndex for $decodeElementName with value $decodeElementValue is $decodeElementIndex")
                return decodeElementIndex
            }
            if (decodeElementIndex == CompositeDecoder.UNKNOWN_NAME && throwOnExtraProperties) {
                throw IllegalArgumentException(
                    "Can not match $decodeElementName to any properties inside of Object: ${descriptor.serialName}"
                )
            }
        }
    }
}

fun <T> decodeFromNestedMap(map: Map<String, Any?>, deserializer: DeserializationStrategy<T>, firestoreDocument: FirestoreDocument?, docSnapshot: DocumentSnapshot?): T {
    val decoder: Decoder = NestedMapDecoder(map, firestoreDocument, docSnapshot)
    return decoder.decodeSerializableValue(deserializer)
}

inline fun <reified T> decodeFromNestedMap(map: Map<String, Any?>, firestoreDocument: FirestoreDocument?, docSnapshot: DocumentSnapshot?): T =
    decodeFromNestedMap(map, serializer(), firestoreDocument, docSnapshot)

inline fun <reified T> DocumentSnapshot.get(): T? {
    val firestoreDocument = FirestoreDocument(this.id, this.reference)
    // need to get all the filed names that contains @ServerTimeStamp annotations
    val serverTimeStampElements = "time"
    val timeStamp: Timestamp? = this.getTimestamp(serverTimeStampElements, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    val objectMap = this.data // Map<String!, Any!>?
    val realTimestamp = objectMap?.get("time")
//    return objectMap?.let { decodeFromNestedMap<T>(it, firestoreDocument, docSnapshot = this) }
    return objectMap?.let { decodeFromNestedMap<T>(it, null, this) }
}

// @Serializer(forClass = Date::class)
// object DateSerializer : KSerializer<Date> {
//    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DateSerializer", PrimitiveKind.STRING)
//
//    override fun serialize(encoder: Encoder, value: Date) {
//        encoder.encodeString(value.time.toString())
//    }
//
//    override fun deserialize(decoder: Decoder): Date {
//        return Date(decoder.decodeString().toLong())
//    }
// }

@Serializer(forClass = Timestamp::class)
object TimestampSerializer : KSerializer<Timestamp> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("__DateSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Timestamp) {
//        encoder.encodeString(Timestamp.now().toString())
        return encoder.encodeString("1653917099245")
    }

    override fun deserialize(decoder: Decoder): Timestamp {
        // TODO: change to Timestamp(decoder.decodeLong(), )
//        return Timestamp(Date(decoder.decodeString().toLong()))
        println("for some reason need to call decoder.decodestring()")
        val temp = decoder.decodeString() // 一定要用 decoder 的某一个 method
        return Timestamp(Date("0".toLong()))
    }
}

object DateAsLongSerializer : KSerializer<Date> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Date", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Date) = encoder.encodeLong(value.time)
    override fun deserialize(decoder: Decoder): Date = Date(decoder.decodeLong())
}

@Serializer(forClass = Timestamp::class)
object SuperTimestampSerializer : KSerializer<Timestamp> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("__Timestamp__") {
        element<Long>("seconds")
        element<Int>("nanoseconds")
    }

    override fun serialize(encoder: Encoder, value: Timestamp) {
        val nestMapEncoder = encoder as NestedMapEncoder
        nestMapEncoder.encodeTimestamp(value)
    }

    override fun deserialize(decoder: Decoder): Timestamp {
        debugPrint("LogTest", "================ start decoding servertimestamp now ================")
        val mydecoder = decoder as NestDecoder
        return mydecoder.decodeTimestamp()
    }
}

@Serializable
// data class DateWrapper(var date: Date? = null, var text: String = "123", var timestamp: Timestamp? = null)
data class DateWrapper(@Serializable(with = SuperTimestampSerializer::class) var timestamp: Timestamp? = null, val name: String)

fun main() {
//    val myData = DateWrapper(timestamp = Timestamp(100L, 200), name = "Mayson hahahahahah")
    val myData = DateWrapper(timestamp = null, name = "Mayson hahahahahah")
    var nestedMap = encodeToMap(myData)
    println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
    println(nestedMap)
    nestedMap["timestamp"] = mapOf("seconds" to 99L, "nanoseconds" to 88)
    val myObject = decodeFromNestedMap<DateWrapper>(nestedMap, null, null)
    println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
    println(myObject)
}
