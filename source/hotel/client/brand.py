#!/usr/bin/env python3
"""Makes Octane (client) and Octane-Renderer checkouts into the Habnut client.

    brand.py <octane-checkout> <octane-renderer-checkout> <icon.png>

Changes what players see: the page title and icons, the loading screen's
logo text, the about box, the console greeting and log prefix, and the
names of the script files the browser downloads. Component and package names
stay upstream's. Fails loudly if upstream moved something.
"""
import pathlib
import re
import shutil
import sys

NAME = "Habnut"
CLIENT = "Nutty"
VERSION = "1.0"
SITE = "https://habnut.co.uk"

ui, renderer, icon = (pathlib.Path(a) for a in sys.argv[1:4])


def edit(path, pairs):
    s = path.read_text(encoding="utf8")
    for old, new in pairs:
        if isinstance(old, re.Pattern):
            s, n = old.subn(new, s)
            if not n:
                sys.exit(f"{path}: upstream changed, no match for {old.pattern}")
            continue
        if old not in s:
            sys.exit(f"{path}: upstream changed, cannot find:\n{old[:200]}")
        s = s.replace(old, new)
    path.write_text(s, encoding="utf8")


# The page itself. The website signs people in, so the client never shows
# Cloudflare's challenge and need not load it.
edit(ui / "index.html", [
    ("<title>Octane</title>", f"<title>{NAME}</title>"),
    (re.compile(r'\s*<!-- Connection hints.*?</script>', re.S), ""),
])
# The page the build actually ships is written by this script.
edit(ui / "scripts/minify-dist.mjs", [
    ('<script async defer src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit"></script></head>',
     f'<title>{NAME}</title><link rel="icon" type="image/png" href="favicon-32x32.png"><link rel="manifest" href="site.webmanifest"></head>'),
])
edit(ui / "public/site.webmanifest", [('"name": "Nitro"', f'"name": "{NAME}"'), ('"short_name": "Nitro"', f'"short_name": "{NAME}"')])
for name in ("favicon.ico", "favicon-16x16.png", "favicon-32x32.png", "apple-touch-icon.png",
             "android-chrome-192x192.png", "android-chrome-512x512.png", "mstile-150x150.png"):
    shutil.copyfile(icon, ui / "public" / name)

edit(ui / "src/components/loading/LoadingView.tsx", [('alt="Octane"', f'alt="{NAME}"')])

edit(ui / "src/components/notification-center/views/alert-layouts/OctaneInfoAlertView.tsx", [
    ("'https://github.com/duckietm/Octane/issues'", f"'{SITE}/help-center'"),
])

about = ui / "src/components/notification-center/views/alert-layouts/OctaneSystemAlertView.tsx"
s = about.read_text(encoding="utf8")
start = s.index("    return (\n")
about.write_text(s[:start].replace("title = 'Octane'", f"title = '{NAME}'") + f'''    return (
        <LayoutNotificationAlertView title={{title}} onClose={{onClose}} classNames={{['octane-alert-system', ...classNames]}} {{...rest}}>
            <Grid>
                <Column size={{12}}>
                    <Column alignItems="center" gap={{0}}>
                        <Text bold fontSize={{4}}>
                            {CLIENT}
                        </Text>
                        <Text>v{VERSION}</Text>
                    </Column>
                    <Column alignItems="center">
                        <Text>Running on Habnut Emulator.</Text>
                        <Column fullWidth gap={{1}}>
                            <Button fullWidth variant="success" onClick={{() => window.open('{SITE}/help-center')}}>
                                Help
                            </Button>
                            {{adsEnabled && (
                                <Button fullWidth onClick={{() => window.dispatchEvent(new CustomEvent('ads:toggle'))}}>
                                    Show Ad
                                </Button>
                            )}}
                        </Column>
                    </Column>
                    <Column alignItems="center" gap={{0}}>
                        <Text center small>Made by the Habnut team.</Text>
                    </Column>
                </Column>
            </Grid>
        </LayoutNotificationAlertView>
    );
}};
''', encoding="utf8")
# The versions it no longer shows.
edit(about, [("GetRendererVersion, GetUIVersion, ", "")])

