package de.qabel.desktop.daemon.sync;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import static org.junit.Assert.fail;

public abstract class AbstractSyncTest {
	protected Path tmpDir;

	@Before
	public void setUp() {
		try {
			tmpDir = Files.createTempDirectory(getClass().getSimpleName());
		} catch (Exception e) {
			fail("failed to create tmp dir: " + e.getMessage());
		}
	}

	@After
	public void tearDown() throws InterruptedException {
		try {
			FileUtils.deleteDirectory(tmpDir.toFile());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected static void waitUntil(Callable<Boolean> evaluate) {
		long timeout = 1000L;
		waitUntil(evaluate, timeout);
	}

	protected static void waitUntil(Callable<Boolean> evaluate, long timeout) {
		try {
			long startTime = System.currentTimeMillis();
			while (!evaluate.call()) {
				Thread.yield();
				if (System.currentTimeMillis() - timeout > startTime) {
					fail("wait timeout");
				}
			}
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}
}
