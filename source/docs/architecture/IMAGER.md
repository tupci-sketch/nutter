# Avatar and Badge Imager

The imager turns a figure string into a picture. It runs inside `habnutctl`, so
a hotel gets it from the same binary that installs and runs everything else:

```
habnutctl imager --listen 127.0.0.1:8081 --era modern
```

The installer writes a `habnut-imager` unit that runs exactly that, and Nginx
proxies it at `/imager/`.

## Why the hotel renders its own

A figure string such as `hd-180-1.hr-828-92.ch-210` describes an avatar but is
not a picture, and the website needs pictures in many more places than the game
client: profiles, staff lists, forum posts, the news, moderation tools. The
usual arrangement is to point those `<img>` tags at somebody else's renderer.
That works until the day it does not, and then every avatar on the site breaks
at once, including in the moderation tools that staff need most when something
has gone wrong.

Rendering here means the pictures come from the same asset pack the client
draws from, so the website and the hotel always agree, and nothing outside the
server has to be reachable for a profile page to load.

## How a figure becomes a picture

1. **Parse the figure string.** Each segment names a slot, a set within that
   slot, and the colours chosen for it. A malformed segment is dropped rather
   than failing the figure, because figure strings arrive from the database and
   from other players.
2. **Look up each set in `figuredata.xml`.** A set expands to one or more parts,
   each with its own type, draw index, and whether it is colourable.
3. **Find each part's library in `figuremap.xml`.** A part the pack has no
   library for is skipped: a missing hairstyle should cost that hairstyle, not
   the whole avatar.
4. **Build the sprite name**, in the form the pack uses:
   `{library}_{size}_{action}_{type}_{partId}_{direction}_{frame}`, for example
   `hh_human_hair_h_std_hr_828_2_0`.
5. **Sort back to front.** `figuredata` orders parts within one set only, so a
   separate table decides which slot draws over which — the difference between
   a face drawn over hair and hair drawn over a face.
6. **Composite**, applying the tint, the offsets, and any mirroring.

## Three rules that are easy to get wrong

**Directions 4, 5 and 6 do not exist in a pack.** They are 2, 1 and 0 mirrored.
Anything that renders them literally leaves a quarter of the compass blank.

**Mirroring reflects about the avatar's origin, not the part's own width.**
Every part turns about the same axis, which keeps the figure together. Flipping
each part about its own box instead slides each one by however wide it happens
to be, which pulls the avatar apart in exactly the directions that are hardest
to notice in a screenshot. A pack that already stores a sprite mirrored and a
direction that needs mirroring cancel out, so the two combine exclusively.

**Only colourable parts are tinted.** Colourable parts ship greyscale and take
their colour from the figure string; the part's colour index picks which of the
figure's colour slots applies, and the colour id is looked up in the palette
belonging to that slot's set type — the same id means different things for hair
and for a shirt. A part that is not colourable already carries its own colours
and must be left alone.

## Group badges

A group's badge is not stored as a picture either. It is a code such as
`b03120s13181`, read as a letter and five digits per part: two digits of part
id, two of colour, one of position.

| Letter | Meaning |
|--------|---------|
| `b`    | Base shape, drawn first and filling the badge |
| `s`    | Symbol, part ids 1–99 |
| `t`    | Symbol, continuing the numbering from 100 |

Position is a cell of a three-by-three grid, `0` top-left through `8`
bottom-right. Parts too large for a cell are centred on the badge instead.

Packs differ in whether a part ships once per colour or once in greyscale to be
tinted, so both are tried. A `badgeparts.xml` in the pack root, when present,
declares the sprite names and the colour palette; without one the usual naming
is assumed.

The same route serves achievement badges, which are named rather than coded,
because callers hold both kinds in one database column and cannot tell them
apart.

## HTTP interface

| Route | Purpose |
|-------|---------|
| `GET /avatar.png` | Render a figure |
| `GET /badge/{code}.png` | Render a group badge code or a named badge |
| `GET /health` | Liveness |

Avatar parameters:

| Parameter | Default | Meaning |
|-----------|---------|---------|
| `figure` | required | The figure string |
| `direction` | `2` | Which way the body faces, 0–7 |
| `head_direction` | `direction` | Which way the head faces |
| `action` | `std` | `std`, `wlk`, `sit`, `lay`, `wav`, `respect`, `blow` |
| `frame` | `0` | Frame within an animated action |
| `size` | `n` | `n` for the room-sized figure, `s` for the small head |
| `headonly` | `0` | Draw the head alone |
| `scale` | `1` | Whole-number pixel multiplier, up to 4 |
| `era` | server default | `classic` or `modern` |

Anything that changes the picture is part of the URL, so responses carry a long
`Cache-Control` and an `ETag`, and a browser that already holds the image gets a
`304`. Rendered images are also kept in memory, least-recently-used first,
because profile pictures are the most repeated request a hotel's site serves.

Waving, respecting and blowing a kiss change the body only. Packs ship no head
sprite for those actions, so head parts fall back to standing rather than
disappearing.

## Eras

Both visual eras are served from one process. An era's pack is loaded the first
time something asks for it, so installing the second era does not need a
restart, and a request naming an era with no artwork gets a `503` rather than a
broken picture.

## Failure behaviour

- A part the pack lacks is skipped; the rest of the avatar still renders.
- A figure with no drawable parts at all is a `404`, not an empty image.
- A sprite path that would leave the pack directory is refused. Packs are
  untrusted input.
- An alias chain is followed to a fixed depth, so a pack that points a sprite
  back at itself cannot hang a request.
- With no imager configured, the CMS returns no URL and its views draw a
  monogram, so a hotel mid-setup still shows complete pages.
