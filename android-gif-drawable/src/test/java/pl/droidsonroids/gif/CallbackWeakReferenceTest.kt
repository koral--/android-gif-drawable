package pl.droidsonroids.gif;

import android.graphics.drawable.Drawable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import pl.droidsonroids.gif.MultiCallback.CallbackWeakReference;

import static org.assertj.core.api.Java6Assertions.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class CallbackWeakReferenceTest {

	@Mock Drawable.Callback callback;
	@Mock Drawable.Callback anotherCallback;

	@Test
	public void testEquals() throws Exception {
		final CallbackWeakReference reference = new CallbackWeakReference(callback);
		final CallbackWeakReference anotherReference = new CallbackWeakReference(callback);
		assertThat(reference).isEqualTo(anotherReference);
	}

	@Test
	public void testNotEqualReferences() throws Exception {
		final CallbackWeakReference reference = new CallbackWeakReference(callback);
		final CallbackWeakReference anotherReference = new CallbackWeakReference(anotherCallback);
		assertThat(reference).isNotEqualTo(anotherReference);
	}

	@Test
	public void testNotEqualDifferentObjects() throws Exception {
		final CallbackWeakReference reference = new CallbackWeakReference(callback);
		assertThat(reference).isNotEqualTo(null);
		assertThat(reference).isNotEqualTo(callback);
	}

	@Test
	public void testHashCode() throws Exception {
		final CallbackWeakReference reference = new CallbackWeakReference(callback);
		final CallbackWeakReference anotherReference = new CallbackWeakReference(callback);
		assertThat(reference.hashCode()).isEqualTo(anotherReference.hashCode());
	}

	@Test
	public void testHashCodeNull() throws Exception {
		final CallbackWeakReference reference = new CallbackWeakReference(callback);
		final CallbackWeakReference anotherReference = new CallbackWeakReference(null);
		assertThat(reference.hashCode()).isNotEqualTo(anotherReference.hashCode());
	}
}