# ADR 0004 — Retired parking layout storage selection

Status: superseded on 2026-11-13 by [ADR 0006 — Private filesystem storage](0006-parking-layout-private-filesystem.md).

This historical decision originally selected Firebase Cloud Storage for source images and immutable layout JSON. The user subsequently chose private filesystem storage on the existing VPS and requested removal of the cloud-specific implementation. Its adapter, SDK dependency and bucket/project credential configuration have been removed. The application no longer supports that provider.

MySQL layout metadata, physical-slot identity, booking/hold behavior and the existing API remain part of L04. Superseding this storage decision does not resolve its outstanding review findings. Current operations are documented in [the filesystem runbook](../deployment/parking-layout-filesystem.md); no old cloud setup instructions apply.
