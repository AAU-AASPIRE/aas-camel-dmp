/* Copyright (C) 2026 the aas-camel-dmp Authors
 * SPDX-License-Identifier: MIT */
package dk.aau.smartproductionlab.dmp.executable.regression;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ServiceLoader;
import java.util.List;

import dk.aau.smartproductionlab.dmp.core.configuration.route.core.SynchronousInvocationAdapter;
import dk.aau.smartproductionlab.dmp.core.configuration.route.core.SynchronousSourceReadAdapter;
import org.junit.jupiter.api.Test;

public class ConnectorInvocationAdapterDiscoveryTest {
	@Test
	public void componentClasspathDiscoversSynchronousConnectorAdapters() {
		List<SynchronousInvocationAdapter> adapters = ServiceLoader
				.load(SynchronousInvocationAdapter.class).stream()
				.map(ServiceLoader.Provider::get).toList();
		assertTrue(adapters.stream().anyMatch(a -> a.supports("dmp-opcua:test")));
		assertTrue(adapters.stream().anyMatch(a -> a.supports("dmp-mqtt:command")));
		assertTrue(adapters.stream().anyMatch(a -> a.supports("http://device/action")));
		assertFalse(adapters.stream().anyMatch(a -> a.supports("dmp-modbus:modbus-tcp://device")),
				"a PLC4X action trigger is the dmp-modbus producer's acknowledged tag write");
	}

	@Test
	public void componentClasspathDiscoversFreshSourceReadAdapters() {
		List<SynchronousSourceReadAdapter> adapters = ServiceLoader
				.load(SynchronousSourceReadAdapter.class).stream()
				.map(ServiceLoader.Provider::get).toList();
		assertTrue(adapters.stream().anyMatch(a -> a.supports("dmp-opcua:test")));
	}
}
