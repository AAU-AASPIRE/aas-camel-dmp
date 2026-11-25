/* Copyright (C) 2026 the aas-camel-dmp Authors
 * SPDX-License-Identifier: MIT */
package dk.aau.smartproductionlab.dmp.sparql.component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;
import dk.aau.smartproductionlab.dmp.core.configuration.route.core.ConnectorInvocationException;
import dk.aau.smartproductionlab.dmp.core.util.JsonPointer;
import dk.aau.smartproductionlab.dmp.sparql.StateUpdate;

/**
 * One state variable. Its producer writes the sink's body as the variable's values: {@code null} clears it, a
 * value of the wrong type or shape is rejected before anything is sent. It has no consumer: mappings write the store,
 * the planner and the behaviour tree read it.
 */
public final class DmpSparqlEndpoint extends DefaultEndpoint {
	private final StateUpdate update;

	DmpSparqlEndpoint(String uri, DmpSparqlComponent component, StateUpdate update) {
		super(uri, component);
		this.update = update;
	}

	@Override public DmpSparqlComponent getComponent() { return (DmpSparqlComponent) super.getComponent(); }

	/** What this endpoint writes (exposed for tests). */
	public StateUpdate update() { return update; }

	/** The update a body becomes at {@code time} (exposed for tests). */
	String sparql(Object body, Instant time) {
		Object value = body == JsonPointer.JSON_NULL ? null : body;
		List<Object> values;
		try {
			values = update.values(value);
			return update.sparql(values, getComponent().session(), time);
		} catch (IllegalArgumentException invalid) {
			throw new ConnectorInvocationException(ConnectorInvocationException.Category.CONFIGURATION, "STATE_VALUE_INVALID",
					invalid.getMessage(), Map.of("subject", update.subject(), "property", update.property()));
		}
	}

	@Override
	public Producer createProducer() {
		return new DefaultProducer(this) {
			@Override
			public void process(Exchange exchange) {
				getComponent().post(sparql(exchange.getMessage().getBody(), Instant.now()));
			}
		};
	}

	@Override
	public Consumer createConsumer(Processor processor) {
		throw new UnsupportedOperationException("the state store is written by mappings, not read: dmp-sparql has no consumer");
	}
}
