/* Copyright (C) 2026 the aas-camel-dmp Authors
 * SPDX-License-Identifier: MIT */
package dk.aau.smartproductionlab.dmp.sparql.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import dk.aau.smartproductionlab.dmp.sparql.StateStoreSettings;
import dk.aau.smartproductionlab.dmp.sparql.StateTerms.Cardinality;
import dk.aau.smartproductionlab.dmp.sparql.StateTerms.ValueType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Against a real state store ({@code DMP_STATE_STORE_TEST_URL}, its origin, e.g. an Oxigraph at
 * {@code http://localhost:7878}): a written variable reads back as a typed triple, a second write replaces it, a
 * clear removes it, and its provenance record is kept. Run with {@code -Pintegration}.
 */
@Tag("integration")
class OxigraphStateStoreIT {
	private static final String GRAPH = "https://smartproductionlab.aau.dk/state";
	private static final String LAB = "https://smartproductionlab.aau.dk/semantics/";
	private static final String STATION = "urn:test:station:" + System.nanoTime();

	@Test
	void aVariableIsWrittenReplacedAndClearedWithItsProvenance() throws Exception {
		String origin = System.getenv("DMP_STATE_STORE_TEST_URL");
		assumeTrue(origin != null && !origin.isBlank(), "DMP_STATE_STORE_TEST_URL is not set");
		try (var context = new DefaultCamelContext()) {
			context.start();
			context.getComponent(DmpSparqlComponent.SCHEME, DmpSparqlComponent.class)
					.configure(new StateStoreSettings(URI.create(origin + "/update")));
			ProducerTemplate template = context.createProducerTemplate();
			String occupied = DmpSparqlComponent.uri(GRAPH, STATION, LAB + "OccupiedBy", ValueType.REFERENCE, Cardinality.SINGLE, "it");
			String x = DmpSparqlComponent.uri(GRAPH, STATION, LAB + "AbsoluteX", ValueType.NUMBER, Cardinality.SINGLE, "it");

			template.sendBody(occupied, "urn:uuid:aaaa");
			template.sendBody(x, 240.0);
			assertEquals(List.of("urn:uuid:aaaa"), values(origin, "OccupiedBy"));
			assertTrue(ask(origin, "ASK { GRAPH <" + GRAPH + "> { <" + STATION + "> <" + LAB + "AbsoluteX> ?x FILTER(?x = 240) } }"),
					"a number is a number to a query");

			template.sendBody(occupied, "urn:uuid:bbbb");
			assertEquals(List.of("urn:uuid:bbbb"), values(origin, "OccupiedBy"), "a write replaces the value");

			template.sendBody(occupied, null);
			assertEquals(List.of(), values(origin, "OccupiedBy"), "no value clears it");
			assertTrue(ask(origin, "ASK { GRAPH <" + GRAPH + "/provenance> { ?record <https://smartproductionlab.aau.dk/dmp/state#subject> <"
					+ STATION + "> ; <https://smartproductionlab.aau.dk/dmp/state#property> <" + LAB + "OccupiedBy> } }"),
					"the provenance of the clear is kept");
		}
	}

	private static List<String> values(String origin, String property) throws Exception {
		String body = query(origin, "SELECT ?v WHERE { GRAPH <" + GRAPH + "> { <" + STATION + "> <" + LAB + property + "> ?v } }",
				"text/csv");
		return body.lines().skip(1).filter(line -> !line.isBlank()).map(String::strip).toList();
	}

	private static boolean ask(String origin, String sparql) throws Exception {
		return query(origin, sparql, "application/sparql-results+json").contains("true");
	}

	private static String query(String origin, String sparql, String accept) throws Exception {
		var request = HttpRequest.newBuilder(URI.create(origin + "/query?query=" + URLEncoder.encode(sparql, StandardCharsets.UTF_8)))
				.header("Accept", accept).GET().build();
		HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, response.statusCode(), response.body());
		return response.body();
	}
}
