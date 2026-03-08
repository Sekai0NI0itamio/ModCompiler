# Template Provenance

- Base source: `https://github.com/FabricMC/fabric-example-mod`
- Snapshot base: official `1.21` branch shallow clone on March 8, 2026
- Retargeted anchor: `minecraft_version=1.21.8`, `fabric-api=0.133.4+1.21.8`
- Reason: separate early `1.21-1.21.8` scaffold because the later `1.21.9+` family is tracked independently in this repo
- Java target: 21
- Notes: this folder is marked `anchor_only` in `version-manifest.json`; exact dependency tuning across the full range still needs to be added before production use
