# ADR-001: Pre-Ingestion Golden Record Deduplication & Real-Time Sanctions Gate for S/4HANA 2025

## Status
Accepted

## Context
In SAP S/4HANA 2025 FPS01 transformations, legacy Customer (XD01) and Vendor (XK01) master records converge into the unified Business Partner (API_BUSINESS_PARTNER) domain model. Unverified records cause duplicate Golden Records, failed SEPA runs, and OFAC/EU sanctions exposure.

## Decision
Implement an inline Compliance & Quality Gate in SAP CPI enforcing deterministic SHA-256 Golden Record fingerprinting, ISO 13616 Mod-97 IBAN verification, and normalized OFAC/EU watchlist screening prior to OData V4 ingestion.

## Consequences
Zero duplicate or non-compliant Business Partners enter the S/4HANA 2025 Clean Core, with full audit traceability via X-MDG-Golden-Hash and HTTP 422 quarantine routing.
