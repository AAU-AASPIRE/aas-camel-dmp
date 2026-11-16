/* Copyright (C) 2026 the aas-camel-dmp Authors
 * SPDX-License-Identifier: MIT */
package dk.aau.smartproductionlab.dmp.opcua.canonical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the JSON value of OPC UA built-in values.
 */
class OpcUaCanonicalTest {

	@Test
	void scalarsKeepTheirJsonType() {
		assertEquals(42.5, OpcUaCanonical.jsonValue(42.5));
		assertEquals("idle", OpcUaCanonical.jsonValue("idle"));
		assertEquals(Boolean.TRUE, OpcUaCanonical.jsonValue(true));
		assertNull(OpcUaCanonical.jsonValue(null));
	}

	@Test
	void byteArrayValueBecomesBase64() {
		assertEquals("AQID", OpcUaCanonical.jsonValue(new byte[] { 1, 2, 3 }));
	}

	@Test
	void decodedStructuresUnionsAndEnumerationsAreJsonObjectsAndNumbers() {
		var type = (org.eclipse.milo.opcua.sdk.core.typetree.DataType) java.lang.reflect.Proxy.newProxyInstance(
				getClass().getClassLoader(), new Class<?>[] { org.eclipse.milo.opcua.sdk.core.typetree.DataType.class },
				(proxy, method, args) -> method.getName().equals("toString") ? "Stub" : null);
		var enumType = (org.eclipse.milo.opcua.sdk.core.typetree.DataType) java.lang.reflect.Proxy.newProxyInstance(
				getClass().getClassLoader(), new Class<?>[] { org.eclipse.milo.opcua.sdk.core.typetree.DataType.class },
				(proxy, method, args) -> method.getName().equals("getDataTypeDefinition")
						? new org.eclipse.milo.opcua.stack.core.types.structured.EnumDefinition(
								new org.eclipse.milo.opcua.stack.core.types.structured.EnumField[] {
										new org.eclipse.milo.opcua.stack.core.types.structured.EnumField(2L, null, null, "Running") }) : null);
		var members = new java.util.LinkedHashMap<String, Object>();
		members.put("Name", "Cake");
		members.put("Limits", new org.eclipse.milo.opcua.stack.core.types.structured.Range(0.0, 10.0));
		members.put("Mode", new org.eclipse.milo.opcua.sdk.core.types.DynamicEnumType(enumType, 2));
		members.put("Steps", new Integer[] { 1, 2 });
		assertEquals(Map.of("Name", "Cake", "Limits", Map.of("Low", 0.0, "High", 10.0), "Mode", 2, "Steps", java.util.List.of(1, 2)),
				OpcUaCanonical.jsonValue(new Variant(new org.eclipse.milo.opcua.sdk.core.types.DynamicStructType(type, members))));
		assertEquals(Map.of("Fault", 7), OpcUaCanonical.jsonValue(new org.eclipse.milo.opcua.sdk.core.types.DynamicUnionType(type,
				new org.eclipse.milo.opcua.sdk.core.types.DynamicUnionType.UnionValue("Fault", 7))));
		assertNull(OpcUaCanonical.jsonValue(new org.eclipse.milo.opcua.sdk.core.types.DynamicUnionType(type)), "a union holding no field");
		assertEquals(1, OpcUaCanonical.jsonValue(org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass.Object));
	}
}
