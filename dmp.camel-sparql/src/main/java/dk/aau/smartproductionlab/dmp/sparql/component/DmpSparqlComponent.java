/* Copyright (C) 2026 the aas-camel-dmp Authors
 * SPDX-License-Identifier: MIT */
package dk.aau.smartproductionlab.dmp.sparql.component;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.Endpoint;
import org.apache.camel.support.DefaultComponent;
import dk.aau.smartproductionlab.dmp.core.configuration.route.core.ConnectorInvocationException;
import dk.aau.smartproductionlab.dmp.sparql.StateStoreSettings;
import dk.aau.smartproductionlab.dmp.sparql.StateTerms.Cardinality;
import dk.aau.smartproductionlab.dmp.sparql.StateTerms.ValueType;
import dk.aau.smartproductionlab.dmp.sparql.StateUpdate;

/**
 * {@code dmp-sparql:state?graph=…&subject=…&property=…&valueType=…&cardinality=…&source=…}: one state variable in the
 * configured state store ({@link #configure}), written as a sink. Every endpoint shares the one store and the DMP
 * session this component started, which each write records as its provenance (plan 4.1, 4.9).
 */
public final class DmpSparqlComponent extends DefaultComponent {
	public static final String SCHEME = "dmp-sparql";
	private static final String KIND = "state";

	private volatile StateStoreSettings settings;
	private volatile HttpClient client;
	private final String session = UUID.randomUUID().toString();

	/** The endpoint URI of one state variable. Values are passed RAW so Camel leaves them unchanged. */
	public static String uri(String graph, String subject, String property, ValueType valueType, Cardinality cardinality,
			String source) {
		return SCHEME + ':' + KIND + "?graph=RAW(" + encode(graph) + ")&subject=RAW(" + encode(subject) + ")&property=RAW("
				+ encode(property) + ")&valueType=" + valueType.vocabularyName() + "&cardinality=" + cardinality.vocabularyName()
				+ "&source=RAW(" + encode(source) + ")";
	}

	/** URI values are percent-encoded inside RAW(…), so an IRI's parentheses cannot end it. */
	private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

	private static String decode(String value) { return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
	}

	/** Sets the store every endpoint writes to. */
	public synchronized void configure(StateStoreSettings settings) {
		this.settings = settings;
		this.client = settings == null ? null : HttpClient.newBuilder().connectTimeout(settings.timeout()).build();
	}

	/** This component's DMP session, recorded with every write. */
	public String session() { return session; }

	/** Sends one update and waits for the store to accept it. */
	void post(String update) {
		StateStoreSettings current = settings;
		HttpClient http = client;
		if (current == null || http == null)
			throw new ConnectorInvocationException(ConnectorInvocationException.Category.CONFIGURATION, "STATE_STORE_NOT_CONFIGURED",
					"no state store is configured (" + StateStoreSettings.UPDATE_URL + ")", Map.of());
		HttpRequest request = HttpRequest.newBuilder(current.update()).timeout(current.timeout())
				.header("Content-Type", "application/sparql-update; charset=utf-8")
				.POST(HttpRequest.BodyPublishers.ofString(update, StandardCharsets.UTF_8)).build();
		HttpResponse<String> response;
		try {
			response = http.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException unreachable) {
			throw new ConnectorInvocationException(ConnectorInvocationException.Category.TRANSPORT, "STATE_STORE_UNREACHABLE",
					"the state store could not be reached", Map.of("cause", String.valueOf(unreachable.getMessage())));
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new ConnectorInvocationException(ConnectorInvocationException.Category.TRANSPORT, "STATE_STORE_INTERRUPTED",
					"the state store write was interrupted", Map.of());
		}
		int status = response.statusCode();
		if (status / 100 == 2) return;
		boolean retriable = status >= 500 || status == 408 || status == 429;
		throw new ConnectorInvocationException(retriable ? ConnectorInvocationException.Category.TRANSPORT
				: ConnectorInvocationException.Category.REMOTE, retriable ? "STATE_STORE_FAILED" : "STATE_STORE_REJECTED",
				"the state store answered " + status, Map.of("status", status, "cause", truncate(response.body())));
	}

	@Override
	protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
		if (!KIND.equals(remaining)) throw new IllegalArgumentException("dmp-sparql endpoints are dmp-sparql:state, not " + remaining);
		String graph = decode(required(parameters, "graph"));
		String subject = decode(required(parameters, "subject"));
		String property = decode(required(parameters, "property"));
		ValueType valueType = ValueType.of(required(parameters, "valueType"));
		Cardinality cardinality = Cardinality.of(required(parameters, "cardinality"));
		String source = decode(required(parameters, "source"));
		DmpSparqlEndpoint endpoint = new DmpSparqlEndpoint(uri, this,
				new StateUpdate(graph, subject, property, valueType, cardinality, source));
		setProperties(endpoint, parameters);
		return endpoint;
	}

	private String required(Map<String, Object> parameters, String name) {
		String value = getAndRemoveParameter(parameters, name, String.class);
		if (value == null || value.isBlank()) throw new IllegalArgumentException("dmp-sparql endpoint requires " + name);
		return value;
	}

	private static String truncate(String body) {
		if (body == null) return "";
		return body.length() <= 500 ? body : body.substring(0, 500) + "…";
	}
}
