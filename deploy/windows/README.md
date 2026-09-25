# Windows service deployment

The application is intentionally a normal JVM process with a graceful HTTP shutdown API.
For a native Windows Service wrapper, use a service manager such as WinSW or NSSM and
configure it to launch the same JAR with either `--mode=producer` or `--mode=consumer`.

The graceful application-level shutdown endpoints are:

```text
POST http://127.0.0.1:8080/api/shutdown
POST http://127.0.0.1:8081/api/shutdown
```

This keeps the application independent of the operating-system service manager.
