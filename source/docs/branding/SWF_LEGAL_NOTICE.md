# SWF Asset Pack — Legal Notice

## Overview

Habnut does not distribute or bundle third-party SWF asset packs. Asset packs
must be supplied by the operator and installed separately using `habnutctl swf install`.

## Operator Responsibility

By installing and operating an asset pack alongside Habnut, the operator confirms
that they hold all necessary rights, licences, or permissions for the assets used.
The Habnut project and its contributors accept no liability arising from unlicensed
asset use by operators.

## Branding Requirement

All installed asset packs must have branding applied before activation. The command
`habnutctl swf rebrand <name>` performs the required string substitutions across XML
configuration files to replace third-party brand strings with Habnut branding.

Packs that have not been rebranded will fail the `habnutctl swf validate` check and
the CI asset-validation gate. No pack may be deployed with unmodified third-party
brand strings in any player-facing context.

## Asset Validator

The `tools/asset-validator` tool enforces the following at build time:

- All entries in `required_xml`, `required_swf`, and `required_json` within
  `PACK_MANIFEST.json` are present in the pack ZIP.
- No XML file in the pack contains prohibited placeholder markers.
- The manifest carries a non-empty `version` field.

CI will block any build that fails asset validation.

## PACK_MANIFEST.json

The manifest schema is located at `infra/swf/PACK_MANIFEST.json`. Operators may
extend the `required_*` lists to enforce pack completeness for their specific
deployment. The `branding.replace_strings` array drives `habnutctl swf rebrand`.
