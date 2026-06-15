# Deep Interview Context Snapshot: df-crafting-monitor

Task statement:
- Build a greenfield monitoring app for Delta Force item crafting status.
- Monitor four crafting stations: technology center, workbench, medicine station, and armor station.
- Enhance the official WeChat mini-program behavior, which requires entering the mini-program view to inspect status.

Desired outcome:
- A player-facing app that can monitor all four crafting categories in near real time.
- Android native notification bar support for ongoing crafting status.
- Separate notification when a single crafted item completes.
- A desktop/home-screen card or widget for real-time monitoring.

Stated solution:
- Reverse or inspect the official mini-program API to obtain read-only account crafting data after QQ/WeChat authorization.
- Implement native Android notifications and background monitoring.
- Prevent the Android background task from being killed as much as possible.
- Minimize battery consumption while maintaining useful monitoring.

Probable intent hypothesis:
- Reduce the friction of repeatedly opening the official mini-program just to check crafting completion.
- Provide timely, passive awareness through notifications and a widget without affecting gameplay fairness.

Known facts/evidence:
- User describes this as a greenfield project.
- Workspace currently contains no application source files, only `.omx`/`.omc` state directories.
- The app is intended to be read-only and not modify game/account data.

Constraints:
- Requires account-specific data, likely involving QQ and WeChat authorization.
- User believes the API approach is safe and legal, and wants the app limited to read-only convenience.
- Android background execution, notification reliability, and battery use are core constraints.
- No implementation should begin during deep-interview mode.

Unknowns/open questions:
- Target platform stack: native Android, Flutter, React Native, Kotlin Multiplatform, or another approach.
- Minimum Android version and distribution path.
- Whether this is a personal-use tool, closed beta, or public app.
- Data polling interval, latency expectations, and acceptable missed-notification behavior.
- Exact meaning and design of the desktop card/widget.
- Authentication/token storage and user privacy/security requirements.
- Legal/compliance boundary for reverse-engineered mini-program interfaces.

Decision-boundary unknowns:
- Which technical decisions the agent may make without confirmation.
- Which choices require explicit user approval, especially auth flow, background strategy, release/distribution, and API reverse-engineering scope.

Likely codebase touchpoints:
- None yet. Greenfield architecture and project scaffold remain undecided.

Prompt-safe initial-context summary status:
- not_needed
