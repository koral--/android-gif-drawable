package pl.droidsonroids.gif

import android.graphics.drawable.Drawable
import org.assertj.core.api.Java6Assertions
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import pl.droidsonroids.gif.MultiCallback.CallbackWeakReference

@RunWith(MockitoJUnitRunner::class)
class CallbackWeakReferenceTest {
    @Mock
    var callback: Drawable.Callback? = null

    @Mock
    var anotherCallback: Drawable.Callback? = null
    @Test
    @Throws(Exception::class)
    fun testEquals() {
        val reference = CallbackWeakReference(callback)
        val anotherReference = CallbackWeakReference(callback)
        Java6Assertions.assertThat(reference).isEqualTo(anotherReference)
    }

    @Test
    @Throws(Exception::class)
    fun testNotEqualReferences() {
        val reference = CallbackWeakReference(callback)
        val anotherReference = CallbackWeakReference(anotherCallback)
        Java6Assertions.assertThat(reference).isNotEqualTo(anotherReference)
    }

    @Test
    @Throws(Exception::class)
    fun testNotEqualDifferentObjects() {
        val reference = CallbackWeakReference(callback)
        Java6Assertions.assertThat(reference).isNotEqualTo(null)
        Java6Assertions.assertThat(reference).isNotEqualTo(callback)
    }

    @Test
    @Throws(Exception::class)
    fun testHashCode() {
        val reference = CallbackWeakReference(callback)
        val anotherReference = CallbackWeakReference(callback)
        Java6Assertions.assertThat(reference.hashCode()).isEqualTo(anotherReference.hashCode())
    }

    @Test
    @Throws(Exception::class)
    fun testHashCodeNull() {
        val reference = CallbackWeakReference(callback)
        val anotherReference = CallbackWeakReference(null)
        Java6Assertions.assertThat(reference.hashCode()).isNotEqualTo(anotherReference.hashCode())
    }
}