# Deployment Guide

## Prerequisites

- Ubuntu 22.04 or 24.04 LTS
- Docker 24+, Docker Compose v2
- A registered domain name with DNS A record pointing to the server
- Ports 80 and 443 open in firewall

## Fresh Installation

```bash
# Download and install habnutctl
curl -fsSL https://releases.habnut.local/habnutctl/latest/install.sh | bash

# Run the 28-step installer
habnutctl install \
  --domain yourdomain.example \
  --email admin@yourdomain.example \
  --db-pass $(openssl rand -base64 32)
```

The installer handles:
1. Dependency verification (Docker, Java, Go, Node, PHP)
2. Directory structure creation under `/var/lib/habnut`
3. Secret generation (DB passwords, app key, Grafana password)
4. Docker Compose stack deployment
5. Flyway migrations V1–V8
6. TLS certificate issuance (Let's Encrypt via Certbot)
7. Nginx config deployment
8. Prometheus and Grafana provisioning
9. State file creation at `/var/lib/habnut/state.json`

## SWF Asset Pack

```bash
# Install asset pack
habnutctl swf install /path/to/pack.zip

# Apply branding
habnutctl swf rebrand Habnut

# Validate
habnutctl swf validate

# Check status
habnutctl swf status
```

## Updating

```bash
habnutctl update
```

The 13-step updater will:
1. Back up the current emulator JAR
2. Download the new JAR
3. Run new Flyway migrations
4. Perform a rolling restart

If any step fails, the updater automatically restores the backup JAR and restarts.

## Health Check

```bash
habnutctl doctor
```

Reports on all 11 health checks: binaries, services, emulator HTTP health,
state file, and disk space.

## Backup and Restore

```bash
# Create backup
habnutctl backup

# Restore from backup
habnutctl restore /var/lib/habnut/backups/backup-20240101-120000.sql.gz
```

## Rollback

```bash
habnutctl rollback
```

Restores the most recent backup JAR and restarts the emulator.

## Logs

```bash
# Tail emulator logs
habnutctl logs -f

# Tail a specific service
habnutctl logs nginx -f
habnutctl logs mariadb -f
```

## Service Management

```bash
habnutctl start
habnutctl stop
habnutctl restart
habnutctl status
```

## Monitoring

- **Prometheus**: http://localhost:9090 (internal only)
- **Grafana**: http://localhost:3000 (internal only)
- **Loki**: http://localhost:3100 (internal only)
- **AlertManager**: http://localhost:9093 (internal only)

Expose these via SSH tunnel or a separate authenticated reverse proxy.
