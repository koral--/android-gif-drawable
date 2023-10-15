package pl.droidsonroids.gif

import android.graphics.drawable.Drawable
import org.assertj.core.api.Java6Assertions.assertThat
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
    fun testEquals() {
        val reference = CallbackWeakReference(callback)

        val anotherReference = CallbackWeakReference(callback)

        assertThat(reference).isEqualTo(anotherReference)
    }

    @Test
    fun testNotEqualReferences() {
        val reference = CallbackWeakReference(callback)

        val anotherReference = CallbackWeakReference(anotherCallback)

        assertThat(reference).isNotEqualTo(anotherReference)
    }

    @Test
    fun testNotEqualDifferentObjects() {
        val reference = CallbackWeakReference(callback)

        assertThat(reference).isNotEqualTo(null)
        assertThat(reference).isNotEqualTo(callback)
    }

    @Test
    fun testHashCode() {
        val reference = CallbackWeakReference(callback)

        val anotherReference = CallbackWeakReference(callback)

        assertThat(reference.hashCode()).isEqualTo(anotherReference.hashCode())
    }

    @Test
    fun testHashCodeNull() {
        val reference = CallbackWeakReference(callback)

        val anotherReference = CallbackWeakReference(null)

        assertThat(reference.hashCode()).isNotEqualTo(anotherReference.hashCode())
    }
}