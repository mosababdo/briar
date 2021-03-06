package org.briarproject.bramble.plugin.tor;

import android.content.Context;
import android.support.test.runner.AndroidJUnit4;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.plugin.BackoffFactory;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.api.system.ResourceProvider;
import org.briarproject.bramble.test.BrambleAndroidIntegrationTestComponent;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.DaggerBrambleAndroidIntegrationTestComponent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.net.SocketFactory;

import static android.support.test.InstrumentationRegistry.getTargetContext;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


@RunWith(AndroidJUnit4.class)
public class BridgeTest extends BrambleTestCase {

	private final static long TIMEOUT = SECONDS.toMillis(23);

	private final static Logger LOG =
			Logger.getLogger(BridgeTest.class.getName());

	@Inject
	NetworkManager networkManager;
	@Inject
	ResourceProvider resourceProvider;
	@Inject
	CircumventionProvider circumventionProvider;
	@Inject
	EventBus eventBus;
	@Inject
	BackoffFactory backoffFactory;
	@Inject
	Clock clock;

	private final Context appContext =
			getTargetContext().getApplicationContext();

	private List<String> bridges;
	private AndroidTorPluginFactory factory;

	private volatile String currentBridge = null;

	@Before
	public void setUp() {
		BrambleAndroidIntegrationTestComponent component =
				DaggerBrambleAndroidIntegrationTestComponent.builder().build();
		component.inject(this);

		Executor ioExecutor = Executors.newCachedThreadPool();
		ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);
		LocationUtils locationUtils = () -> "US";
		SocketFactory torSocketFactory = SocketFactory.getDefault();

		bridges = circumventionProvider.getBridges();
		CircumventionProvider bridgeProvider = new CircumventionProvider() {
			@Override
			public boolean isTorProbablyBlocked(String countryCode) {
				return true;
			}

			@Override
			public boolean doBridgesWork(String countryCode) {
				return true;
			}

			@Override
			public List<String> getBridges() {
				return singletonList(currentBridge);
			}
		};
		factory = new AndroidTorPluginFactory(ioExecutor, scheduler, appContext,
				networkManager, locationUtils, eventBus, torSocketFactory,
				backoffFactory, resourceProvider, bridgeProvider, clock);
	}

	@Test
	public void testBridges() throws Exception {
		assertTrue(bridges.size() > 0);

		for (String bridge : bridges) testBridge(bridge);
	}

	private void testBridge(String bridge) throws Exception {
		DuplexPlugin duplexPlugin =
				factory.createPlugin(new TorPluginCallBack());
		assertNotNull(duplexPlugin);
		AndroidTorPlugin plugin = (AndroidTorPlugin) duplexPlugin;

		currentBridge = bridge;
		LOG.warning("Testing " + bridge);
		try {
			plugin.start();
			long start = clock.currentTimeMillis();
			while (clock.currentTimeMillis() - start < TIMEOUT) {
				if (plugin.isRunning()) return;
				clock.sleep(500);
			}
			if (!plugin.isRunning()) {
				fail("Could not connect to Tor within timeout.");
			}
		} finally {
			plugin.stop();
		}
	}

}
