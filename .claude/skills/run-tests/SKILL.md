---
name: run-tests
description: Run and interpret Open Car Project's checks — JVM unit tests, build, lint and the instrumented drag test. Use before finishing any code change or when asked to test.
---

# Run tests

```bash
./gradlew test assembleDebug          # always; fast, no device
./gradlew lintDebug                   # before a PR (CI fails on lint errors)
./gradlew connectedAndroidTest        # only if gestures/grid changed; needs a device
```

- Results: `app/build/reports/tests/testDebugUnitTest/index.html`; failures print in the
  Gradle output with the assertion message.
- Unit tests live in `app/src/test/kotlin/org/opencarproject/launcher/`, mirroring the main
  package. Pure logic in `core/pure` must have a test there.
- `connectedAndroidTest` uninstalls the app when done. Reinstall with the `run-on-emulator`
  skill.
- A UI change isn't verified by tests alone: install it and look at a screenshot.
