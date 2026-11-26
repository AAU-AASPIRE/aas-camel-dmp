/* Copyright (C) 2026 the aas-camel-dmp Authors
 * SPDX-License-Identifier: MIT */
package dk.aau.smartproductionlab.dmp.modbus.component;

import org.apache.camel.Exchange;
import org.apache.camel.support.PollingConsumerSupport;

/** One fresh tag read per poll (e.g. {@code pollEnrich}); a failed or rejected read raises. */
final class DmpModbusPollingConsumer extends PollingConsumerSupport {
	private final DmpModbusEndpoint endpoint;

	DmpModbusPollingConsumer(DmpModbusEndpoint endpoint) {
		super(endpoint);
		this.endpoint = endpoint;
	}

	@Override
	public Exchange receive() { return receive(DmpModbusEndpoint.REQUEST_TIMEOUT_MS); }

	@Override
	public Exchange receiveNoWait() { return receive(DmpModbusEndpoint.REQUEST_TIMEOUT_MS); }

	@Override
	public Exchange receive(long timeout) {
		Exchange exchange = endpoint.createExchange();
		exchange.getMessage().setBody(endpoint.read());
		return exchange;
	}

	@Override protected void doStart() { }

	@Override protected void doStop() { }
}
