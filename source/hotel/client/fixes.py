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
edit("src/components/groups/views/GroupMembersView.tsx", " Habbo Membri. Pagina", " members. Page")

# The furni editor's import fetches the official furniture data; say so plainly.
for old, new in (("Import from Habbo", "Import official data"), ("Imported from Habnut", "Imported official data"),
                 ("Not found on Habnut for this classname", "Not in the official data for this classname"),
                 ("from Habnut", "from the official data")):
    p = ui / "src/components/furni-editor/views/FurniEditorEditView.tsx"
    p.write_text(p.read_text(encoding="utf8").replace(old, new), encoding="utf8")

# Labels for players that are single words, so the general rewording leaves them be.
for rel, old, new in (("src/api/wired/variablesExplorer.ts", "            return 'Habbo';", "            return 'Habnut';"),
                      ("src/hooks/rooms/widgets/useUserChooserState.ts", "            return 'Habbo';", "            return 'Habnut';"),
                      ("src/components/wired-tools/WiredCreatorToolsView.tsx", "let categoryLabel = 'Habbo';", "let categoryLabel = 'Habnut';")):
    edit(rel, old, new)

print("fixes applied")
