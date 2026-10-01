#!/usr/bin/env node
// Converts the frozen Flutter ARB files into Android string resources (NATIVE_PLAN.md §3.4).
//
//   legacy/flutter/lib/l10n/app_en.arb      -> core/design-system/src/main/res/values/strings.xml
//   legacy/flutter/lib/l10n/app_zh_Hant.arb  -> core/design-system/src/main/res/values-zh-rTW/strings.xml
//
// The output lives in `core/design-system` rather than in `app` because every screen needs it and
// `android.nonTransitiveRClass=true` gives each module an R class holding only its own resources:
// strings declared in `app` are unreachable from `feature/*`. `core/design-system` is already an
// `api` dependency of every feature, and it already ships the other generated assets (the font
// subset), so the call sites read `com.marcow.bible.core.designsystem.R` with no per-feature wiring.
//
// `app_zh.arb` (Simplified) has no native locale in Phase 1, so it is deliberately not emitted.
//
// Key naming: the ARB keys stay camelCase in the source, and this script converts them to
// Android's snake_case convention (`selectBook` -> `select_book`) with a pure function, so
// re-running is stable and no hand-maintained map can drift.
//
// Placeholders: ARB `{name}` becomes Android positional `%1$s` / `%1$d`, using the type declared
// in the `@key` metadata. Both translations are validated against each other, so a mismatched
// placeholder set fails here instead of showing "wrong format" at runtime.
//
// Like the bible.db builder this is NOT wired into Gradle: CI must never regenerate shipped
// resources. Re-run by hand and commit the result. Run with `--check` to prove they are in sync.
//
// Usage:
//   node tool/arb_to_strings.mjs [--check]

import { readFileSync, writeFileSync, mkdirSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const arbDir = join(root, "legacy", "flutter", "lib", "l10n");
const resDir = join(root, "core", "design-system", "src", "main", "res");

/** `selectChapterCurrent` -> `select_chapter_current`. */
function toSnakeCase(key) {
  return key
    .replace(/([a-z0-9])([A-Z])/g, "$1_$2")
    .replace(/([A-Z]+)([A-Z][a-z])/g, "$1_$2")
    .toLowerCase();
}

/**
 * Rewrites ARB placeholders into Android positional ones: `{chapter}` -> `%1$d`.
 * Returns null when the value has no placeholder, so callers can tell "no args" from "arg 0".
 */
function toAndroidPlaceholders(value, placeholderTypes, errors, context) {
  if (typeof value !== "string" || !value.includes("{")) return null;
  const order = [];
  const converted = value.replace(/\{(\w+)\}/g, (_, name) => {
    const type = placeholderTypes?.[name]?.type;
    if (!type) {
      errors.push(`${context}: placeholder {${name}} has no @metadata entry`);
      return "";
    }
    if (!order.includes(name)) order.push(name);
    // int / num are formatted as integers; everything else as a string. The position is
    // substituted by hand, so the literal `%n$` is built by concatenation.
    const position = "%" + order.length;
    return type === "int" || type === "num" ? position + "$d" : position + "$s";
  });
  return { text: converted, args: order };
}

function readArb(fileName) {
  const path = join(arbDir, fileName);
  let payload;
  try {
    payload = JSON.parse(readFileSync(path, "utf8"));
  } catch (error) {
    throw new Error(`cannot read ${path}: ${error.message}`);
  }
  const entries = [];
  for (const [key, value] of Object.entries(payload)) {
    if (key.startsWith("@")) continue;
    if (typeof value !== "string") {
      throw new Error(`${fileName}: ${key} is not a string`);
    }
    entries.push({ key, value, meta: payload[`@${key}`] });
  }
  return { fileName, entries };
}

function escapeXml(value) {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "\\'");
}

