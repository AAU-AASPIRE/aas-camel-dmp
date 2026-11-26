/* Copyright (C) 2026 the aas-camel-dmp Authors
 * SPDX-License-Identifier: MIT */
package dk.aau.smartproductionlab.dmp.opcua.component;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.milo.client.MiloClientComponent;
import org.apache.camel.component.milo.client.MiloClientConfiguration;

/**
 * {@code dmp-opcua:}: camel-milo's client with a Method producer that
 * answers with the Method's result (see {@link DmpOpcUaEndpoint}). Endpoints
 * are configured exactly as {@code milo-client:} and share this component's
 * connection manager; reads and writes are upstream's, subscriptions this
 * module's ({@link DmpOpcUaSubscriptionConsumer}): a variable, or with the DMP
 * options {@code eventFields} and {@code eventType} the events of a notifier
 * ({@link EventSelection}). The DMP option {@code browsePath} names the node by
 * an AID browse path instead of {@code node} ({@link OpcUaBrowsePaths}). The
 * upstream generated configurers are registered under this scheme
 * ({@code META-INF/services/org/apache/camel/configurer/dmp-opcua-*}), so
 * every {@code milo-client:} option binds the same way.
 */
@org.apache.camel.spi.annotations.Component("dmp-opcua")
public class DmpOpcUaComponent extends MiloClientComponent {
	@Override
	protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
		MiloClientConfiguration configuration = new MiloClientConfiguration(getConfiguration());
		configuration.setEndpointUri(remaining);
		DmpOpcUaEndpoint endpoint = new DmpOpcUaEndpoint(uri, this, configuration.getEndpointUri(),
				getMiloClientConnectionManager());
		endpoint.setConfiguration(configuration);
		// DMP options, unknown to the upstream configurer: an event subscription's select and where clause.
		String browsePath = getAndRemoveParameter(parameters, "browsePath", String.class);
		if (browsePath != null) endpoint.setBrowsePath(browsePath);
		String eventFields = getAndRemoveParameter(parameters, "eventFields", String.class);
		String eventType = getAndRemoveParameter(parameters, "eventType", String.class);
		if (eventFields != null) endpoint.setEventSelection(EventSelection.parse(eventFields, eventType));
		else if (eventType != null) throw new IllegalArgumentException("eventType needs eventFields");
		setProperties(endpoint, parameters);
		return endpoint;
	}
}
