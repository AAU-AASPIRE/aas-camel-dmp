"""The demo's filling station (demo/), authored with aas-model.

The station is aas-model's ``dispensingStation`` resource: an IMA dispensing station with an OPC UA interface. This
module writes two files from it, both generated, never edited by hand:

- ``demo/station/DispensingStationAAS.json``: the Resource the station publishes when it registers, as the station
  simulators of the AP2030 lab export it (``model_dump_json``), with its OPC UA base set to the demo's station;
- ``demo/aas/filling-station.json``: the AAS environment that registration builds from it
  (``build_from_model``, serialized as BaSyx JSON), which BaSyx loads at startup in place of a registration
  service.

Regenerate from fixtures/ after changing aas-model:

    uv run python -m demo
"""

from __future__ import annotations

import json
from pathlib import Path

from aas_model import Resource, build_from_model
from aas_model.resource_instances import RESOURCE_FACTORIES
from basyx.aas.adapter.json import json_serialization

DEMO = Path(__file__).resolve().parent.parent / "demo"
#: Where the station's OPC UA server is reachable on the demo's network.
OPCUA_BASE = "opc.tcp://filling-station:4840/dispensing/"


def resource() -> Resource:
    station = RESOURCE_FACTORIES["dispensingStation"]()
    export = json.loads(station.model_dump_json())
    for description in export["asset_interfaces_description"]["InterfaceTemplateForOPCUA"].values():
        description["EndpointMetadata"]["base"]["value"] = OPCUA_BASE
    return Resource.model_validate(export)


def main() -> None:
    station = resource()
    (DEMO / "station").mkdir(parents=True, exist_ok=True)
    (DEMO / "aas").mkdir(parents=True, exist_ok=True)
    (DEMO / "station" / f"{station.id_short}.json").write_text(station.model_dump_json(indent=2) + "\n", encoding="utf-8")
    environment = json.loads(json_serialization.object_store_to_json(build_from_model(station)))
    (DEMO / "aas" / "filling-station.json").write_text(json.dumps(environment, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {station.id_short}: {len(environment.get('submodels', []))} submodels")


if __name__ == "__main__":
    main()
