import re, sys, os

log = open(sys.argv[1], errors="replace").read() if os.path.exists(sys.argv[1]) else ""
root = os.environ.get("GITHUB_WORKSPACE", "")

def esc(s):
    return s.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")

errors = []
for m in re.finditer(r"^e: (?:file://)?(\S+?\.kt):(\d+):(\d+) (.*)$", log, re.M):
    path, line, col, msg = m.groups()
    if root and path.startswith(root + "/"):
        path = path[len(root) + 1:]
    errors.append((path, int(line), int(col), msg.strip()))

# de-duplicate (the same error is often printed once per source set)
seen, unique = set(), []
for e in errors:
    if e not in seen:
        seen.add(e); unique.append(e)

for path, line, col, msg in unique[:10]:
    print(f"::error file={path},line={line},col={col}::{esc(msg)}")

if unique:
    body = "\n".join(f"{p.split('/')[-1]}:{l}:{c} {m}" for p, l, c, m in unique)
    print(f"::error title=Kotlin compiler errors ({len(unique)})::{esc(body[:6000])}")

# Non-Kotlin failures (dependency resolution, plugin, AGP, etc.)
wrong = re.search(r"\* What went wrong:\n(.*?)(?:\n\* Try:|\Z)", log, re.S)
if wrong:
    print(f"::error title=Gradle: what went wrong::{esc(wrong.group(1).strip()[:3500])}")

if not unique and not wrong and log:
    tail = "\n".join(log.strip().splitlines()[-40:])
    print(f"::error title=Build log tail::{esc(tail[:3500])}")
