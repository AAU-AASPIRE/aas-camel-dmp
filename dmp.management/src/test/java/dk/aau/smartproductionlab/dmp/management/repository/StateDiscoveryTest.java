/* Copyright (C) 2026 the aas-camel-dmp Authors
 * SPDX-License-Identifier: MIT */
package dk.aau.smartproductionlab.dmp.management.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import dk.aau.smartproductionlab.dmp.core.plan.MappingRoute;
import dk.aau.smartproductionlab.dmp.core.plan.Sink;
import dk.aau.smartproductionlab.dmp.management.compiler.ConnectorConfigurationFactory;
import dk.aau.smartproductionlab.dmp.management.compiler.ConnectorConfigurationFactory.DesiredBundle;
import dk.aau.smartproductionlab.dmp.management.compiler.MappingPlanCompiler;
import dk.aau.smartproductionlab.dmp.sparql.StateStoreSettings;
import dk.aau.smartproductionlab.dmp.sparql.StateTerms.Cardinality;
import dk.aau.smartproductionlab.dmp.sparql.StateTerms.ValueType;
import dk.aau.smartproductionlab.dmp.sparql.configuration.StateEndpoint;
import org.junit.jupiter.api.Test;

/**
 * A Sink on a StateVariable (plan D36: the state store is one more sink), compiled through discovery from the
 * StateDeclarations aas-model generates ({@code state/*.json}): the IMA dispensing station's {@code OccupiedBy},
 * about its own AAS, and the filling module's {@code TransferPointX}, about its transfer point.
 */
class StateDiscoveryTest {
	private static final String STATION = "https://smartproductionlab.aau.dk/aas/imaDispensingSystemAAS";
	private static final String STATION_STATE = STATION + "/submodels/StateDeclaration";
	private static final String MODULE = "https://smartproductionlab.aau.dk/aas/FillingModuleAAS";
	private static final String MODULE_STATE = MODULE + "/submodels/StateDeclaration";
	private static final String LAB = "https://smartproductionlab.aau.dk/semantics/";
	private static final String AIMC_ID = "urn:fixture/submodels/AIMC";
	private static final String DATA_ID = "urn:fixture/submodels/Data";
	private static final StateStoreSettings STORE = new StateStoreSettings(URI.create("http://state-store:7878/update"));

	@Test
	void aStationsOccupiedByIsWrittenAsAReferenceAboutItsOwnAas() throws Exception {
		try (var repository = new FakeAasRepository().put(resource("station-state")).put(data())
				.put(aimc(refProperty(DATA_ID, "Value"), variable(STATION_STATE, "OccupiedBy"), "OccupiedBy"))) {
			DesiredBundle bundle = only(discovery(repository, STORE).scan());
			assertTrue(bundle.valid(), issues(bundle));
			Sink sink = stateSink(bundle);
			var target = (StateEndpoint) sink.endpoint();
			assertEquals("OccupiedBy", sink.sinkId());
			assertEquals(STATION, target.subject(), "an AAS is its id");
			assertEquals(LAB + "OccupiedBy", target.property(), "the property is the variable's meaning");
			assertEquals(ValueType.REFERENCE, target.valueType());
			assertEquals(Cardinality.SINGLE, target.cardinality());
			assertEquals("https://smartproductionlab.aau.dk/state", target.graph());
			assertTrue(target.acceptsAbsentValue(), "an evaluation without a value clears the variable");
			assertTrue(target.uri(null).startsWith("dmp-sparql:state?"));
			var dependencies = bundle.desired().dependencies();
			assertTrue(dependencies.contains(new MappingPlanCompiler.Dependency("series", STATION_STATE, "OccupiedBy")), dependencies.toString());
			assertTrue(dependencies.contains(new MappingPlanCompiler.Dependency("series", STATION_STATE, "Target")),
					"the store and graph are the declaration's Target: " + dependencies);
		}
	}

