# Template Provenance

- Source: `https://github.com/FabricMC/fabric-example-mod`
- Snapshot: official `1.21` branch shallow clone on March 8, 2026
- Retargeted anchor: `minecraft_version=1.21.1`, `fabric-api=0.116.4+1.21.1`
- Reason: separate early `1.21-1.21.1` scaffold because `1.21.2+` uses a different teleport API family in the Tpa Teleport range build
- Java target: 21
- Notes: this folder is marked `anchor_only` in `version-manifest.json`; exact dependency tuning across the full range still needs to be added before production use
