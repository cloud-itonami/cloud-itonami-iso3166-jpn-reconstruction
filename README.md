# cloud-itonami-iso3166-jpn-reconstruction

Open ISO 3166 Agency Blueprint for **JPN-RECONSTRUCTION**: Reconstruction Agency
(復興庁, Reconstruction Agency) — a Japan-agency-level LEAF under
the `cloud-itonami-iso3166-jpn` country-level coordinator.

This repository designs a forkable OSS business for an independent
compliance consultant: an already-incorporated operator (typically one
already using `cloud-itonami-iso3166-jpn` for general Japan market entry)
gets a Compliance Advisor + independent **Reconstruction-Zone Compliance Governor** to
navigate eligibility and procurement rules specific to Reconstruction Agency special reconstruction zones (復興特区) for an operator delivering disaster-recovery infrastructure, housing, or public-service contracts in a designated reconstruction area.

This is the final repo in the Japan agency-level sweep started by
ADR-2607040100 — with this blueprint published, all 19/19 Japan central-
government bodies in `kotoba-lang/iso3166` are `:maturity :blueprint`.

## No robotics premise — digital/data service exemption

Agency-specific compliance navigation is a pure data/software service with
no physical-domain work — the same exemption class as `cloud-itonami-6310`
and `cloud-itonami-gtin-*`. `blueprint.edn` sets
`:itonami.blueprint/robotics false` and `:required-technologies` lists only
real capabilities (`:identity`, `:forms`, `:dmn`, `:bpmn`, `:audit-ledger`),
no `:robotics`.

## Core Contract

```text
operator intake + prior filing/compliance history
        |
        v
Compliance Advisor -> Reconstruction-Zone Compliance Governor -> compliance draft, or human sign-off
        |
        v
gated filing / registration / compliance-program submission + audit ledger
```

No automated proposal can submit a filing or registration the governor
refuses, suppress a compliance record, or claim a legal conclusion the
governor has not cleared. `:filing/submit` is never in any phase's `:auto`
set — it always requires human sign-off (mirrors `cloud-itonami-M6910`'s
`filing-submit-never-auto-at-any-phase` invariant).

## What this is NOT

- **Not Reconstruction Agency (復興庁) itself, and not the
  government of Japan.** See [`docs/business-model.md`](docs/business-model.md)
  for the boundary with `com-etzhayyim-ooyake`, `matsurigoto`,
  `com-etzhayyim-toritsugi`, `legal-entity.etzhayyim.com`,
  `cloud-itonami-M6910`, and the country-level `cloud-itonami-iso3166-jpn`.
- **Not legal or tax advice.** Every regulatory claim must cite the
  official Reconstruction Agency source and route final filings to
  Japan-licensed counsel or a registered agent where the law requires
  licensed representation.

## Regulatory source register (`facts.edn`)

This repository says three times that every requirement it surfaces must cite
an official Reconstruction Agency source — in the boundary note above, in
`docs/operator-guide.md`'s minimum production controls, and in
`docs/business-model.md`, where a fabricated regulatory claim is a HARD hold
that cannot be cleared by human approval alone.

[`facts.edn`](facts.edn) is the set those three requirements point at. Until it
existed there was no such set, so no claim here could be traced to anything and
nothing could tell a real citation from an invented one.

It registers, with the authority for each:

- the **復興特区 regime** — 東日本大震災復興特別区域法 and its Cabinet Order and
  Ordinance, the 復興基本法 above them, 復興庁設置法 and 復興庁組織令, plus the
  Fukushima, large-disaster and 被災市街地 regimes that overlap it;
- the **procurement law the special measures are special relative to** —
  会計法, 予算決算及び会計令, 官公需法, 入札契約適正化法. A zone checklist that
  cites only the reconstruction statutes has omitted the law that governs the
  bid;
- the **Agency pages** an operator is actually sent to, including 調達情報,
  where the Agency publishes its own SME contracting policy;