	@Test
	void aPlacesPositionIsAboutTheCoordinateItIs() throws Exception {
		try (var repository = new FakeAasRepository().put(resource("module-state")).put(resource("module-variables")).put(data())
				.put(aimc(refProperty(DATA_ID, "Value"), variable(MODULE_STATE, "TransferPointX"), "TransferPointX"))) {
			DesiredBundle bundle = only(discovery(repository, STORE).scan());
			assertTrue(bundle.valid(), issues(bundle));
			var target = (StateEndpoint) stateSink(bundle).endpoint();
			assertEquals(MODULE + "/submodels/Variables#Measurements.Coordinates.TransferPoint", target.subject(),
					"an element is <submodel id>#<idShortPath>");
			assertEquals(LAB + "AbsoluteX", target.property());
			assertEquals(ValueType.NUMBER, target.valueType());
		}
	}

	@Test
	void theFillingModulesTransferPointMappingComposesItsCoordinatesIntoTheStore() throws Exception {
		try (var repository = new FakeAasRepository().put(resource("module-state")).put(resource("module-variables"))
				.put(resource("module-aimc"))) {
			DesiredBundle bundle = only(discovery(repository, STORE).scan());
			assertTrue(bundle.valid(), issues(bundle));
			var route = assertInstanceOf(MappingRoute.class, bundle.bundle().route());
			assertEquals(2, route.sources().size(), "the transfer point and the base it is relative to");
			assertEquals(java.util.List.of("TransferPointX", "TransferPointY"),
					route.sinks().stream().map(Sink::sinkId).toList());
			assertTrue(route.sinks().stream().allMatch(sink -> sink.endpoint() instanceof StateEndpoint));
		}
	}

	@Test
	void aSubjectElementThatDoesNotExistBlocksTheMapping() throws Exception {
		try (var repository = new FakeAasRepository().put(resource("module-state")).put(data())
				.put(aimc(refProperty(DATA_ID, "Value"), variable(MODULE_STATE, "TransferPointX"), "TransferPointX"))) {
			assertFalse(only(discovery(repository, STORE).scan()).valid());
		}
	}

	@Test
	void withoutAConfiguredStateStoreTheMappingIsBlocked() throws Exception {
		try (var repository = new FakeAasRepository().put(resource("station-state")).put(data())
				.put(aimc(refProperty(DATA_ID, "Value"), variable(STATION_STATE, "OccupiedBy"), "OccupiedBy"))) {
			DesiredBundle bundle = only(discovery(repository, null).scan());
			assertFalse(bundle.valid());
			assertTrue(issues(bundle).contains(StateStoreSettings.UPDATE_URL), issues(bundle));
		}
	}

	@Test
	void aDeclarationNamingAnotherStoreIsBlocked() throws Exception {
		try (var repository = new FakeAasRepository().put(resource("station-state")).put(data())
				.put(aimc(refProperty(DATA_ID, "Value"), variable(STATION_STATE, "OccupiedBy"), "OccupiedBy"))) {
			var elsewhere = new StateStoreSettings(URI.create("http://other-store:7878/update"));
			DesiredBundle bundle = only(discovery(repository, elsewhere).scan());
			assertFalse(bundle.valid());
			assertTrue(issues(bundle).contains("does not name the configured state store"), issues(bundle));
		}
	}

	@Test
	void aStateVariableIsNeverASource() throws Exception {
		try (var repository = new FakeAasRepository().put(resource("station-state")).put(data())
				.put(aimc(variable(STATION_STATE, "OccupiedBy"), refProperty(DATA_ID, "Value"), "Value"))) {
			DesiredBundle bundle = only(discovery(repository, STORE).scan());
			assertFalse(bundle.valid());
			assertTrue(issues(bundle).contains("a StateVariable is a sink"), issues(bundle));
		}
	}

	@Test
	void aVariableWithoutItsValueTypeCannotBeWritten() throws Exception {
		String untyped = resource("station-state").replace("\"idShort\": \"ValueType\"", "\"idShort\": \"Untyped\"")
				.replace("https://smartproductionlab.aau.dk/state/StateVariable/ValueType/1/0", "urn:not-a-value-type");
		try (var repository = new FakeAasRepository().put(untyped).put(data())
				.put(aimc(refProperty(DATA_ID, "Value"), variable(STATION_STATE, "OccupiedBy"), "OccupiedBy"))) {
			DesiredBundle bundle = only(discovery(repository, STORE).scan());
			assertFalse(bundle.valid());
			assertTrue(issues(bundle).contains("ValueType"), issues(bundle));
		}
	}

