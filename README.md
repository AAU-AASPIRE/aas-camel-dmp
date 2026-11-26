# AAS-Camel-DMP

A data-mapping processor (DMP) for Asset Administration Shells. It reads the
**AIMC** (Asset Interfaces Mapping Configuration) and **AID** (Asset Interfaces
Description) submodels from an AAS v3 repository and compiles each mapping into
[Apache Camel](https://camel.apache.org/) routes. These routes move values
between devices and the AAS, transform them with sandboxed Lua, and serve AAS
Operations by invoking device actions. There are no configuration files: routes
are built at runtime from the repository and kept current from its change
events (Kafka or MQTT).

- **Protocols** (AID bindings): MQTT 5, OPC UA, HTTP (polling, long-poll, SSE),
  Modbus TCP, SNMP v1/v2c
- **AAS**: element sources and sinks, event-sourced Properties and collections,
  Operations delegated by BaSyx, TimeSeries segments on InfluxDB 2, StateVariables
  in a SPARQL state store
- **Transformations**: Lua blobs in the AIMC, compiled in memory, with a CPU
  budget and no I/O

Built on Java 25, Camel 4 and Eclipse AAS4J. Tested with Eclipse BaSyx v2.

## Quickstart

[`demo/`](demo/) runs a simulated filling station (an IMA dispensing station
with an OPC UA server) and its AAS on BaSyx, connected by the DMP:

```bash
cd demo && docker compose up --build
./dispense.sh 5     # Occupy, dispense 5 ml, Release: AAS Operations BaSyx delegates to the DMP
```

| | |
| --- | --- |
| BaSyx web UI | http://localhost:3000 (`UI_PORT` to change) |
| AAS repository | http://localhost:8081 |
| DMP mapping status | http://localhost:8080/management/status |
| InfluxDB (weight and state, written by the DMP) | http://localhost:8086, user `demo`, password `demo-password`, bucket `ap2030` |

The station's AAS ([`demo/aas/filling-station.json`](demo/aas/filling-station.json))
is generated with aas-model by `fixtures/demo.py`. Edit its AIMC, for example a
Lua transformation, and the DMP recompiles that mapping from the change event.

## Build and run

```bash
mvn install -Pintegration     # build and test (unit and integration)
docker build -t aas-camel-dmp .
docker run -p 8080:8080 \
  -e DMP_MANAGED_AAS_REPOSITORY_URI=http://aas-env:8081 \
  -e DMP_MANAGED_EVENT_TRANSPORT=mqtt -e DMP_MANAGED_MQTT_BROKER_URI=tcp://mosquitto:1883 \
  aas-camel-dmp
```

Port 8080 serves delegated Operation calls, `/health` and `/management/status`.
`/management/status` shows each mapping as `installed`, `blocked`, `stale` or
`failed`.

<details>
<summary>Configuration (environment variables)</summary>

| Variable | Meaning / default |
| --- | --- |
| `DMP_MANAGED_AAS_REPOSITORY_URI` | **Required.** AAS repository root |
| `DMP_MANAGED_AAS_AUTHORIZATION` | `Authorization` header for repository reads |
| `DMP_CREDENTIALS_FILE` | JSON file with the credentials that the AIDs' security schemes need; without it only `nosec` interfaces compile |
| `DMP_MANAGED_EVENT_TRANSPORT` | `kafka` (default), `mqtt`, or `poll` (no events: rescan every `DMP_MANAGED_RESCAN_INTERVAL`, default `PT30S`) |
| `DMP_MANAGED_KAFKA_BROKERS`, `DMP_MANAGED_CONTROL_TOPIC`, `DMP_MANAGED_CONTROL_GROUP_ID` | Required for Kafka |
| `DMP_MANAGED_KAFKA_SECURITY_PROTOCOL`, `…_SASL_MECHANISM`, `…_SASL_USERNAME`, `…_SASL_PASSWORD` | Kafka security (default `PLAINTEXT`) |
| `DMP_MANAGED_MQTT_BROKER_URI` | Required for MQTT: the broker carrying BaSyx's MQTT events |
| `DMP_MANAGED_MQTT_CLIENT_ID`, `…_USERNAME`, `…_PASSWORD`, `…_REPOSITORY_ID`, `…_SESSION_EXPIRY_SECONDS` | MQTT session (defaults `dmp-control`, none, `+`, 86400) |
| `DMP_MANAGED_AIMC_IDS` | JSON array of AIMC ids to adopt; default: every AIMC in the repository |
| `DMP_INFLUX_URL`, `DMP_INFLUX_ORG`, `DMP_INFLUX_TOKEN` | InfluxDB 2 for TimeSeries segments; without them segment mappings are blocked |
| `DMP_STATE_STORE_UPDATE_URL` | SPARQL 1.1 Update endpoint of the state store for StateVariable sinks (e.g. `http://state-store:7878/update`); the `Target/Endpoint` of an asset's `Variables/Knowledge` must name its origin; without it state mappings are blocked |
| `DMP_HTTP_PORT` | Listener port, default 8080 |
| `DMP_AFFORDANCE_VALIDATION` | `OFF` (default), `ON` or `REPORT` |

Limits and timeouts: `DMP_MANAGED_MAX_MAPPINGS`, `…_MAX_INVALIDATIONS`,
`…_MAX_PARALLEL_READS`, `…_RETRY_INTERVAL_MS`, `…_CONNECT_TIMEOUT_MS`,
`…_REQUEST_TIMEOUT_MS`, `…_PAGE_SIZE`, `…_MAX_PAGES`, `DMP_INFLUX_LOOKBACK`,
`DMP_INFLUX_FLUSH_INTERVAL`, `DMP_INFLUX_BUFFER_LIMIT`, `DMP_STATE_STORE_TIMEOUT` (ISO-8601, default `PT5S`).
</details>

## Modules

| Module | Purpose |
| --- | --- |
| `dmp.core` | Route model, fan-in/out, mapping bundle lifecycle |
| `dmp.management` | Repository client, AIMC/AID projection, mapping compiler, event consumers |
| `dmp.component` | Executable, health and status endpoints |
| `dmp.camel-lua` | Lua sandbox |
| `dmp.camel-aas` | AAS element sources and sinks, Operation delegation |
| `dmp.camel-mqtt`, `-opcua`, `-http`, `-modbus`, `-snmp` | Protocol connectors |
| `dmp.camel-influxdb2` | TimeSeries segments on InfluxDB 2 |
| `dmp.camel-sparql` | StateVariable sinks: the latest value of a state variable in a SPARQL 1.1 state store, with its provenance; a result without a value for the SinkId clears it |

## Evaluation

[`evaluation/`](evaluation/) reproduces the paper's experiments and figures.

## Citation

T. Schwörer, M. B. Jensen, C. Schou, D. Chrysostomou: *AAS-Camel-DMP – A
Bi-directional Data Mapping Processor for Automated Vertical Asset Integration*,
Procedia CIRP (CIRP CMS 2027), submitted. See [CITATION.cff](CITATION.cff).

## License

MIT; see [LICENSE](LICENSE). Derived from
[Eclipse BaSyx DataBridge](https://github.com/eclipse-basyx/basyx-databridge); see [NOTICE](NOTICE).

Part of [AASPIRE](https://github.com/AAU-AASPIRE/aaspire): developed here since 26 Nov 2026, with the history of
the AP2030 line's aas-camel-dmp.
