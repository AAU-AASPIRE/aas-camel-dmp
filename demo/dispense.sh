#!/usr/bin/env bash
# One fill through the station's AAS: Occupy, Dispensing <volume ml> (default 5), Release.
# Each call goes to BaSyx, which delegates it to the DMP, which calls the station's OPC UA Method.
set -euo pipefail

VOLUME="${1:-5}"
AAS="${AAS:-http://localhost:8081}"
CCI="https://smartproductionlab.aau.dk/aas/DispensingStationAAS/submodels/ControlComponentInstance"
SKILLS="$AAS/submodels/$(printf '%s' "$CCI" | base64 | tr -d '\n=' | tr '+/' '-_')/submodel-elements/Skills"
UUID="$(cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen)"
UUID_ARG='{"value":{"modelType":"Property","idShort":"Uuid","valueType":"xs:string","value":"'"$UUID"'"}}'

invoke() { # skill, inputArguments
  echo "== $1"
  curl -sS -X POST "$SKILLS.$1.operation/invoke" -H 'Content-Type: application/json' \
    -d '{"inputArguments":['"$2"'],"inoutputArguments":['"$UUID_ARG"'],"clientTimeoutDuration":"PT60S"}'
  echo
}

invoke Occupy ''
invoke Dispensing '{"value":{"modelType":"Property","idShort":"Volume","valueType":"xs:double","value":"'"$VOLUME"'"}}'
invoke Release ''
