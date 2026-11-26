/* Copyright (C) 2026 the aas-camel-dmp Authors
 * SPDX-License-Identifier: MIT */
package dk.aau.smartproductionlab.dmp.core.configuration.route.core;

import org.apache.camel.model.ProcessorDefinition;

/** Performs a fresh connector read for a periodically acquired source. */
public interface SynchronousSourceReadAdapter {
	boolean supports(String endpointUri);

	void configureRead(ProcessorDefinition<?> route, String endpointUri);
}
