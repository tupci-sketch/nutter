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
CLIENT = "Habnut Client"
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

print(f"branded as {CLIENT} v{VERSION}")
