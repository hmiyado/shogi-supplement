#!/usr/bin/env python3
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTAINER = "supabase_db_infra"
MIGRATIONS = sorted((ROOT / "infra/supabase/migrations").glob("*.sql"))
BOUNDARY = "20261003090000"


def psql(sql):
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "psql", "-X", "-At", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", "postgres"],
        input=sql, text=True, capture_output=True, check=True,
    )
    return result.stdout


def check_tap(name, sql):
    output = psql(sql)
    plan = re.findall(r"^1\.\.(\d+)$", output, re.M)
    assertions = re.findall(r"^(?:not )?ok \d+\b.*$", output, re.M)
    print(name)
    print("\n".join(assertions))
    if len(plan) != 1 or len(assertions) != int(plan[0]) or re.search(r"^not ok\b|^Bail out!|Looks like you", output, re.M):
        raise RuntimeError("Failed or incomplete TAP output: " + output)


applied = set(psql("select version from supabase_migrations.schema_migrations").splitlines())
missing = [p for p in MIGRATIONS if p.name.split("_")[0] not in applied]
contract = (ROOT / "tools/db-regression/current-client.sql").read_text()
for after in [False, True]:
    setup = "BEGIN;\n" + "\n".join(p.read_text() for p in missing if p.name.split("_")[0] < BOUNDARY)
    setup += "\nDROP FUNCTION IF EXISTS public.put_repertoire_entry(uuid,text,text,text,text,text);\nDROP TABLE IF EXISTS public.repertoire_entries;\n"
    if after:
        setup += "\n" + "\n".join(p.read_text() for p in MIGRATIONS if p.name.split("_")[0] == BOUNDARY)
    check_tap("current client " + ("after migration" if after else "before migration"), setup + "\n" + contract + "\nROLLBACK;")
for test in sorted((ROOT / "infra/supabase/tests").glob("*.sql")):
    body = re.sub(r"\bbegin;", "", test.read_text(), count=1, flags=re.I)
    check_tap(test.name, "BEGIN;\n" + "\n".join(p.read_text() for p in missing) + "\n" + body)
