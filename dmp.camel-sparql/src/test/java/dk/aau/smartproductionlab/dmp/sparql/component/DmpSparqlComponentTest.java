/* Copyright (C) 2026 the aas-camel-dmp Authors
 * SPDX-License-Identifier: MIT */
package dk.aau.smartproductionlab.dmp.sparql.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import dk.aau.smartproductionlab.dmp.core.configuration.route.core.ConnectorInvocationException;
import dk.aau.smartproductionlab.dmp.core.util.JsonPointer;
import dk.aau.smartproductionlab.dmp.sparql.StateStoreSettings;
import dk.aau.smartproductionlab.dmp.sparql.StateTerms.Cardinality;
import dk.aau.smartproductionlab.dmp.sparql.StateTerms.ValueType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class DmpSparqlComponentTest {
	private record Update(String contentType, String body) { }

	private static final String GRAPH = "https://smartproductionlab.aau.dk/state";
	private static final String LAB = "https://smartproductionlab.aau.dk/semantics/";
	/** A place: an element of a submodel, whose idShortPath may hold brackets. */
	private static final String PLACE = "https://smartproductionlab.aau.dk/aas/FillingModuleAAS/submodels/Variables#Measurements.Coordinates.TransferPoint";
	private static final String URI_ = DmpSparqlComponent.uri(GRAPH, PLACE, LAB + "AbsoluteX", ValueType.NUMBER,
			Cardinality.SINGLE, "aimc(1):TransferPoint");

	private final List<Update> updates = new CopyOnWriteArrayList<>();
	private volatile int status = 204;
	private HttpServer store;
	private DefaultCamelContext context;
	private ProducerTemplate template;
	private DmpSparqlComponent component;

	@BeforeEach
	void start() throws Exception {
		store = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		store.createContext("/update", exchange -> {
			updates.add(new Update(exchange.getRequestHeaders().getFirst("Content-Type"),
					new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
			byte[] answer = status / 100 == 2 ? new byte[0] : "Parse error".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, answer.length == 0 ? -1 : answer.length);
			if (answer.length > 0) exchange.getResponseBody().write(answer);
			exchange.close();
		});
		store.start();
		context = new DefaultCamelContext();
		context.start();
		component = context.getComponent(DmpSparqlComponent.SCHEME, DmpSparqlComponent.class);
		component.configure(new StateStoreSettings(URI.create("http://127.0.0.1:" + store.getAddress().getPort() + "/update")));
		template = context.createProducerTemplate();
	}

	@AfterEach
	void stop() {
		context.stop();
		store.stop(0);
	}

	@Test
	void aWriteIsOneSparqlUpdateWithTheValueTypedAndTheSourceRecorded() {
		template.sendBody(URI_, 240.0);
		assertEquals(1, updates.size());
		Update update = updates.get(0);
		assertTrue(update.contentType().startsWith("application/sparql-update"), update.contentType());
		assertTrue(update.body().contains("<" + PLACE + "> <" + LAB + "AbsoluteX> \"240.0\"^^"), update.body());
		assertTrue(update.body().contains("\"aimc(1):TransferPoint\""), "the source survives the URI: " + update.body());
		assertTrue(update.body().contains("\"" + component.session() + "\""), update.body());
	}

	@Test
	void anAbsentValueClearsTheVariable() {
		template.sendBody(URI_, JsonPointer.JSON_NULL);
		template.sendBody(URI_, null);
		assertEquals(2, updates.size());
		for (Update update : updates) assertTrue(!update.body().contains("AbsoluteX> \""), update.body());
	}

	@Test
	void aValueOfTheWrongTypeIsRejectedBeforeAnythingIsSent() {
		var failure = assertThrows(CamelExecutionException.class, () -> template.sendBody(URI_, "240"));
		var cause = assertInstanceOf(ConnectorInvocationException.class, failure.getCause());
		assertEquals("STATE_VALUE_INVALID", cause.getConnectorCode());
		assertEquals(List.of(), updates);
	}

	@Test
	void aRejectedUpdateFailsTheWriteAndAnOverloadedStoreIsATransportFailure() {
		status = 400;
		var rejected = assertInstanceOf(ConnectorInvocationException.class,
				assertThrows(CamelExecutionException.class, () -> template.sendBody(URI_, 1.0)).getCause());
		assertEquals("STATE_STORE_REJECTED", rejected.getConnectorCode());
		assertEquals(ConnectorInvocationException.Category.REMOTE, rejected.getCategory());
		status = 503;
		var failed = assertInstanceOf(ConnectorInvocationException.class,
				assertThrows(CamelExecutionException.class, () -> template.sendBody(URI_, 1.0)).getCause());
		assertEquals(ConnectorInvocationException.Category.TRANSPORT, failed.getCategory());
	}

	@Test
	void withoutAConfiguredStoreTheWriteFails() {
		component.configure(null);
		var failure = assertInstanceOf(ConnectorInvocationException.class,
				assertThrows(CamelExecutionException.class, () -> template.sendBody(URI_, 1.0)).getCause());
		assertEquals("STATE_STORE_NOT_CONFIGURED", failure.getConnectorCode());
	}

	@Test
	void anEndpointUriRoundTripsItsValues() throws Exception {
		var endpoint = context.getEndpoint(URI_, DmpSparqlEndpoint.class);
		assertEquals(PLACE, endpoint.update().subject());
		assertEquals(LAB + "AbsoluteX", endpoint.update().property());
		assertEquals("aimc(1):TransferPoint", endpoint.update().source());
		assertEquals(ValueType.NUMBER, endpoint.update().valueType());
	}
}
