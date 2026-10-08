#!/usr/bin/env python3
"""Makes a Polaris checkout into Habnut Emulator.

    brand.py <polaris-checkout>

Changes only what people see: the version players get from :about, the
emulator command's reply, the staff dashboard, the boot banner and log lines.
Class names, packages and the plugin API stay exactly as upstream wrote them,
so Arcturus/Polaris plugins keep loading unchanged. Fails loudly if upstream
moved something, rather than building a half-branded server.
"""
import pathlib
import re
import sys

NAME = "Habnut Emulator"
VERSION = "1.0"
SITE = "https://habnut.co.uk"

root = pathlib.Path(sys.argv[1]) / "Emulator/src/main/java/com/eu/habbo"


def edit(rel, pairs):
    p = root / rel
    s = p.read_text(encoding="utf8")
    for old, new in pairs:
        if old not in s:
            sys.exit(f"{rel}: upstream changed, cannot find:\n{old[:200]}")
        s = s.replace(old, new)
    p.write_text(s, encoding="utf8")


def literals(rel, fn):
    """Applies fn to the inside of every Java string literal in a file."""
    p = root / rel
    s = p.read_text(encoding="utf8")
    p.write_text(re.sub(r'"((?:\\.|[^"\\\n])*)"', lambda m: '"' + fn(m.group(1)) + '"', s), encoding="utf8")


def box(text, width):
    return "| " + text.ljust(width - 4) + " |"


HABNUT_BLOCK = (
    '"\\n" + "██╗  ██╗ █████╗ ██████╗ ███╗   ██╗██╗   ██╗████████╗\\n"\n'
    '            + "██║  ██║██╔══██╗██╔══██╗████╗  ██║██║   ██║╚══██╔══╝\\n"\n'
    '            + "███████║███████║██████╔╝██╔██╗ ██║██║   ██║   ██║   \\n"\n'
    '            + "██╔══██║██╔══██║██╔══██╗██║╚██╗██║██║   ██║   ██║   \\n"\n'
    '            + "██║  ██║██║  ██║██████╔╝██║ ╚████║╚██████╔╝   ██║   \\n"\n'
    '            + "╚═╝  ╚═╝╚═╝  ╚═╝╚═════╝ ╚═╝  ╚═══╝ ╚═════╝    ╚═╝   \\n"\n'
    f'            + "{NAME} v{VERSION} - the game server of Habnut.\\n";'
)

FIGLET = [
    r"  _   _    _    ____  _   _ _   _ _____ ",
    r" | | | |  / \  | __ )| \ | | | | |_   _|",
    r" | |_| | / _ \ |  _ \|  \| | | | | | |  ",
    r" |  _  |/ ___ \| |_) | |\  | |_| | | |  ",
    r" |_| |_/_/   \_\____/|_| \_|\___/  |_|  ",
]
OLD_FIGLET = [
    r"   ____   ___  _        _    ____  ___ ____  ",
    r"  |  _ \ / _ \| |      / \  |  _ \|_ _/ ___| ",
    r"  | |_) | | | | |     / _ \ | |_) || | \___ \ ",
    r"  |  __/| |_| | |___ / ___ \|  _ <  | |  ___) |",
    r"  |_|    \___/|_____/_/   \_\_| \_\|___||____/ ",
]


def java(s):
    return s.replace("\\", "\\\\")


