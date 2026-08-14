# Runbook: EmulatorDown (ALT-001)

**Severity**: Critical  
**Alert**: `EmulatorDown` — `up{job="habnut_emulator"} == 0` for 1 minute

## Symptoms

- Prometheus alert fires
- Players cannot connect or are disconnected
- `/ws` WebSocket endpoint returns 502 from Nginx

## Investigation

```bash
# 1. Check emulator container status
docker compose -f /var/lib/habnut/docker/docker-compose.yml ps emulator

# 2. Check recent logs
habnutctl logs habnut-emulator -n 200

# 3. Check emulator health endpoint directly
curl -sf http://localhost:8080/health

# 4. Check Java process
ps aux | grep habnut-emulator.jar

# 5. Check disk space (OOM can cause Java to crash)
df -h /var/lib/habnut
```

## Common Causes and Fixes

### Out of Memory (OOM)
```bash
# Check kernel OOM killer
dmesg | grep -i oom | tail -20

# If OOM: increase JVM heap in docker-compose.yml
# Change: java -Xmx2g  →  java -Xmx3g
# Then restart:
habnutctl restart
```

### Database Connection Failure
```bash
# Check MariaDB is up
habnutctl status | grep mariadb

# If down:
habnutctl start
```

### Configuration Error
```bash
# Check emulator config
cat /var/lib/habnut/config/emulator.properties

# Revert to known good config and restart
habnutctl restart
```

### Corrupt JAR
```bash
# Roll back to previous version
habnutctl rollback
```

## Escalation

If emulator does not recover within 5 minutes of intervention, run:
```bash
habnutctl doctor --bundle
```
and provide the support bundle to the on-call engineer.