- one **repealed** ordinance, kept and marked, because an operator working from
  older paperwork will meet that citation.

### Verifying it

```bash
nbb scripts/verify-facts.cljs
```

Re-fetches every entry from the live authority. Three exit codes, and the
third is the point:

| exit | meaning |
|-----:|---------|
| `0` | every entry checked, every entry agreed with the register |
| `1` | the register is wrong about the world — a page moved, a law was repealed |
| `2` | **REFUSED.** This run could not answer. Not a pass. |

A verifier that degrades to unanimous agreement when it cannot reach anything
is worse than none, because the green is indistinguishable from a green that
was earned. So no network, a host whose 404 stops discriminating, a needle
that has migrated into the site chrome, an unreadable register, or a
self-test returning the wrong reason all exit 2 and print REFUSED.

### What measuring these hosts turned up

The verifier is shaped by five things measured on 2026-08-27, each of which
would otherwise have produced a check that passes without asserting anything.
`facts.edn`'s header carries the full account.

- **The citation surface the Agency links to cannot be verified, and it is the
  same host as the one that can.** 復興関係法令 links every statute to
  `elaws.e-gov.go.jp`, a JavaScript SPA that answers `200` with an identical
  800-byte shell for a real law id and a fabricated one. It 301s to
  `laws.e-gov.go.jp` — where `/api/2/law_data/` *does* return `404` for a
  fabricated id. Same domain, opposite answers, separated only by path, so an
  allowlist of trusted authorities gets this wrong.
- **A law id is not derivable from era, year and number.** 東日本大震災復興基本法
  is 平成23年法律第76号, but its id is `423AC1000000076`; the id built the way
  every other Act here is built, `423AC0000000076`, is a 404. The `AC0`/`AC1`
  segment marks cabinet- versus member-submitted, and nothing in the citation
  says which. Every id here was read back, never assembled.
- **A repealed regulation answers `200` with its full text**, and
  `repeal_status` is the *string* `"None"` for a law in force — truthy, so the
  obvious check marks everything repealed and its mirror image marks everything
  live. Publishing a repealed rule as current is precisely the fabricated claim
  `docs/business-model.md` calls a HARD hold.
- **Every deep link the Agency publishes 301s, and the section indexes do not**,
  so neither following redirects nor refusing to follow them is right alone.
  Both the settled URL and the path the Agency still publishes are stored.
- **The published `sitemap.xml` names a host that serves nothing.** All 7,594
  entries point at `reconstruction.r-cms.jp`, the CMS vendor origin, which
  answers `403`. An ingest that harvested the site's only machine-readable
  index would carry a vendor hostname into every compliance record.

### The checks are tested, in both directions

The verifier runs twelve self-tests before it checks anything, each asserting
the *reason* it expects rather than merely that something failed — a negative
test that only asserts failure counts a timeout as a success. A self-test that
returns the wrong reason, including passing, REFUSES the run.

That is not decoration. The first version of this verifier reported every
redirect as changed, because this host sends a *relative* `Location` and it was
being compared against the absolute URL in the register. Ten self-tests were
green; all ten asserted the failure direction. The eleventh — asserting that a
correctly recorded redirect *passes* — is what caught it.

Separately, each check was confirmed to go red for the reason it names by
breaking `facts.edn` twelve ways and checking that the reported reason matched
the break: a fabricated law id, a repealed rule declared live, a needle swapped
for site chrome, a fabricated page URL, a stale title, a via-path landing
elsewhere, an id whose title names a different statute, a register with no
pages, an unreadable register, and three host claims gone stale. The unmodified
register is green.

## Capability layer

Resolves via [`kotoba-lang/iso3166`](https://github.com/kotoba-lang/iso3166)
(code `JPN-RECONSTRUCTION`, `:parent "JPN"`, cross-referenced to ooyake's
`gov.jpn.reconstruction`). Required capabilities:

- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
