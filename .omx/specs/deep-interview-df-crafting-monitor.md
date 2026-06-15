# Deep Interview Spec: Delta Force Crafting Monitor

## Metadata

- Profile: standard
- Context type: greenfield
- Rounds: 8
- Final ambiguity: 0.111
- Threshold: 0.20
- Context snapshot: `.omx/context/df-crafting-monitor-20260614T230804Z.md`
- Recommended next workflow: `$ralplan`

## Clarity Breakdown

| Dimension | Score | Notes |
| --- | ---: | --- |
| Intent | 0.92 | Reduce the friction of repeatedly opening the official mini-program to check crafting state. |
| Outcome | 0.88 | Android app with four-station crafting monitoring, ongoing notification, per-item completion alerts, and home-screen widget/card. |
| Scope | 0.88 | V1 is narrowly scoped to Android crafting monitoring. Adjacent game-helper features are out. |
| Constraints | 0.88 | Local-first, read-only, no server-side credential custody, no bypass/evasion, low battery impact. |
| Success Criteria | 0.82 | Recommended latency/battery targets accepted; details can be refined in planning and testing. |

## Intent

Build a public-facing Android app that passively monitors Delta Force item crafting status so players do not need to repeatedly enter the official WeChat mini-program to check whether items have completed.

## Desired Outcome

V1 should provide:

- Monitoring for four crafting areas: technology center, workbench, medicine station, and armor station.
- Built-in QQ/WeChat login authorization where required.
- Automatic, read-only retrieval of the user's crafting status through the official mini-program interface path.
- A native Android ongoing notification showing current four-station crafting state.
- Separate completion notifications for individual crafted items.
- A home-screen desktop card/widget for quick monitoring.
- Low battery impact by default, with transparent stale-state/last-updated information.

## In Scope For V1

- Android only.
- Public distribution outside app-store review requirements.
- Local-first monitoring and session custody.
- Built-in QQ/WeChat authorization flow.
- Read-only data retrieval from the official mini-program interface path.
- Adaptive polling/sync on the device.
- Visible, user-controllable foreground notification while monitoring is enabled.
- Per-item completion notifications.
- Widget/card showing current status and last update time.
- Lightweight backend only for non-sensitive configuration, announcements, interface-adaptation metadata, crash diagnostics, or optional telemetry.

## Out Of Scope / Non-goals For V1

- iOS.
- App-store review optimization as a core requirement.
- Server-side login/session custody.
- Server-side polling on behalf of users.
- Game overlay or floating window.
- Trade or price monitoring.
- Multi-account support.
- Social sharing.
- Any automated game operation.
- Cracking, bypassing validation, signature circumvention, hidden abnormal behavior, or evading platform/game risk controls.

Future direction:
- Trade/price monitoring may be considered as a final-version optimization direction, but not in V1.

## Decision Boundaries

The planning/execution agent may decide without further confirmation:

- Android-native architecture and module structure.
- Recommended local-first background strategy.
- Lightweight backend boundaries that avoid sensitive credential/session custody.
- Notification channels, foreground notification UX, widget refresh mechanics, and stale-state indicators.
- Recommended latency/battery defaults within the accepted tradeoff.
- Retry, backoff, reauthorization, and graceful failure behavior.

The agent must ask or stop before:

- Adding server-side custody of QQ/WeChat/game login state.
- Adding server-side user polling.
- Adding features listed as V1 non-goals.
- Implementing bypass, cracking, anti-risk-control evasion, or abnormal stealth behavior.
- Expanding from read-only monitoring into game-changing operations.

## Constraints

- Data access must be read-only.
- Sensitive login/session material stays on the user's device in V1.
- Backend must not store credentials, session tokens, cookies, or other reusable account access material for V1.
- Interface failures must degrade gracefully through retry/backoff, user reauthorization, or waiting for app adaptation.
- Battery consumption must be minimized by default.
- Android background restrictions must be treated as product constraints, not implementation annoyances.
- The ongoing notification must be visible, user-controllable, and stoppable.

## Recommended Acceptance Criteria

### Data And Authorization