	private static RepositoryDiscovery discovery(FakeAasRepository repository, StateStoreSettings store) {
		return new RepositoryDiscovery(repository.client(),
				new ConnectorConfigurationFactory.RuntimeSettings(repository.uri(), null, null, true, store));
	}

	private static Sink stateSink(DesiredBundle bundle) {
		return assertInstanceOf(MappingRoute.class, bundle.bundle().route()).sinks().stream()
				.filter(sink -> sink.endpoint() instanceof StateEndpoint).findFirst().orElseThrow();
	}

	private static DesiredBundle only(RepositoryDiscovery.Scan scan) {
		assertEquals(1, scan.mappings().size(), scan.toString());
		return scan.mappings().values().iterator().next().desired();
	}

	private static String issues(DesiredBundle bundle) {
		return bundle.issues().toString() + bundle.desired().failures();
	}

	private static String resource(String name) throws IOException {
		try (InputStream in = StateDiscoveryTest.class.getResourceAsStream("/state/" + name + ".json")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static String variable(String declaration, String name) {
		return "{\"type\":\"ModelReference\",\"keys\":[" + key("Submodel", declaration) + "," + key("SubmodelElementCollection", name) + "]}";
	}

	private static String refProperty(String submodel, String property) {
		return "{\"type\":\"ModelReference\",\"keys\":[" + key("Submodel", submodel) + "," + key("Property", property) + "]}";
	}

	private static String key(String type, String value) {
		return "{\"type\":\"" + type + "\",\"value\":\"" + value + "\"}";
	}

	private static String data() {
		return "{\"modelType\":\"Submodel\",\"id\":\"" + DATA_ID + "\",\"idShort\":\"Data\",\"submodelElements\":["
				+ "{\"modelType\":\"Property\",\"idShort\":\"Value\",\"valueType\":\"xs:string\",\"value\":\"IDLE\"}]}";
	}

	private static String aimc(String source, String sink, String sinkId) {
		return "{\"modelType\":\"Submodel\",\"id\":\"" + AIMC_ID + "\",\"idShort\":\"AIMC\","
				+ "\"semanticId\":{\"type\":\"ExternalReference\",\"keys\":[{\"type\":\"GlobalReference\",\"value\":\"https://admin-shell.io/idta/AssetInterfacesMappingConfiguration/2/0/Submodel\"}]},"
				+ "\"submodelElements\":[{\"modelType\":\"SubmodelElementList\",\"idShort\":\"MappingConfigurations\",\"typeValueListElement\":\"SubmodelElementCollection\","
				+ "\"semanticId\":{\"type\":\"ExternalReference\",\"keys\":[{\"type\":\"GlobalReference\",\"value\":\"https://admin-shell.io/idta/AssetInterfacesMappingConfiguration/1/0/MappingConfigurations\"}]},"
				+ "\"value\":[{\"modelType\":\"SubmodelElementCollection\",\"idShort\":\"state\",\"value\":["
				+ endpoint("Sources", "Source", "SourceId", "Value", source) + ","
				+ endpoint("Sinks", "Sink", "SinkId", sinkId, sink) + ","
				+ "{\"modelType\":\"Blob\",\"idShort\":\"Transformation\",\"contentType\":\"text/plain\",\"value\":\""
				+ java.util.Base64.getEncoder().encodeToString(("function aimc_main(sources) return { " + sinkId
						+ " = sources.Value } end").getBytes(StandardCharsets.UTF_8)) + "\"}"
				+ "]}]}]}";
	}

	private static String endpoint(String listName, String referenceName, String idName, String id, String reference) {
		return "{\"modelType\":\"SubmodelElementList\",\"idShort\":\"" + listName
				+ "\",\"typeValueListElement\":\"SubmodelElementCollection\",\"value\":[{\"modelType\":\"SubmodelElementCollection\",\"value\":["
				+ "{\"modelType\":\"ReferenceElement\",\"idShort\":\"" + referenceName + "\",\"value\":" + reference + "},"
				+ "{\"modelType\":\"Property\",\"idShort\":\"" + idName + "\",\"valueType\":\"xs:string\",\"value\":\"" + id + "\"}"
				+ "]}]}";
	}
}
