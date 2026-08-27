# Operator Guide

## First Deployment

1. Confirm the client already uses (or has completed the equivalent of)
   `cloud-itonami-iso3166-jpn` for general Japan market-entry; this repo is
   an agency-specific supplement, not a substitute.
2. Register the client's intake: business type, the specific
   Reconstruction Agency-regulated activity involved, prior filing/compliance
   history in Japan if any.
3. Run the advisor in read-only mode against Reconstruction Agency's
   (復興庁) published guidance. Note that the Agency cites its own statutes
   through a JavaScript surface that answers `200` for law identifiers that do
   not exist; resolve statutes through the e-Gov law API instead, as
   `facts.edn` does, and check `repeal_status` — repealed rules are still
   served in full.
4. Compare the checklist against the client's current documentation.
5. Enable gated filing/compliance-draft assistance once the
   Reconstruction-Zone Compliance Governor contract is trusted; actual submission always
   requires human sign-off.

## Minimum Production Controls

- client-owned data store for compliance documents
- clear provenance (official Reconstruction Agency source citation) for every
  requirement surfaced — the citable set is [`facts.edn`](../facts.edn), and a
  requirement whose authority is not in it has no spec-basis here. Extend the
  register against the authority; never invent a law id or a URL. Confirm the
  register still holds with `nbb scripts/verify-facts.cljs` (exit 2 means the
  run could not answer, which is not a pass)
- approval workflow for any filing, registration, or compliance-program
  submission
- named referral relationship with Japan-licensed counsel or a registered
  agent for anything beyond checklist/draft assistance
- monthly audit export

## Certification

Certified operators must prove data provenance, audit traceability, that
automated actions cannot bypass the Reconstruction-Zone Compliance Governor, and a working
referral relationship with Japan-licensed counsel or a registered agent for
whatever licensed representation Japanese law requires for actual
Reconstruction Agency filings.
