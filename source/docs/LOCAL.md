# Running a hotel on your own computer

`habnutctl dev` brings a whole hotel up on `127.0.0.1` — the website, the game,
the staff pages, the database — already filled with rooms, furniture and people
to sign in as. Nothing is installed system-wide, nothing needs root, nothing
listens anywhere but your own machine, and one command removes every trace of
it again.

It exists because the alternative was a server. `habnutctl install` wants root,
a domain, a certificate and system packages; none of that is reasonable when
the question is just "does this work?".

## Getting it onto a Linux Mint laptop

The whole thing start to finish. Mint is Ubuntu underneath, so Docker's own
instructions nearly work — the one line that does not is called out below.

**1. Docker.** Mint reports its own codename (`xia`, `wilma`, …) in
`VERSION_CODENAME`, which is not a codename Docker's apt repository knows, so
following Docker's instructions verbatim gives a 404. `UBUNTU_CODENAME` is the
one to use:

```sh
sudo apt-get update
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$UBUNTU_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER
```

Log out and back in — the group change only applies to a new session — then
check with `docker run hello-world`.

**2. The executable.** It is statically linked, so it runs on any x86-64 Linux
whatever its glibc version. Either take the whole repository:

```sh
git clone --depth 1 https://github.com/<owner>/<repo>.git
cd <repo> && chmod +x bin/habnutctl-linux-amd64
```

`--depth 1` matters: the history carries every previous build of the
executables, which is most of the repository's size.

Or take the one file, which is a quarter of that:

```sh
gh api -H "Accept: application/vnd.github.raw" \
  /repos/<owner>/<repo>/contents/bin/habnutctl-linux-amd64 > habnutctl
chmod +x habnutctl
```

**3. Start it.**

```sh
./bin/habnutctl-linux-amd64 dev up
```

## What you need

Docker, and nothing else. The hotel, the website, the game client and the seed
data are all inside the `habnutctl` binary.