- User can complete QQ/WeChat authorization from inside the app.
- App can retrieve and display crafting status for all four target stations after authorization.
- App stores only the minimum necessary local session/auth state and protects it using Android platform storage/security mechanisms.
- App never performs write operations against game/account data.
- If authorization expires or becomes invalid, app clearly prompts reauthorization.

### Monitoring And Notifications

- Ongoing notification displays a concise four-station summary and last successful sync time.
- Individual completion notification is sent when a known crafted item reaches its predicted completion time.
- In foreground monitoring mode, known completion notifications should ideally appear within 0-5 minutes of completion.
- Under Android power restrictions or when exact scheduling is unavailable, fallback tolerance up to about 15 minutes is acceptable for V1.
- Notification channels separate ongoing status from completion alerts.
- Users can pause/stop monitoring from the app and/or notification.

### Widget/Card

- Widget/card shows four-station crafting state, next completion, and last update time.
- Widget/card refreshes after successful sync, app open, manual refresh, and periodic background refresh.
- Default periodic widget/background refresh should be conservative, roughly 15-30 minutes depending on Android constraints.
- Stale data must be visibly labeled instead of pretending to be live.

### Battery And Reliability

- Default mode prioritizes low power over second-level precision.
- Monitoring should use adaptive sync: lower frequency when no items are near completion; higher attention near predicted completion windows where Android allows it.
- Foreground notification mode is acceptable for V1 when monitoring is enabled.
- Optional higher-frequency monitoring may exist only as an explicit user choice.
- App handles process death, reboot, network loss, and auth expiry gracefully.

### Failure And Safety

- On API mismatch, abnormal response, suspected invalid session, or interface change, app backs off and surfaces a clear status.
- Allowed responses: retry with backoff, reauthorize, use updated adapter/config, or wait for compatibility update.
- Disallowed responses: bypass validation, crack signatures, hide abnormal behavior, evade risk controls, or automate gameplay.

## Assumptions Exposed And Resolutions

- Assumption: Public release means product-grade auth/privacy/reliability requirements.
  - Resolution: Accepted as public release, but not dependent on app-store review.

- Assumption: Server-side polling would improve notification timeliness.
  - Resolution: Rejected for V1 because it requires sensitive credential/session custody and increases public-product risk.

- Assumption: Local requests fully avoid limit/session/risk issues.
  - Resolution: Local-first reduces centralized server-IP risk, but does not guarantee equivalence with the official mini-program execution environment. V1 must include graceful failure and reauthorization paths.

- Assumption: Precise real-time notification is required.
  - Resolution: V1 accepts low-power defaults, visible foreground monitoring, and practical delay tolerances.

## Technical Context Findings

- Workspace is currently a greenfield project with no application source files, only `.omx`/`.omc` state directories.
- Android WorkManager periodic work has a 15-minute minimum repeat interval and actual timing can be delayed by constraints/system optimization.
- Android power management can limit jobs, alarms, network access, and related background resources based on app/device state.
- Android 14+ foreground services require appropriate foreground service types; unsuitable background work should use WorkManager or other platform mechanisms.
- Android 14 exact alarm permission is denied by default for most newly installed apps targeting API 33+, so exact scheduling must not be assumed.
- FCM high-priority messages can improve timely delivery but implies a backend push path; V1 avoids backend custody/polling, so FCM is not the core V1 monitoring mechanism unless used only for non-sensitive app-side events.
- Android 13+ requires runtime notification permission for non-exempt notifications.

Evidence URLs:
- https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- https://developer.android.com/topic/performance/power/power-details
- https://developer.android.com/about/versions/14/changes/fgs-types-required
- https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- https://firebase.google.com/docs/cloud-messaging/android-message-priority
- https://developer.android.com/develop/ui/compose/notifications/notification-permission

## Handoff Recommendation

Use `$ralplan` next to produce architecture and test-spec artifacts before implementation. Planning should focus on:

- Android-native stack and project scaffold.
- Auth/session storage boundaries.
- Mini-program interface adapter design.
- Local-first background scheduler and foreground notification design.
- Widget architecture.
- Battery test plan.
- Failure-mode and reauthorization flows.
- Privacy/security model for public distribution outside app stores.

No direct implementation has been performed during this deep-interview phase.
