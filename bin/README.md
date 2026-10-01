# Executables

The whole hotel, as one file per platform.

| File | For |
|------|-----|
| `habnutctl-linux-amd64` | A Linux server, 64-bit |
| `habnutctl-windows-amd64.exe` | A Windows server, 64-bit |

Each carries the emulator, the client bundle and the CMS inside it, so a fresh
machine needs nothing installed beforehand.

## On your own computer

```bash
chmod +x habnutctl-linux-amd64
./habnutctl-linux-amd64 dev up
```

Brings the whole hotel up on `127.0.0.1`, seeded with rooms, furniture and four
accounts, and prints where to find it. No root, no domain, no certificate.
Docker is the only thing it needs that is not in the binary.
`../source/docs/LOCAL.md` has the rest.

## On a server

```bash
chmod +x habnutctl-linux-amd64
sudo ./habnutctl-linux-amd64 install
```

That runs the installer end to end — system user, Java, MariaDB, Redis, Nginx,
PHP, the database migrations, the services and a TLS certificate — and leaves a
hotel running. Afterwards the same binary is how you run it:

| Command | Does |
|---------|------|
| `dev up` / `dev down` | A hotel on this computer, for looking at |
| `dev status` / `dev logs` / `dev reset` / `dev seed` / `dev db` | Running it |
| `install` | Set up a hotel on a fresh machine |
| `update` | Update in place, rolling back on failure |
| `start` / `stop` / `restart` / `status` | The services |
| `swf install <pack.zip> --era modern` | Install artwork |
| `imager` | Serve avatar and badge pictures |
| `backup` / `restore` / `rollback` | The database and the asset pack |
| `doctor` | Check a running hotel and write a support bundle |
| `logs` | Follow what the services are saying |

Run `habnutctl <command> --help` for the flags on any of them.

## Building these yourself

```bash
source/scripts/build-launcher.sh          # both platforms
source/scripts/build-launcher.sh linux    # one
```

The script builds the emulator JAR, the client bundle and a production CMS tree,
packs them into the binary, and writes the result back here.

## What is not in here

Artwork. The hotel needs a SWF asset pack, which you supply and install with
`habnutctl swf install`. Nothing about a pack is distributed with these
binaries.
