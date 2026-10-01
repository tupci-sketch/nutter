/**
 * Generate the client's packet table from the hotel's own.
 *
 * The names on the wire are decided by the server: its handlers are registered
 * against the constants in PacketType.java, and a client that invents its own
 * names is simply talking to nobody. Rather than keep two lists in step by
 * hand, this reads that file and writes the TypeScript table from it, so there
 * is one source of truth and a rename on the server cannot quietly strand the
 * client. `pnpm packets:check` fails the build if the committed file has
 * drifted from the server's.
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));

export const JAVA_SOURCE = resolve(
  here,
  '../../emulator/src/main/java/com/habnut/emulator/protocol/PacketType.java',
);
export const TS_TARGET = resolve(here, '../src/protocol/packets.ts');

const CONSTANT = /public\s+static\s+final\s+String\s+([A-Z0-9_]+)\s*=\s*"([^"]+)"\s*;/g;

/** Every constant in the server's packet table, in the order it declares them. */
export function parseJava(source) {
  const entries = [];
  for (const [, name, value] of source.matchAll(CONSTANT)) {
    entries.push({ name, value });
  }
  if (entries.length === 0) {
    throw new Error(`No packet constants found in ${JAVA_SOURCE}`);
  }
  return entries;
}

export function render(entries) {
  const seenValues = new Map();
  for (const { name, value } of entries) {
    if (seenValues.has(value)) {
      throw new Error(
        `Two packet names share the wire value "${value}": ` +
          `${seenValues.get(value)} and ${name}`,
      );
    }
    seenValues.set(value, name);
  }

  const width = Math.max(...entries.map(e => e.name.length));
  const lines = entries.map(
    ({ name, value }) => `  ${name}:${' '.repeat(width - name.length)} '${value}',`,
  );

  return `// GENERATED FILE — do not edit.
//
// Written from the hotel's PacketType.java by \`pnpm packets:generate\`, so the
// names the client sends are the names the server has handlers for. Add a
// packet on the server, run the generator, and it appears here.

export const Packet = {
${lines.join('\n')}
} as const;

export type PacketName = keyof typeof Packet;
export type PacketType = (typeof Packet)[PacketName];

export interface Envelope {
  type: PacketType | string;
  payload: unknown;
}
`;
}

export function generate() {
  return render(parseJava(readFileSync(JAVA_SOURCE, 'utf8')));
}

const invokedDirectly = process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url));

if (invokedDirectly) {
  const output = generate();
  const check = process.argv.includes('--check');

  if (check) {
    const current = readFileSync(TS_TARGET, 'utf8');
    if (current !== output) {
      console.error(
        'packets.ts is out of date with PacketType.java. Run `pnpm packets:generate`.',
      );
      process.exit(1);
    }
    console.log('packets.ts matches PacketType.java.');
  } else {
    writeFileSync(TS_TARGET, output);
    console.log(`Wrote ${TS_TARGET}`);
  }
}
