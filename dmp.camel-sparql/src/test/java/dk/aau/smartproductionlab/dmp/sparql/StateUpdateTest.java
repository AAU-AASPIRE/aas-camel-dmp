/* Copyright (C) 2026 the aas-camel-dmp Authors
 * SPDX-License-Identifier: MIT */
package dk.aau.smartproductionlab.dmp.sparql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import dk.aau.smartproductionlab.dmp.sparql.StateTerms.Cardinality;
import dk.aau.smartproductionlab.dmp.sparql.StateTerms.ValueType;
import org.junit.jupiter.api.Test;

class StateUpdateTest {
	private static final String GRAPH = "https://smartproductionlab.aau.dk/state";
	private static final String STATION = "https://smartproductionlab.aau.dk/aas/imaDispensing";
	private static final String LAB = "https://smartproductionlab.aau.dk/semantics/";
	private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

	private static StateUpdate occupiedBy() {
		return new StateUpdate(GRAPH, STATION, LAB + "OccupiedBy", ValueType.REFERENCE, Cardinality.SINGLE, "aimc:StationState");
	}

	@Test
	void aValueReplacesTheOldOneAndItsProvenanceInOneRequest() {
		String sparql = occupiedBy().sparql(List.of("urn:uuid:7f3a9c2e"), "session-1", NOW);
		assertTrue(sparql.startsWith("DELETE WHERE { GRAPH <" + GRAPH + "> { <" + STATION + "> <" + LAB + "OccupiedBy> ?value } };"), sparql);
		assertTrue(sparql.contains("GRAPH <" + GRAPH + "> { <" + STATION + "> <" + LAB + "OccupiedBy> <urn:uuid:7f3a9c2e> }"), sparql);
		assertTrue(sparql.contains("GRAPH <" + GRAPH + "/provenance> { <" + occupiedBy().record() + ">"), sparql);
		assertTrue(sparql.contains("\"session-1\"") && sparql.contains("\"2026-11-24T12:00:00Z\"^^<"), sparql);
		assertTrue(sparql.contains("\"aimc:StationState\""), "the mapping is the source: " + sparql);
	}

	@Test
	void noValueClearsTheVariableButKeepsItsProvenance() {
		StateUpdate update = occupiedBy();
		assertEquals(List.of(), update.values(null));
		String sparql = update.sparql(List.of(), "session-1", NOW);
		assertTrue(sparql.contains("?value } };"), sparql);
		assertFalse(sparql.contains("<" + STATION + "> <" + LAB + "OccupiedBy> <"), "nothing inserted: " + sparql);
		assertTrue(sparql.contains("/provenance> { <urn:dmp:state:"), sparql);
	}

	@Test
	void theRecordIsTheSameForEveryWriteOfOneVariable() {
		assertEquals(occupiedBy().record(), occupiedBy().record());
		assertTrue(occupiedBy().record().matches("urn:dmp:state:[0-9a-f]{64}"));
		var other = new StateUpdate(GRAPH, STATION, LAB + "HeldBy", ValueType.REFERENCE, Cardinality.SINGLE, "m");
		assertFalse(other.record().equals(occupiedBy().record()));
	}

	@Test
	void valuesAreTypedByTheVariablesValueTypeAndNeverConverted() {
		assertEquals("\"240.0\"^^<http://www.w3.org/2001/XMLSchema#double>", StateTerms.term(ValueType.NUMBER, 240.0));
		assertEquals("\"240\"^^<http://www.w3.org/2001/XMLSchema#double>", StateTerms.term(ValueType.NUMBER, 240L));
		assertEquals("\"true\"^^<http://www.w3.org/2001/XMLSchema#boolean>", StateTerms.term(ValueType.BOOLEAN, true));
		assertEquals("\"a \\\"b\\\"\\n\"", StateTerms.term(ValueType.STRING, "a \"b\"\n"));
		assertEquals("<urn:uuid:7f3a9c2e>", StateTerms.term(ValueType.REFERENCE, "urn:uuid:7f3a9c2e"));
		assertThrows(IllegalArgumentException.class, () -> StateTerms.term(ValueType.NUMBER, "240"));
		assertThrows(IllegalArgumentException.class, () -> StateTerms.term(ValueType.BOOLEAN, 1));
		assertThrows(IllegalArgumentException.class, () -> StateTerms.term(ValueType.NUMBER, Double.NaN));
		assertThrows(IllegalArgumentException.class, () -> StateTerms.term(ValueType.REFERENCE, "7f3a9c2e"), "not an IRI");
		assertThrows(IllegalArgumentException.class, () -> StateTerms.term(ValueType.REFERENCE, "urn:a> } ; DROP ALL ; <x"));
	}

	@Test
	void aSingleVariableTakesOneValueAndASetAList() {
		assertEquals(List.of(1.0), occupiedBy().values(1.0));
		assertThrows(IllegalArgumentException.class, () -> occupiedBy().values(List.of("urn:a")));
		var set = new StateUpdate(GRAPH, STATION, LAB + "Holds", ValueType.REFERENCE, Cardinality.SET, "m");
		assertEquals(List.of("urn:a", "urn:b"), set.values(List.of("urn:a", "urn:b")));
		assertThrows(IllegalArgumentException.class, () -> set.values("urn:a"));
		String sparql = set.sparql(set.values(List.of("urn:a", "urn:b")), "s", NOW);
		assertTrue(sparql.contains("<" + LAB + "Holds> <urn:a> , <urn:b> }"), sparql);
	}

	@Test
	void theStoreIsNamedByItsOrigin() {
		var settings = new StateStoreSettings(java.net.URI.create("http://state-store:7878/update"));
		assertTrue(settings.serves(java.net.URI.create("http://state-store:7878")));
		assertFalse(settings.serves(java.net.URI.create("http://state-store:7879")));
		assertFalse(settings.serves(java.net.URI.create("https://state-store:7878")));
		assertThrows(IllegalArgumentException.class, () -> new StateStoreSettings(java.net.URI.create("http://u:p@state-store/update")));
	}
}
