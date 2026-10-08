#!/usr/bin/env python3
"""Writes the Octane client's runtime configuration for Habnut.

    write-client-config.py <client-dir/configuration> <out-dir> <https://domain>

Starts from the client's own templates and sets every URL to this hotel. The
website (Atom CMS) signs players in and hands the client a ticket, so the
client's own login screen is off.
"""
import json
import os
import re
import shutil
import sys


def load_jsonc(path):
    text = open(path, encoding="utf8").read()
    out, i, n, in_str = [], 0, len(text), False
    while i < n:  # strip comments outside strings
        c = text[i]
        if in_str:
            out.append(c)
            if c == "\\":
                out.append(text[i + 1]); i += 2; continue
            if c == '"':
                in_str = False
            i += 1; continue
        if c == '"':
            in_str = True; out.append(c); i += 1; continue
        if text.startswith("//", i):
            while i < n and text[i] != "\n":
                i += 1
            continue
        if text.startswith("/*", i):
            i = text.index("*/", i) + 2; continue
        out.append(c); i += 1
    clean = re.sub(r",(\s*[}\]])", r"\1", "".join(out))
    return json.loads(clean)


def main():
    src, out, site = sys.argv[1], sys.argv[2], sys.argv[3].rstrip("/")
    os.makedirs(out, exist_ok=True)
    game = f"{site}/emu"
    wss = "wss://" + site.split("://", 1)[1] + "/emu/"
    nitro = f"{site}/nitro"

    r = load_jsonc(os.path.join(src, "renderer-config.json.example.json"))
    r.update({
        "socket.url": wss,
        "api.url": game,
        "asset.url": f"{nitro}/bundled",
        "image.library.url": f"{nitro}/c_images/",
        "hof.furni.url": f"{nitro}/c_images/dcr/hof_furni",
        "images.url": f"{nitro}/images",
        "gamedata.url": f"{nitro}/gamedata",
        "external.texts.url": ["${gamedata.url}/ExternalTexts.json?t=%timestamp%",
                               "${gamedata.url}/UITexts.jsonc?t=%timestamp%"],
        "external.texts.translation.url": "",
        "furnidata.translation.url": "",
        "login.screen.enabled": False,
        "login.turnstile.enabled": False,
        "login.turnstile.sitekey": "",
        "system.log.debug": False,
        "timezone.settings": "Europe/London",
        "loading.logo.url": f"{site}/client/habnut-logo.png",
    })
    json.dump(r, open(os.path.join(out, "renderer-config.json"), "w"), indent=1)

    u = load_jsonc(os.path.join(src, "ui-config.example"))
    # The hotel view's pictures live with the other interface images.
    lv = u.get("loginview", {}).get("images", {})
    for k, v in list(lv.items()):
        if isinstance(v, str):
            lv[k] = v.replace("${asset.url}/c_images/", "${images.url}/")
    u.update({
        "url.prefix": site,
        "habbopages.url": f"{nitro}/habbopages/",
        "camera.url": f"{site}/usercontent/camera/",
        "thumbnails.url": f"{site}/usercontent/camera/thumbnail/%thumbnail%.png",
        "show.google.ads": False,
    })
    text = json.dumps(u, indent=1)
    for prefix in ("${asset.url}/images/", "${asset.url}/c_images/reception/"):
        text = text.replace(prefix, "${images.url}/" + ("reception/" if "reception" in prefix else ""))
    open(os.path.join(out, "ui-config.json"), "w").write(text)

    mode = {
        "distObfuscationEnabled": False,
        "secureAssetsEnabled": False,
        "secureApiEnabled": False,
        "apiBaseUrl": game,
        "plainConfigBaseUrl": f"{site}/client/configuration/",
        "plainGamedataBaseUrl": f"{nitro}/gamedata/",
    }
    json.dump(mode, open(os.path.join(out, "client-mode.json"), "w"), indent=1)

    for name, target in (("hotlooks.example", "hotlooks.json"), ("news.example", "news.json")):
        try:
            json.dump(load_jsonc(os.path.join(src, name)), open(os.path.join(out, target), "w"), indent=1)
        except Exception as e:
            print(f"warn: {name}: {e}")
    # Everything the build put here that is not a template: the generated
    # loader scripts and the ready-made data files.
    written = {"renderer-config.json", "ui-config.json", "client-mode.json", "hotlooks.json", "news.json"}
    for name in os.listdir(src):
        if "example" not in name and name not in written:
            shutil.copyfile(os.path.join(src, name), os.path.join(out, name))
    print("client configuration written to", out)


if __name__ == "__main__":
    main()
