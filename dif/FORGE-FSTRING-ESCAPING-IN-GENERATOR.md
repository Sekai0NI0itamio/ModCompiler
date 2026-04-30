---
id: FORGE-FSTRING-ESCAPING-IN-GENERATOR
title: Generator script — Python f-string not applied to source string, leaving {MOD_ID} and {{ literal braces in Java output
tags: [generator, python, compile-error, f-string, escaping, best-practice]
versions: []
loaders: [forge, neoforge, fabric]
symbols: []
error_patterns: ["illegal start of expression", "class, interface, enum, or record expected", "reached end of file while parsing"]
---

## Issue

A generator script produces Java source files that contain literal `{MOD_ID}`,
`{MOD_VERSION}`, or `{{` / `}}` instead of the substituted values. The Java
compiler then fails with `illegal start of expression` or
`class, interface, enum, or record expected`.

## Error

```
error: illegal start of expression
    public static final String MODID = "{MOD_ID}";
                                       ^
error: class, interface, enum, or record expected
}}
^
```

## Root Cause

The source string was defined as a regular Python string (`"""\..."""`) instead of
an f-string (`f"""\..."""`). Python f-strings substitute `{VAR}` placeholders and
convert `{{` / `}}` to literal `{` / `}`. Without the `f` prefix:

- `{MOD_ID}` stays as the literal text `{MOD_ID}` in the Java file
- `{{` stays as `{{` — two braces — which is invalid Java

This is easy to miss when copy-pasting a source block from another string in the
same file that IS an f-string.

## Fix

Add the `f` prefix to the string literal:

```python
# WRONG — regular string, no substitution
FORGE_26_SRC = """\
package com.example;
@Mod("{MOD_ID}")
public class MyMod {{
    ...
}}
"""

# CORRECT — f-string, substitution happens at definition time
FORGE_26_SRC = f"""\
package com.example;
@Mod("{MOD_ID}")
public class MyMod {{
    ...
}}
"""
```

After fixing, verify the generated file before committing:

```bash
python3 scripts/generate_<mod>_bundle.py
cat incoming/<mod>-all-versions/<slug>/src/main/java/.../MyMod.java | head -20
# Should show: @Mod("accountswitcher") not @Mod("{MOD_ID}")
# Should show: public class MyMod { not public class MyMod {{
```

## Prevention

When writing a new source block in a generator:
1. Always use `f"""..."""` if the string contains `{VAR}` placeholders or `{{`/`}}`
2. After generating, spot-check at least one output file before committing
3. If a source block is derived from another by copy-paste, double-check the `f` prefix

## Verified

Confirmed in Account Switcher all-versions port (run 1, April 2026).
`FORGE_26_SRC` was missing the `f` prefix. After adding it, `accountswitcher-forge-26-1-2`
passed on run 2.