- **Windows / macOS** — [Docker Desktop](https://docs.docker.com/desktop/). It
  has to be *running*, not just installed.
- **Linux** — [Docker Engine](https://docs.docker.com/engine/install/), and add
  yourself to the `docker` group so this does not need `sudo`:
  `sudo usermod -aG docker $USER`, then log out and back in.

Docker is the one thing that cannot be shipped in a binary. A database, a
cache, PHP and a JVM are four separate installs otherwise, and getting them to
agree with each other is exactly the work this is meant to save you.

## Starting it

```
habnutctl dev up
```

The first run fetches container images, builds the website's image and builds
the database, which takes a few minutes. After that it is seconds. When it finishes it prints where
everything is:

```
The hotel is up.

  The hotel      http://127.0.0.1:8088
  The game       http://127.0.0.1:8088/hotel
  Staff pages    http://127.0.0.1:8088/dcc
  Mail it sends  http://127.0.0.1:8025

  Sign in with any of these. The password is: password
    tupci    Administrator  owns the public rooms and can open the staff pages
    Hal      Moderator      can see the moderation queue
    Marnie   VIP            owns a furnished room of her own
    Robbie   Member         an ordinary account, to see what a new player sees
```

Open the first URL, sign in as `tupci`, and click through to the hotel. You
will not be asked for a ticket: signing in on the website is what gets you into
the game.

## What is in it

The hotel comes up seeded, because an empty hotel tells you nothing about
whether it works.

**Everywhere:** seven room shapes, eighteen pieces of furniture covering every
behaviour the room engine knows about — something to sit on, something to stand
on, a gate that opens, a lamp with three settings, dice, a teleport pad, a
roller — and a catalogue with all of it in it, in six pages.

**Only locally:** four accounts, seven furnished rooms, friendships between the
accounts, items in `tupci`'s inventory, and a thread on the forum. Four of the
rooms are in the hotel (a lobby, a grand hall, somebody's front room, a pool)
and three in the roleplay city (a city hall, a precinct, a pawn shop), because
a room belongs to one world and the navigator only shows the world you are in
— so `--rp` needs its own.

The demo accounts all share one password and exist only on a hotel running on
your own machine. `habnutctl install` never creates them.

To come up empty instead and register an account yourself:

```
habnutctl dev up --no-demo
```

## Without an asset pack

The game runs, but rooms and figures draw as plain coloured shapes: the
pictures come from an asset pack, which is not ours to ship. Everything else
works — walking, chat, furniture, the catalogue, the navigator, the website,
the staff pages.

On the website, figures show as a monogram rather than a broken image: a local
hotel has no imager running and nothing for one to draw from, so it is not
asked for pictures at all.

### Adding one

```
habnutctl swf install <pack.zip-or-directory> --local --era classic
```

`--local` installs into the hotel in your home directory rather than into a
system install, so it needs no root. A `.zip` or a plain directory both work,
and the layout inside does not matter: the data files are found by name and
the sprites by extension, wherever the pack happens to put them.

Restart the game page afterwards — the local web server serves the artwork
uncached, so a reinstall shows up on a refresh.

`--era modern` installs a second set alongside, and a player can switch
between them in-game without leaving the room.

A pack lands in two places inside the hotel's directory: `swf/` holds it as
it arrived, and `assets/` holds the sprites extracted from it, which is what
the web server serves. Both are deleted along with everything else by
`habnutctl dev down --purge`, and together they roughly double the pack's size
on disk.

### Pictures on the website

The website renders figures through the imager rather than the game client.
To turn it on, run `habnutctl imager` beside the hotel and set `IMAGER_URL` in
`cms/.env` to where it is listening. Until then the site shows a monogram,
which is also what it does when a pack is installed but the imager is not
running.

## The other commands

| Command | What it does |
| --- | --- |
| `habnutctl dev up` | Start it. Safe to run on a hotel that is already up. |
| `habnutctl dev down` | Stop it. The database is kept, so `up` resumes where you left off. |
| `habnutctl dev down --purge` | Stop it and delete the database. |
| `habnutctl dev status` | Whether it is running, and where to find it. |
| `habnutctl dev logs` | What it is saying. `-f` to follow, or name one service. |
| `habnutctl dev reset` | Throw the hotel away and build it again from nothing. |
| `habnutctl dev seed` | Apply the seed files again, keeping whatever you changed. |
| `habnutctl dev db` | A database prompt, without installing a client. |

The services are `db`, `redis`, `mail`, `emulator`, `cms` and `web`, so
`habnutctl dev logs -f emulator` follows the hotel itself.

## Where it keeps things

One directory holds the lot:

| Platform | Directory |
| --- | --- |
| Linux | `~/.habnut/dev` |
| macOS | `~/Library/Application Support/Habnut/dev` |
| Windows | `%LOCALAPPDATA%\Habnut\dev` |

Set `HABNUT_DEV_HOME` or pass `--dir` to put it somewhere else. Inside:

```
docker-compose.yml   the stack — yours to edit
Dockerfile.cms       the website's image
nginx.conf           the web server
app.key              this hotel's signing key
cms/                 the website, with its .env
client/              the game client
emulator/            habnut-emulator.jar
seed/                base.sql and demo.sql — yours to edit
logs/
```

The website's image is built rather than pulled: the official PHP images do
not carry `pdo_mysql`, and the tools to compile it are dropped from them, so
installing it at container start would mean fetching a compiler on every
start — slow, and it fails outright with no network. It is built once on the
first run and cached after that.

The generated files are yours once they exist: `habnutctl dev up` will not
overwrite a compose file you have added a service to. Pass `--recreate` when
you want them rewritten.

The seed files work the same way. Edit `seed/demo.sql`, run `habnutctl dev
seed`, and your changes are applied. Nothing already in the database is
overwritten, so a hotel you have been playing with keeps what you did to it.

## Running two at once

Give them names and ports:

```
habnutctl dev up --name classic --port 8088
habnutctl dev up --name rp --port 8089 --rp
```

Both come up seeded for their own world: the hotel's navigator and catalogue
show the hotel's rooms and furniture, the city's show the city's, and a page
or room marked for both appears in both.

`--rp` sets the world on both halves — the hotel runs as the city and the site
issues tickets for it — because the world travels on the handover ticket, so
setting it on only one would run the city and send everybody to the hotel. A
player can still ask for the other by name: `/hotel?world=classic`.

Each gets its own directory, its own database and its own Compose project, so
neither adopts the other's containers.

## Mail

Everything the hotel sends — registration, password resets, ban notices — is
caught by a local inbox at `http://127.0.0.1:8025`. Nothing leaves the machine,
and you can follow a password reset all the way through without a mail server.

## When something goes wrong

**It says Docker is not available.** Installed is not the same as running: on
Windows and macOS the engine lives in a VM that has to be started. Open Docker
Desktop and wait for it to say it is running.

**A port is already in use.** `--port` moves the website. The others move with
`--name`, which gives the whole hotel its own set.

**Building the website's image failed.** It compiles PHP extensions, so it
needs network on the first run. Build it by hand to see the error in full:
`docker compose -f ~/.habnut/dev/docker-compose.yml build cms`.

**The database never came up.** `habnutctl dev logs db`. On a first run it
builds its data directory, which can take a minute on a slow disk; the start-up
wait allows three.

**The website shows an error page.** It is running with debugging on, so the
page says what went wrong and where. `habnutctl dev logs cms` has the rest.

**The game opens but nothing is drawn.** If the hotel is up and the room is
still blank, you have no asset pack — see above. `habnutctl dev up` will not
report success unless the hotel answered, so a running hotel really is running.

**The client cannot connect.** `habnutctl dev logs emulator`, then
`habnutctl dev logs web` — the game reaches the hotel through the web server,
so it is one of those two.

**You want to start completely clean.** `habnutctl dev reset`.

## Differences from a real install

Everything about how the parts fit together is the same — the website at `/`,
the game at `/client/`, the socket at `/ws`, one account across both, the same
schema applied by the same migrator. What differs is everything that needs a
server:

| | Local | Installed |
| --- | --- | --- |
| Reachable from | this machine only | the internet |
| TLS | none | Let's Encrypt |
| Debugging | on | off |
| Mail | caught locally | your SMTP server |
| Services | Docker containers | systemd or Windows services |
| Starts at boot | no | yes |
| Demo accounts | yes | never |

A change that works here is a change that will work there. It is not a hotel
you should put players on.
