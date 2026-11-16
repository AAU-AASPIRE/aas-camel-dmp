/* Copyright (C) 2026 the aas-camel-dmp Authors
 * SPDX-License-Identifier: MIT */
package dk.aau.smartproductionlab.dmp.core.component;

import org.apache.camel.CamelContext;

/** Application-owned route/configuration extension loaded by the DMP runtime. */
public interface DmpRuntimeExtension {
	void configure(CamelContext context) throws Exception;
}
