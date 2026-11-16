/* Copyright (C) 2026 the aas-camel-dmp Authors
 * SPDX-License-Identifier: MIT */
package dk.aau.smartproductionlab.dmp.core.util;

/** Marks a decoded value whose connector wire contract requires JSON encoding. */
public record ConnectorJsonPayload(Object value) { }
