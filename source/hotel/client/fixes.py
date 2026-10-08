#!/usr/bin/env python3
"""Fixes to upstream Octane that Habnut needs, applied at build time.

    fixes.py <octane-checkout>

Each fix says what was wrong. Fails loudly if upstream moved the code, so a
fix is never silently lost.
"""
import pathlib
import sys

ui = pathlib.Path(sys.argv[1])


def edit(rel, old, new):
    p = ui / rel
    s = p.read_text(encoding="utf8")
    if old not in s:
        sys.exit(f"{rel}: upstream changed, cannot find:\n{old[:300]}")
    p.write_text(s.replace(old, new, 1), encoding="utf8")


# The shop's page list vanished for good after the front page had been shown.
# The front page hides it in an effect that depends on the hide callback, and
# the callback was a new function on every render, so the effect ran again
# after switching tabs and hid the list for every other page too. A stable
# callback runs it only when the front page itself is shown.
edit("src/components/catalog/CatalogView.tsx",
     "    const catalogAdmin = useCatalogAdmin();\n",
     "    const hideNavigation = useMemo(() => () => setNavigationHidden?.(true), [setNavigationHidden]);\n"
     "    const catalogAdmin = useCatalogAdmin();\n")
edit("src/components/catalog/CatalogView.tsx",
     "{GetCatalogLayout(currentPage, () => setNavigationHidden(true))}",
     "{GetCatalogLayout(currentPage, hideNavigation)}")

# The group members window said its count in Italian.
edit("src/components/groups/views/GroupMembersView.tsx", " Membri. Pagina", " members. Page")

print("fixes applied")
