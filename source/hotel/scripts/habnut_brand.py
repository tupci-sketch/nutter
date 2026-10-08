"""The one rule for player-visible text: no "Habb-" words anywhere.

    Habbo, Habbos, Habbo's ... -> Habnut ...   (HABBO -> HABNUT)
    any other Habb- word       -> Nut-           Habburgers -> Nutburgers,
                                                 Habbfather -> Nutfather,
                                                 Habbicons -> Nuticons

Links are left alone (a rewritten URL points nowhere). Use rebrand() on
display text only, never on identifiers, class names or keys.
"""
import re

_URL = re.compile(r"(https?://\S+)")
_NITRO = re.compile(r"\bnitro\b", re.IGNORECASE)  # the client: Nutty, here
_HABBO = re.compile(r"habb[oóòôö]", re.IGNORECASE)
_HABB = re.compile(r"habb", re.IGNORECASE)  # whatever is left once Habbo is done
# Puns built on a word that starts with b keep their b.
_PUNS = {"habburger": "nutburger", "habberge": "nutberge", "habbucks": "nutbucks", "habburn": "nutburn", "habby": "nutty"}
_PUN = re.compile(r"\b(" + "|".join(_PUNS) + r")", re.IGNORECASE)


def _case(word, upper, title, lower):
    if word.isupper():
        return upper
    return title if word[0].isupper() else lower


def rebrand(text):
    if not isinstance(text, str) or ("abb" not in text.lower() and "nitro" not in text.lower()):
        return text
    parts = _URL.split(text)
    for i, part in enumerate(parts):
        if part.startswith("http"):
            continue
        part = _PUN.sub(lambda m: _case(m.group(0), _PUNS[m.group(0).lower()].upper(), _PUNS[m.group(0).lower()].title(),
                                         _PUNS[m.group(0).lower()]), part)
        part = _HABBO.sub(lambda m: _case(m.group(0), "HABNUT", "Habnut", "habnut"), part)
        part = _HABB.sub(lambda m: _case(m.group(0), "NUT", "Nut", "nut"), part)
        part = _NITRO.sub(lambda m: _case(m.group(0), "NUTTY", "Nutty", "nutty"), part)
        parts[i] = part
    return "".join(parts)


if __name__ == "__main__":
    for sample in ("Habbo Hotel", "HABBO", "Habburgers", "the Habbfather", "Habbicons", "habbo.com/x https://habbo.com/a",
                   "Habborella", "Welcome Habbos!", "Habberge", "Habby the cat", "HabbCrazy", "Adjust the default Nitro settings", "nitro.login.title"):
        print(f"{sample!r:40} -> {rebrand(sample)!r}")