edit(ui / "src/components/wired-tools/WiredToolsSettingsTabView.tsx", [("['Octane']", f"['{NAME}']")])

# Script files: habnut-*.js rather than octane-renderer-*.js.
vite = ui / "vite.config.mjs"
edit(vite, [(re.compile(r"return 'octane-renderer(-[a-z]+)?';"), lambda m: m.group(0).replace("octane-renderer", "habnut-renderer"))])

edit(renderer / "packages/utils/src/OctaneLogger.ts", [("return '[Octane]';", f"return '[{NAME}]';")])
edit(renderer / "packages/utils/src/OctaneVersion.ts", [
    ("`\\n %c  OCTANE  %c  UI ${OctaneVersion.UI_VERSION}  %c  Renderer ${OctaneVersion.RENDERER_VERSION}  %c \\n`",
     f"`\\n %c  {NAME.upper()}  %c  {CLIENT} v{VERSION}  %c  {SITE.split('://')[1]}  %c \\n`"),
    ("`Octane UI ${OctaneVersion.UI_VERSION} - Renderer ${OctaneVersion.RENDERER_VERSION}`", f"`{CLIENT} v{VERSION}`"),
])

# ---- Habnut's own pictures, type and words -------------------------------------

HERE = pathlib.Path(__file__).resolve().parent
KIT = HERE.parent / "brand" / "out"
FONTS = HERE.parent / "fonts"
src = ui / "src"
sources = [p for p in src.rglob("*") if p.suffix in (".ts", ".tsx", ".css", ".scss")
           and ".test." not in p.name and "__tests__" not in p.parts]


def everywhere(old, new):
    """Replaces a path or name in every source file that mentions it."""
    for p in sources:
        s = p.read_text(encoding="utf8")
        if old in s:
            p.write_text(s.replace(old, new), encoding="utf8")


def swap_image(old_rel, new_rel, picture):
    """Puts a Habnut picture where an original was, under a Habnut name."""
    old, new = src / "assets/images" / old_rel, src / "assets/images" / new_rel
    if not old.exists():
        sys.exit(f"upstream moved {old_rel}")
    shutil.copyfile(KIT / picture, new)
    if old != new:
        old.unlink()
        everywhere(old_rel, new_rel)
        # the folder's own index imports it relative to itself
        everywhere("./" + old.name, "./" + new.name)


swap_image("toolbar/icons/habbo.png", "toolbar/icons/habnut.png", "icon-28.png")
swap_image("toolbar/air/logo.png", "toolbar/air/logo.png", "icon-28.png")
swap_image("loading/octane-logo.png", "loading/habnut-logo.png", "logo-large.png")
swap_image("reward-track/hint-frank.png", "reward-track/hint-nutty.png", "hint-nutty.png")
swap_image("user-profile/frank-stop.png", "user-profile/stop-nutty.png", "stop-nutty.png")
swap_image("mysterytrophy/frank_mystery_trophy.png", "mysterytrophy/nutty_mystery_trophy.png", "trophy-nutty.png")
for view in ("hotelview.png", "hotelview-morning.png", "hotelview-night.png", "hotelview-sunset.png"):
    swap_image(f"hotelview/{view}", f"hotelview/{view}", "hotelview.png")

# Type: Nunito for text and Pixelify Sans for pixel text, in place of the
# original UI and pixel fonts, which are removed so nothing can still use them.
webfonts = src / "assets/webfonts"
for name, file in (("HabnutSans.woff2", "nunito-latin-400-normal.woff2"), ("HabnutSans-b.woff2", "nunito-latin-700-normal.woff2"),
                   ("HabnutSans-x.woff2", "nunito-latin-800-normal.woff2"), ("HabnutSans-i.woff2", "nunito-latin-400-italic.woff2"),
                   ("HabnutPixel.woff2", "pixelify-sans-latin-400-normal.woff2"), ("HabnutPixel-b.woff2", "pixelify-sans-latin-700-normal.woff2")):
    shutil.copyfile(FONTS / file, webfonts / name)
