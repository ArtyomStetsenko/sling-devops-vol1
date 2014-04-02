package org.apache.sling.devops.minion;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.devops.Instance;
import org.apache.sling.discovery.DiscoveryService;
import org.apache.sling.discovery.InstanceDescription;
import org.apache.sling.hc.api.execution.HealthCheckExecutor;
import org.apache.sling.installer.provider.jcr.impl.JcrInstaller;
import org.apache.sling.launchpad.api.StartupListener;
import org.apache.sling.launchpad.api.StartupMode;
import org.apache.sling.resourceresolver.impl.ResourceResolverFactoryActivator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate=true)
@Service
public class Minion implements StartupListener {

	private static final Logger logger = LoggerFactory.getLogger(Minion.class);

	public static final String CONFIG = System.getProperty("sling.devops.config"); // TODO
	public static final String CONFIG_PATH = "/sling-cfg";

	public static final String PID_JCR_RESOURCE_RESOLVER_FACTORY = "org.apache.sling.jcr.resource.internal.JcrResourceResolverFactoryImpl";
	public static final String PID_JCR_INSTALLER = "org.apache.sling.installer.provider.jcr.impl.JcrInstaller";

	public static final String PATH_JCR_RESOURCE_RESOLVER_FACTORY = ResourceResolverFactoryActivator.PROP_PATH;
	public static final String PATH_JCR_INSTALLER = JcrInstaller.PROP_SEARCH_PATH;

	public static final String[] HC_TAGS = new String[]{ "devops", "minion" };

	@Reference
	private HealthCheckExecutor healthCheckExecutor;

	@Reference
	private DiscoveryService discoveryService;

	private InstanceAnnouncer instanceAnnouncer;
	private HealthCheckMonitor healthCheckMonitor;

	@Activate
	public void onActivate() throws IOException {
		this.instanceAnnouncer = new ZooKeeperInstanceAnnouncer();
		this.healthCheckMonitor = new HealthCheckMonitor(this.healthCheckExecutor, HC_TAGS);
		this.healthCheckMonitor.addListener(new HealthCheckMonitor.HealthCheckListener() {
			@Override
			public void onOk() {
				logger.info("Health checks succeeded, announcing instance...");
				final InstanceDescription instanceDescription = Minion.this.discoveryService.getTopology().getLocalInstance();
				Minion.this.instanceAnnouncer.announce(new Instance(
						instanceDescription.getSlingId(),
						CONFIG,
						new HashSet<>(Arrays.asList(instanceDescription.getProperty(InstanceDescription.PROPERTY_ENDPOINTS).split(",")))
						));
			}
			@Override
			public void onFail() {}
		});
	}

	@Deactivate
	public void onDeactivate() throws Exception {
		this.instanceAnnouncer.close();
		this.healthCheckMonitor.close();
	}

	@Override
	public void inform(StartupMode mode, boolean finished) {
		if (finished) this.startupFinished(mode);
	}

	@Override
	public void startupFinished(StartupMode mode) {
		logger.info("Startup finished, running health checks.");
		this.healthCheckMonitor.start();
	}

	@Override
	public void startupProgress(float ratio) {
	}
}
