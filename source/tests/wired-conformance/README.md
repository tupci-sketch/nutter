# Wired 2.0 Conformance Suite

Chapter 59.1 makes a fully passing run of this suite the precondition for game
work. It exists to catch the class of defect that is invisible at compile time
and expensive in production: a condition that silently inverts, a trigger that
fires on a neighbouring event, a variable that leaks between rooms.

## Running

```bash
gradle :wired-conformance:test
```

The suite needs no database, no Redis and no running server. `WiredHarness`
builds a registry with every definition registered, backed by an in-memory
variable store, so wired semantics are exercised in isolation.

## What it covers

| Area | File | Asserts |
|------|------|---------|
| Registry completeness | `RegistryConformanceTest` | All 21 triggers, 37 conditions, 22 selectors and 56 actions are registered under their specified codes, with no duplicates and no unknown codes resolving |
| Value model | `ValueConformanceTest` | Type conversion across number, text and bool; the arithmetic, comparison and text operator sets; the coercion rules where operand types differ |
| Variables | `VariableConformanceTest` | Room, user and global scope isolation; eviction confined to one room; the variable arithmetic actions; variable-to-variable operands |
| Conditions | `ConditionConformanceTest` | Each comparison on both sides of its boundary; each negated condition against its positive twin; behaviour with no actor and with unset variables |
| Triggers | `TriggerConformanceTest` | Each trigger fires on its own event and stays silent on neighbouring ones; chat matching rules; score thresholds; signal and variable keys |
| Execution | `ExecutionConformanceTest` | Conditions combine with AND; action ordering; unknown codes and throwing actions degrade rather than abort; the operation limiter stops a runaway stack |
| Signals | `SignalConformanceTest` | Room-local delivery stays local; global broadcast reaches every room; the per-channel rate limit admits exactly its documented allowance and its window reopens |

## Why the safety cases matter

A room is untrusted input — any player can build a stack. The execution tests
assert that an unknown definition code, an action that throws, and an unbounded
loop all degrade to a contained failure rather than taking down the room. The
signal rate-limit tests assert the same for the one wired feature that can
reach beyond its own room.

## Adding a definition

A new trigger, condition, selector or action must be added to the matching list
in `RegistryConformanceTest` along with a semantics test in the relevant file.
The count assertions fail otherwise, which is deliberate: the gate should
notice a block arriving without tests.
