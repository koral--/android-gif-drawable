package pl.droidsonroids.gif;

import net.jodah.concurrentunit.Waiter;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ConditionVariableTest {

	private static final int TEST_TIMEOUT = 500;
	private static final int BLOCK_DURATION = 200;

	@Rule
	public Timeout timeout = new Timeout(TEST_TIMEOUT, TimeUnit.MILLISECONDS);

	private ConditionVariable conditionVariable;
	private Waiter waiter;

	@Before
	public void setUp() {
		conditionVariable = new ConditionVariable();
		waiter = new Waiter();
	}

	@Test
	public void testBlock() throws Exception {
		blockAndWait();
	}

	@Test
	public void testOpen() throws Exception {
		new Thread() {
			@Override
			public void run() {
				conditionVariable.open();
				waiter.resume();
			}
		}.start();
		conditionVariable.block();
		waiter.await();
	}

	@Test
	public void testInitiallyOpened() throws Exception {
		conditionVariable.set(true);
		conditionVariable.block();
	}

	@Test
	public void testInitiallyClosed() throws Exception {
		conditionVariable.set(false);
		blockAndWait();
	}

	@Test
	public void testClose() throws Exception {
		conditionVariable.close();
		blockAndWait();
	}

	private void blockAndWait() throws InterruptedException, TimeoutException {
		final Thread thread = new Thread() {
			@Override
			public void run() {
				try {
					waiter.resume();
					conditionVariable.block();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					waiter.rethrow(e);
				}
				waiter.fail("ConditionVariable not blocked");
			}
		};
		thread.start();
		thread.join(BLOCK_DURATION);
		waiter.await();
	}
}