for old, new in (("Ubuntu-bt.ttf", "HabnutSans-x.woff2"), ("Ubuntu-ib.ttf", "HabnutSans-b.woff2"), ("Ubuntu-b.ttf", "HabnutSans-b.woff2"),
                 ("Ubuntu-i.ttf", "HabnutSans-i.woff2"), ("Ubuntu-C.ttf", "HabnutSans.woff2"), ("Ubuntu-m.ttf", "HabnutSans.woff2"),
                 ("Ubuntu.ttf", "HabnutSans.woff2"), ("Volter-b.ttf", "HabnutPixel-b.woff2"), ("Volter.ttf", "HabnutPixel.woff2"),
                 ('local("Ubuntu Condensed"), local("Ubuntu")', 'url("@/assets/webfonts/HabnutSans.woff2")'),
                 ("HabboAirUbuntu", "HabnutAirSans")):
    everywhere(old, new)
for p in sources:
    s = p.read_text(encoding="utf8")
    t = re.sub(r"(Habnut(?:Sans|Pixel)[-a-z]*\.woff2['\"]\)\s*)format\(\s*['\"]truetype['\"]\s*\)", r"\1format('woff2')", s)
    if t != s:
        p.write_text(t, encoding="utf8")
for f in webfonts.glob("*.ttf"):
    f.unlink()
shutil.rmtree(webfonts / "org fonts", ignore_errors=True)

# Words: every sentence the client says, under the one text rule.
sys.path.insert(0, str(HERE.parent / "scripts"))
from habnut_brand import rebrand  # noqa: E402

LITERAL = re.compile(r"""(['"`])((?:\\.|(?!\1)[^\\\n])*?(?:Habb|Nitro)(?:\\.|(?!\1)[^\\\n])*?)\1""")
JSX_TEXT = re.compile(r"(?<=[>}])([^<>{}]*(?:Habb|Nitro)[^<>{}]*)(?=[<{])")
def readable(text):
    """A sentence or a plain word someone reads, not a path, file name, key or identifier."""
    plain = re.sub(r"\$\{[^}]*\}", "", text.replace("\\'", "'").replace('\\"', '"'))
    if "/" in plain or "\\" in plain or re.search(r"\.[A-Za-z]{2,5}$", plain):
        return False
    return " " in plain.strip() or re.fullmatch(r"(?:Habb[a-z]+|Nitro)[!?.]?", plain.strip()) is not None


def reword(text):
    """The text rule, applied outside ${...} so code inside a template is never touched."""
    return "".join(part if part.startswith("${") else rebrand(part) for part in re.split(r"(\$\{[^}]*\})", text))


worded = 0
renderer_sources = [p for p in renderer.rglob("*.ts") if "node_modules" not in p.parts and ".test." not in p.name]
for p in sources + renderer_sources:
    if p.suffix not in (".ts", ".tsx"):
        continue
    s = p.read_text(encoding="utf8")
    t = LITERAL.sub(lambda m: m.group(1) + (reword(m.group(2)) if readable(m.group(2)) else m.group(2)) + m.group(1), s)
    if p.suffix == ".tsx":  # in plain .ts, >...< is a generic type, not text
        t = JSX_TEXT.sub(lambda m: rebrand(m.group(1)) if " " in m.group(1).strip() else m.group(0), t)
    if t != s:
        p.write_text(t, encoding="utf8")
        worded += 1
print(f"Habnut pictures, type and words: {worded} files reworded")

print(f"branded as {CLIENT} v{VERSION}")
