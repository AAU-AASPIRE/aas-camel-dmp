/* Copyright (C) 2026 the aas-camel-dmp Authors
 * SPDX-License-Identifier: MIT */
package dk.aau.smartproductionlab.dmp.aas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AasValuesTest {
	@Test
	void aCoordinateReachesLuaWithItsValuesTypedAndItsRelativeToAsTheReference() {
		Map<String, Object> base = Map.of("type", "ModelReference",
				"keys", List.of(Map.of("type", "Submodel", "value", "urn:sm"), Map.of("type", "SubmodelElementCollection", "value", "Base")));
		Object coordinate = Map.of("modelType", "SubmodelElementCollection", "idShort", "TransferPoint", "value", List.of(
				Map.of("modelType", "Property", "idShort", "Y", "valueType", "xs:double", "value", "60.0"),
				Map.of("modelType", "ReferenceElement", "idShort", "RelativeTo", "value", base)));
		assertEquals(Map.of("Y", 60.0, "RelativeTo", base), AasValues.elementValue(coordinate));
	}
}
