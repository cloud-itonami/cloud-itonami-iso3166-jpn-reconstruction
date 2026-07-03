# Business Model: Independent Reconstruction-Agency Special-Zone Procurement Compliance Service — Japan (Reconstruction Agency)

## Classification

- Repository: `cloud-itonami-iso3166-jpn-reconstruction`
- ISO 3166 (agency-level): `JPN-RECONSTRUCTION`, parent `JPN`
- Ooyake cross-reference: `gov.jpn.reconstruction` (Reconstruction Agency / 復興庁)
- Activity: eligibility and procurement rules specific to Reconstruction Agency special reconstruction zones (復興特区) for an operator delivering disaster-recovery infrastructure, housing, or public-service contracts in a designated reconstruction area
- Social impact: [:disaster-recovery-access :special-zone-procurement-clarity :public-spend-transparency]

## Customer

- an operator bidding on a disaster-recovery infrastructure or housing contract in a designated reconstruction zone
- an operator confirming special reconstruction-zone (復興特区) procurement rules before bidding
- a foreign contractor navigating reconstruction-specific compliance for the first time

## Offer

- reconstruction special-zone (復興特区) eligibility classification walkthrough
- special procurement-rule checklist specific to designated reconstruction areas
- ongoing regulatory-change monitoring for Reconstruction Agency program updates
- compliance-audit export package for the operator's own records

## Revenue

- per-engagement compliance-review fee
- recurring regulatory-change monitoring subscription
- compliance-audit export package

## Trust Controls

- any actual filing, registration, or compliance-program submission
  requires Reconstruction-Zone Compliance Governor clearance and always escalates to human
  sign-off (`:filing/submit` is never automated at any phase)
- a false or fabricated regulatory-requirement claim is a HARD hold that
  cannot be overridden by human approval alone — it must be corrected
  against a cited Reconstruction Agency source first
- this service does **not** provide legal or tax advice; characterization
  and filing on the client's behalf beyond checklist/draft assistance
  routes to Japan-licensed counsel or a registered agent
- every requirement cites the official Reconstruction Agency source or
  regulation, never invented

## Boundary with adjacent actors (read before forking)

- **`cloud-itonami-iso3166-jpn`**: the COUNTRY-level coordinator (general
  Japan public-sector market entry). This repo is a narrower, deeper
  AGENCY-level leaf — most operators need the country-level blueprint plus
  only the agency-level blueprints that actually apply to their contract.
- **`com-etzhayyim-ooyake`** (etzhayyim/root): read-only civic-wayfinding
  mirror of government structure, non-commercial, barred from acting as or
  for the government (G3 impersonation ban). This blueprint is commercial
  and never claims to be Reconstruction Agency or an official channel.
- **`matsurigoto`** (etzhayyim/root): sovereign e-government statecraft —
  literally the government. This blueprint is an independent operator that
  engages with Reconstruction Agency under its public rules — never the
  agency itself.
- **`com-etzhayyim-toritsugi`** (etzhayyim/root): guides a consenting
  INDIVIDUAL citizen through their OWN procedure, non-profit,
  donation-only. This blueprint's client is a business operator, not an
  individual citizen, and it is commercial.
- **`cloud-itonami-M6910`**: helps a client BECOME a legal entity
  (incorporation, ISIC 6910) — a prior, different regulatory phase (company
  law). This blueprint assumes incorporation is already done and handles
  Reconstruction Agency-specific compliance (a different regulatory domain).