def main():
    emulator = "Emulator.java"
    p = root / emulator
    s = p.read_text(encoding="utf8")

    old_version = 'public static final String version = "Polaris " + resolveVersionNumber();'
    new_version = (f'public static final String version = "{NAME} v{VERSION}";\n'
                   '    /** The Polaris release this build is made from, for the operator\'s logs. */\n'
                   '    public static final String upstreamVersion = "Polaris " + resolveVersionNumber();')
    if old_version not in s:
        sys.exit("Emulator.java: version line moved")
    s = s.replace(old_version, new_version)

    start = s.index('private static final String logo = ') + len('private static final String logo = ')
    end = s.index('Still Rocking in 2026.\\n";', start) + len('Still Rocking in 2026.\\n";')
    s = s[:start] + HABNUT_BLOCK + s[end:]

    card_old = ('                + "| Polaris (formerly Arcturus Morningstar Extended)               |\\n"\n'
                '                + "| Source : github.com/duckietm/Nitro-Cool-UI-Renderer            |\\n"\n'
                '                + "| Scope  : Educational open-source fork of Arcturus/Morningstar  |\\n"\n')
    card_new = (f'                + "{box(NAME + " v" + VERSION, 66)}\\n"\n'
                f'                + "{box("Hotel  : " + SITE.split("://")[1], 66)}\\n"\n'
                '                + "| Base   : " + upstreamVersion + "\\n"\n')
    if card_old not in s:
        sys.exit("Emulator.java: startup card moved")
    s = s.replace(card_old, card_new)

    for old, new in zip(OLD_FIGLET, FIGLET):
        old_j, new_j = java(old), java(new)
        if old_j not in s:
            sys.exit(f"Emulator.java: banner line moved: {old}")
        s = s.replace(old_j, new_j)

    pairs = [
        ('"[OK] POLARIS" + ANSI_RESET + fit("", 63)', f'"[OK] {NAME.upper()}" + ANSI_RESET + fit("", {75 - len("[OK] " + NAME)})'),
        ('"Polaris game server runtime" + ANSI_RESET + fit("", 49)', f'"The game server of Habnut" + ANSI_RESET + fit("", {76 - len("The game server of Habnut")})'),
        ('"| POLARIS                                                                      |\\n"', f'"{box(NAME.upper(), 80)}\\n"'),
        ('"| Polaris game server runtime                                                  |\\n"', f'"{box("The game server of Habnut", 80)}\\n"'),
    ]
    for old, new in pairs:
        if old not in s:
            sys.exit(f"Emulator.java: cannot find {old}")
        s = s.replace(old, new)
    p.write_text(s, encoding="utf8")
    # Log lines: "Polaris has successfully loaded." and friends.
    literals(emulator, lambda t: t if "pre-Polaris" in t or t == "Polaris " else t.replace("Polaris", NAME))
    # "Stopping {}" already names it, through the version.
    edit(emulator, [('"Stopping Habnut Emulator {}"', '"Stopping {}"'), ('"Stopped Habnut Emulator {}"', '"Stopped {}"')])

    edit("habbohotel/commands/AboutCommand.java", [
        ('"https://github.com/duckietm/Nitro-V3/issues"', f'"{SITE}/help-center"'),
        ('''        message.append("<b>Credits</b>\\r")
                .append("- The General\\r")
                .append("- Krews Team\\r")
                .append("- DuckieTM, simoleo89, Medievalshell, Lorenzo, Remco, Dennis (DennisObject)\\r")
                .append("- Dippy (Improved wired architecture base)\\r")
                .append("- Seth / iSetht (Opacity & Gravity wireds)\\r")
                .append("- bop (Easter egg exploit & Nitro Memory Leaks)\\r")
                .append("- DevHBB (Valentin \\u00d7\\u035c\\u00d7)\\r\\n")
                .append("Report issues at: ")''',
         '''        message.append("<b>Habnut</b>\\r")
                .append("- Made by the Habnut team\\r\\n")
                .append("Need a hand? ")'''),
    ])

    edit("habbohotel/commands/ArcturusCommand.java", [
        ('''            gameClient.getHabbo().whisper("This hotel is powered by Arcturus Emulator! \\r" +
                            "Cet hôtel est alimenté par Arcturus émulateur! \\r" +
                            "Dit hotel draait op Arcturus Emulator! \\r" +
                            "Este hotel está propulsado por Arcturus emulador! \\r" +
                            "Hotellet drivs av Arcturus Emulator! \\r" +
                            "Das Hotel gehört zu Arcturus Emulator betrieben!"''',
         '''            gameClient.getHabbo().whisper("Habnut runs on " + com.eu.habbo.Emulator.version + "."'''),
    ])

    edit("messages/incoming/housekeeping/HousekeepingGetDashboardEvent.java", [
        ('String version = "Polaris";', "String version = com.eu.habbo.Emulator.version;"),
    ])

    # The wired API's built-in documentation, which room builders read.
    for rel in ("networking/gameserver/wired/WiredApiOpenApi.java", "networking/gameserver/wired/WiredApiRouter.java"):
        literals(rel, lambda t: t.replace("Polaris", "Habnut"))

    # Every sentence the server can say, under Habnut's text rule: a string
    # literal with a space and a capital Habb- word, or the word Nitro (but not
    # the X-Nitro-* protocol headers the client depends on).
    sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent / "scripts"))
    from habnut_brand import rebrand

    literal = re.compile(r'"((?:\\.|[^"\\\n])*)"')

    def reword(m):
        text = m.group(1)
        sentence = " " in text and ("Habb" in text or ("Nitro" in text and "Nitro-" not in text and "-Nitro" not in text))
        return '"' + rebrand(text) + '"' if sentence else m.group(0)

    reworded = 0
    for path in root.rglob("*.java"):
        s = path.read_text(encoding="utf8")
        t = literal.sub(reword, s)
        if t != s:
            path.write_text(t, encoding="utf8")
            reworded += 1
    print(f"reworded sentences in {reworded} files")

    print(f"branded as {NAME} v{VERSION}")


if __name__ == "__main__":
    main()
