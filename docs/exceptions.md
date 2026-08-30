# Exception Handling Policy

> One page, three rules. They apply to all five modules.
> New cases must adhere to this.

## Rule 1 — Fallback with WARN for preferences and configuration

When a persisted value fails to parse (invalid number, missing key), log a `WARN` with the raw value and continue with the documented default. Never a silent swallow.

```java
} catch (NumberFormatException exception) {
    JSoftSipLog.warn("Invalid registration timeout value '" + raw + "', using default " + DEFAULT);
    return DEFAULT;
}
```

## Rule 2 — Sanitized log for protocol payloads

A corrupted ctrl_tcp or video wire payload is logged with a truncated snippet passed through `sanitizeSecrets`, and the read degrades (return null / drop the frame). Never the raw payload in the log.

## Rule 3 — One single ERROR, at the deciding boundary

If a layer translates the exception into another one (`RepositoryException`) and the boundary consuming it already logs `ERROR` (async executors, `FxExceptionHandler`), the inner layer downgrades its logging to `DEBUG` or does not log at all: ERROR + throw is forbidden when the boundary also logs. If no boundary guarantees a log, the layer that detects the failure logs `ERROR`.

## Cross-cutting hygiene

- Nameless catches are forbidden; deliberate silence is marked with the `ignored` parameter plus a comment explaining why.
- Best-effort fallbacks (unsupported POSIX permissions) are documented in the method javadoc and use named catches.
- Sibling parsers in the same file share criteria; if one differs on purpose, a comment declares it and points out who warns afterwards.
