# Architect Review：三角洲特勤处物品制造监控 APP V1

## Verdict

**APPROVE_WITH_CHANGES**

Native Android local-first is the right favored option, but the plan should not advance past M0 until API/session feasibility is proven. The required changes are gate hardening and boundary precision, not a direction change.

## Steelman Antithesis

The strongest case against native Android local-first is that it may optimize the wrong constraint. If the only usable manufacturing data path is tightly bound to the WeChat mini-program runtime, private cookies, opaque session exchange, or request signing, then a standalone Android app cannot safely or durably reproduce access without violating the project’s red lines against bypass, signing tricks, or simulated non-user authorization. In that world, native Android gives better notifications and widgets, but it does not solve the core custody problem: lawful, stable, user-authorized data access.

A server or official integration could offer stronger reliability, push delivery, and centralized adaptation, but it is correctly rejected for V1 because it would require custody of reusable credentials or official authorization that the plan does not currently have.

## Tradeoff Tension

**Privacy/local custody vs timely monitoring.** Keeping credentials and polling local protects users and avoids a centralized token breach, but Android background limits mean reminders cannot be reliably real-time. WorkManager timing, Doze, foreground service rules, and exact alarm limits force the product to sell "best effort with visible freshness," not "always instant."

## Architecture Findings

| Severity | Finding |
| --- | --- |
| High | M0 must be a true go/no-go gate. Strengthen this into an explicit ADR before M1 starts. |
| High | Auth/session custody is underspecified. Add a credential taxonomy: OAuth code, access token, cookie, mini-program session, game token, expiry hint, refreshability, storage location, and deletion semantics. |
| High | Mini-program API adapter boundary needs to be stricter. It should be an anti-corruption layer with versioned DTOs, fixtures, parser tests, read-only method allowlists, and a kill switch for waiting adaptation. |
| Medium | Background strategy is directionally correct but needs an SLA vocabulary. "0 to 5 minutes" must be documented as a target under stable mode, not a guarantee. |
| Medium | Widget and notification architecture needs a single event model. Add a local event table with dedupe keys and snapshot-driven renderers for both notification and widget. |
| Medium | Privacy boundary is strong, but backend reject behavior should be explicit. Add schema-level rejection for sensitive telemetry and diagnostics fields. |

## Deliberate-Mode Principle Violations

No blocking deliberate-mode violation found.

Partial gaps:

- The plan is still marked as a draft and must not be treated as consensus-complete.
- The PRD should phrase mini-program data access as conditional on M0 passing.

## Required Improvements

1. Add an M0 Go/No-Go ADR: lawful access path, auth/session lifecycle, sample redacted responses, rate-limit behavior, no-bypass proof, and final decision.
2. Add an API adapter contract: `CraftingRemoteDataSource` interface, versioned response DTOs, parser fixtures, error taxonomy, kill switch, and read-only endpoint allowlist.
3. Add a credential custody matrix: credential type, source, storage, expiry, refresh behavior, logging ban, deletion path, and whether it may ever leave device.
4. Add notification/widget event architecture: local snapshot table, completion event table, dedupe key, renderer ownership, reboot behavior, and permission-denied behavior.
5. Reword stable monitoring from "timely" to best-effort targets under Android constraints.
6. Add backend reject rules for telemetry and diagnostics: deny tokens, cookies, headers, raw responses, user manufacturing details, and account identifiers by schema.
7. Add M0-specific tests to the test spec: auth cancellation, session expiry, parser fixture replay, no write requests, no bypass mechanism, and sensitive-field scanner.

## Final Synthesis Path

Keep Option A: native Android local-first. Do not build product UI or full background systems until M0 proves access is both technically feasible and within the stated safety boundary.

Recommended path:

1. M0 first: feasibility spike plus Go/No-Go ADR.
2. If M0 passes: proceed to M1-M5 with the adapter, custody, notification/widget, and privacy improvements above.
3. If M0 fails because access requires bypass or unavailable mini-program runtime: pause the product, preserve local notification/widget architecture, and either wait for an official access path or redefine the product.
