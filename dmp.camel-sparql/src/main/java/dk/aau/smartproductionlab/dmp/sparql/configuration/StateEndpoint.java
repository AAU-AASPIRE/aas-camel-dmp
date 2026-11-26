/* Copyright (C) 2026 the aas-camel-dmp Authors
 * SPDX-License-Identifier: MIT */
package dk.aau.smartproductionlab.dmp.sparql.configuration;

import dk.aau.smartproductionlab.dmp.core.plan.BundleResources;
import dk.aau.smartproductionlab.dmp.core.plan.Endpoint;
import dk.aau.smartproductionlab.dmp.sparql.StateTerms.Cardinality;
import dk.aau.smartproductionlab.dmp.sparql.StateTerms.ValueType;
import dk.aau.smartproductionlab.dmp.sparql.component.DmpSparqlComponent;

/**
 * A state variable in the configured state store, written as a sink: each evaluation of its mapping sets it to the
 * value under its SinkId, and a result without one clears it ({@link #acceptsAbsentValue()}).
 *
 * @param source the mapping that owns the variable, recorded as the provenance of each write
 */
public record StateEndpoint(String graph, String subject, String property, ValueType valueType, Cardinality cardinality,
		String source) implements Endpoint {
	@Override
	public String uri(BundleResources resources) {
		return DmpSparqlComponent.uri(graph, subject, property, valueType, cardinality, source);
	}

	/** Each evaluation sets the variable; no value for its SinkId means it has none. */
	@Override
	public boolean acceptsAbsentValue() { return true; }
}
