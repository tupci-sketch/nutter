# Habnut hotel

Everything that runs habnut.co.uk. Built on a proven trio, branded Habnut:

| Part | What it is | Built from |
|---|---|---|
| Habnut Emulator v1.0 | game server | duckietm/Polaris-Emulator, pinned, `emulator/brand.py` |
| Nutropolis | the roleplay city, an emulator plugin | `emulator/plugins/nutropolis/` |
| NutCMS v1.0 | website and housekeeping | DennisObject/atomcms, pinned, `cms/rebrand.php` + `cms/overlay/` |
| Habnut Client | the hotel in the browser, at `/client/` | duckietm/Octane + Octane-Renderer, pinned, `client/brand.py` |
| imager | avatar pictures for the website, at `/imager/` | duckietm/Polaris-imager, pinned, `imager/build.sh` |

Docker Compose project `hotel` (`compose.yaml`): `db` (MariaDB), `emulator`,
`cms` (PHP-FPM), `imager`, `web` (nginx). Cloudflare's tunnel reaches `web`.

## Where things live on the server

```
/srv/habnut/hotel/.env            database passwords (root only)
/srv/habnut/hotel/atom.env        website settings (root only)
/srv/habnut/hotel/emulator/       habnut-emulator.jar, config.ini, plugins/
/srv/habnut/hotel/client/         the built client
/srv/habnut/hotel/client-config/  its settings (write-client-config.py)
/srv/habnut/hotel/usercontent/    group badges, camera photos, thumbnails
/srv/habnut/hotel/backups/        nightly database dumps, 14 days
/srv/habnut/nitro/                artwork (.nitro bundles), gamedata, images
```

## Everyday commands

From this folder, with `C="sudo docker compose --env-file /srv/habnut/hotel/.env -f compose.yaml"`:

```sh
$C ps                         # what is running
$C logs -f emulator           # game server log
$C restart emulator           # after changing emulator settings or plugins
sudo habnut-hotel-backup      # a backup now (also runs nightly at 04:17 UTC)
```

## Rebuilding a part

```sh
sudo sh emulator/build.sh /srv/habnut/hotel/emulator   # jar + plugins, then restart emulator
sudo sh client/build.sh /srv/habnut/hotel/client        # then rerun write-client-config.py
python3 -I scripts/write-client-config.py /srv/habnut/hotel/client/configuration /srv/habnut/hotel/client-config https://habnut.co.uk
(cd cms && sudo docker build -t habnut-atom:dev .) && $C up -d cms
sudo sh imager/build.sh && $C up -d imager
```

## Rooms

`scripts/gen-public-rooms.py <db-root-password>` writes the SQL for the
Habnut public rooms and the city of Nutropolis (rooms, furniture, jobs),
checking every placement against the floor plan. Safe to run again.

## Nutropolis

`:city` goes there, `:hotel` comes back, `:rphelp` lists the rest. Jobs,
wages, the bank, fights, the hospital, police, arrests and jail. City money
is kept apart from the hotel's currencies, with an append-only ledger
(`habnut_rp_ledger`). Staff: `:setjob <name> <job|none> [rank]`, `:rpreload`
after changing `habnut_rp_jobs` or `habnut_rp_rooms`.