function renderStringsXml(arb, errors) {
  const lines = [
    '<?xml version="1.0" encoding="utf-8"?>',
    "<!--",
    `  Generated from legacy/flutter/lib/l10n/${arb.fileName} by tool/arb_to_strings.mjs.`,
    "  Do not edit by hand; re-run the script and commit the result.",
    "-->",
    "<resources>",
    // Not in the ARB: `AndroidManifest.xml` needs a launcher label, and the Flutter manifest
    // hardcodes `android:label="Bible"` in every locale, so this is not localized either.
    '    <string name="app_name" translatable="false">Bible</string>',
  ];
  for (const { key, value, meta } of arb.entries) {
    const name = toSnakeCase(key);
    const converted = toAndroidPlaceholders(value, meta?.placeholders, errors, `${arb.fileName}:${key}`);
    // A literal `%` would otherwise be read as a format spec, so mark those unformatted.
    const formatted = !converted && value.includes("%") ? ` formatted="false"` : "";
    const text = converted ? converted.text : value;
    lines.push(`    <string name="${name}"${formatted}>${escapeXml(text)}</string>`);
  }
  lines.push("</resources>", "");
  return lines.join("\n");
}

const en = readArb("app_en.arb");
const zhHant = readArb("app_zh_Hant.arb");
const errors = [];

const enKeys = en.entries.map((e) => e.key);
const zhKeys = zhHant.entries.map((e) => e.key);
const missingInZh = enKeys.filter((k) => !zhKeys.includes(k));
const missingInEn = zhKeys.filter((k) => !enKeys.includes(k));
if (missingInZh.length) errors.push(`app_en.arb has keys missing from app_zh_Hant.arb: ${missingInZh.join(", ")}`);
if (missingInEn.length) errors.push(`app_zh_Hant.arb has keys missing from app_en.arb: ${missingInEn.join(", ")}`);

// Placeholder sets must line up across translations, otherwise a translated string would
// reference a positional argument the caller never passes.
for (const { key, value, meta } of en.entries) {
  const other = zhHant.entries.find((e) => e.key === key);
  if (!other) continue;
  const names = (v) => [...String(v).matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();
  const a = names(value).join(",");
  const b = names(other.value).join(",");
  if (a !== b) errors.push(`placeholder mismatch on ${key}: en [${a}] vs zh-Hant [${b}]`);
  // Every placeholder a value references must be declared, otherwise the `%1$s` conversion above
  // would have no type to pick and the generated string would be positional-but-untyped.
  const declared = Object.keys(meta?.placeholders ?? {});
  for (const name of names(value)) {
    if (!declared.includes(name)) {
      errors.push(`${en.fileName}:${key} uses {${name}} with no @${key}.placeholders entry`);
    }
  }
}

const targets = [
  { dir: join(resDir, "values"), xml: renderStringsXml(en, errors), source: en.fileName },
  { dir: join(resDir, "values-zh-rTW"), xml: renderStringsXml(zhHant, errors), source: zhHant.fileName },
];

if (errors.length) {
  console.error("arb validation failed:");
  for (const error of errors) console.error(`  - ${error}`);
  process.exit(1);
}

if (process.argv.includes("--check")) {
  let stale = false;
  for (const target of targets) {
    const path = join(target.dir, "strings.xml");
    let current;
    try {
      current = readFileSync(path, "utf8");
    } catch {
      console.error(`missing ${path}; run \`node tool/arb_to_strings.mjs\``);
      stale = true;
      continue;
    }
    if (current !== target.xml) {
      console.error(`stale ${path}; run \`node tool/arb_to_strings.mjs\``);
      stale = true;
    }
  }
  if (stale) process.exit(1);
  console.log(`strings.xml is in sync (${enKeys.length} strings, values/ + values-zh-rTW/)`);
} else {
  for (const target of targets) {
    mkdirSync(target.dir, { recursive: true });
    writeFileSync(join(target.dir, "strings.xml"), target.xml);
    console.log(`wrote ${join(target.dir, "strings.xml")} from ${target.source}`);
  }
}

export { toSnakeCase, toAndroidPlaceholders };
