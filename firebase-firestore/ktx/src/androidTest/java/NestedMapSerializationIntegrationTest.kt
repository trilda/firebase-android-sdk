import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ktx.annotations.KDocumentId
import com.google.firebase.firestore.ktx.annotations.KThrowOnExtraProperties
import junit.framework.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.Serializable as Serializable

class NestedMapSerializationIntegrationTest {

    @Serializable
    @KThrowOnExtraProperties
    data class Project(val name: String? = null, val owner: String? = null)

    @Serializable
    data class Owner(val name: String? = null)

    @Serializable
    data class ObjectInsideOfObject(val name: String? = null, val owner: Owner? = null)

    @Serializable
    data class ListOfObjectsInsideOfObject(
        val name: String? = null,
        val listOfOwner: List<Owner?>? = null
    )

    @Serializable
    data class KProjectWithDocId(val name: String? = null, @KDocumentId val owner: String? = null)

    @Serializable
    data class KProjectWithPrimitiveExtraProperties(val name: String? = null, val owner: String? = null, val extraBoolean: Boolean? = true)

    @Serializable
    data class KProjectWithCustomObjectExtraProperties(val name: String? = null, val owner: String? = null, val extraOwner: Owner? = null)

    companion object {
        // Emulator must have local persistence storage enabled
        var settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()

        val testFirestore: FirebaseFirestore by lazy {
            FirebaseFirestore.getInstance().apply {
                this.useEmulator("10.0.2.2", 8080)
                this.firestoreSettings = settings
            }
        }
    }

    @Test
    fun testForTest() {
//        val docRefKotlin = testFirestore.collection("kotlin").document("doc")
//        val docRefPOJO = testFirestore.collection("pojo").document("doc")
        val projectList = listOf(
            Project(),
            Project("x"),
            Project("x", "y"),
            Project(null, null)
        )

        for (project in projectList) {
//            docRefKotlin.serialSet<Project>(project)
//            docRefPOJO.set(project)
//            val expected = waitFor(docRefPOJO.get()).data
//            val actual = waitFor(docRefKotlin.get()).data
            assertTrue(1 == 1)
        }
    }
}

/*
@Test
fun testSerializationSetMethodSameAsPOJOSet() {
val docRefKotlin = testCollection("kotlin_set").document("kotlin")
val docRefPOJO = testCollection("pojo_set").document("pojo")
val projectList = listOf(
Project(),
Project("x"),
Project("x", "y"),
Project(null, null)
)

for (project in projectList) {
docRefKotlin.serialSet<Project>(project)
docRefPOJO.set(project)
val expected = waitFor(docRefPOJO.get()).data
val actual = waitFor(docRefKotlin.get()).data
assertTrue(expected == actual)
}
}
}
*/

fun main() {
}
