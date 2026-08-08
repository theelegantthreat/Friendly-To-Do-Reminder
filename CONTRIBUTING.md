# Contributing to Friendly To‑Do Reminder

Thank you for your interest in contributing to Friendly To‑Do Reminder! We welcome bug reports, feature requests, documentation updates, and code contributions. This document explains how to contribute and what the GPL‑3.0‑or‑later license means for contributors.

## Important: License for Contributions

This repository is licensed under the GNU General Public License v3.0 or later ("GPL‑3.0‑or‑later"). By contributing to this project (including but not limited to opening issues with patches, submitting pull requests, or committing code), you agree that your contributions will be licensed under GPL‑3.0‑or‑later and that you have the right to grant that license.

What this means in practice:

- Any code you contribute will be covered by the same GPL‑3.0‑or‑later terms as the rest of the repository.
- If your contribution includes code from a third party, you must ensure that the third‑party code is compatible with GPL‑3.0‑or‑later and that you have the right to relicense it under GPL.
- When you submit a PR that results in distributed binaries (for example, an APK), the project maintainers must ensure that the Corresponding Source is made available to recipients according to the GPL. This is normally satisfied by hosting the repository’s source (this repository) and linking to it from release pages or binary distribution channels.

If you cannot license your contribution under GPL‑3.0‑or‑later (for example if your code is under an incompatible license), please open an issue to discuss alternatives before submitting a PR.

## How to Contribute

1. Fork the repository.
2. Create a branch for your change:

   ```bash
   git checkout -b feature/your-feature-name
   ```

3. Write clear, focused commits. Prefer small, reviewable changes.
4. Include tests for new behavior where applicable.
5. Add or update documentation (README, comments) as needed.
6. Run the project checks locally (build, lint, tests):

   ```bash
   ./gradlew assembleDebug
   ./gradlew lint
   ./gradlew test
   ```

7. Push your branch to your fork and open a pull request against the default branch of this repository.

## Pull Request Checklist

Please ensure your PR meets the following before requesting review:

- [ ] The change is licensed under GPL‑3.0‑or‑later (see notes above).
- [ ] The code builds and tests pass locally.
- [ ] You included or updated tests where applicable.
- [ ] You updated documentation if the public behavior changed.
- [ ] You followed the project’s code style and file header guidelines.

## Recommended File Header / SPDX

When adding new source files, please include a short license header. Example for Java/Kotlin:

```java
/*
 * Friendly To-Do Reminder
 * Copyright (C) 2026 <Your Name or Organization>
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
```

Replace the copyright line with your own name or organization when appropriate.

## Third‑Party Dependencies

If your contribution adds a new dependency (library, asset, etc.), ensure the dependency's license is compatible with GPL‑3.0‑or‑later and document the dependency and its license in the PR description.

## Attribution, Copyright, and Patent Grants

Contributing code to this project implies that you have the right to do so (you are the copyright holder or have permission) and that you license your contribution under GPL‑3.0‑or‑later. In addition, GPL‑3.0 contains patent provisions: when you contribute you grant the downstream users the patent license described in the GPL to the extent you hold necessary patent rights.

If you need an alternate contributor agreement (for example, a DCO or explicit CLA), open an issue to discuss — the maintainers will respond.

## Reporting Security Issues

For security issues or vulnerabilities, please do not open a public issue. Instead, contact the repository owner directly or use the GitHub security advisory flow.

## Code of Conduct

Please follow respectful behavior in all interactions. If you'd like, we can add a dedicated CODE_OF_CONDUCT.md; open an issue or PR to propose one.

---

Thanks again for considering a contribution! If you have any questions about licensing or the contribution process, open an issue and tag @theelegantthreat.
