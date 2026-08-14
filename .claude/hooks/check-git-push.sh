#!/bin/bash
# appリポジトリへのgit pushを止める。pushの前に確認を取るため。
# researchリポジトリは確認不要の運用なので通す。
set -u

input=$(cat)
command=$(printf '%s' "$input" | jq -r '.tool_input.command // ""')

if ! printf '%s' "$command" | grep -Eq '(^|[;&|[:space:]])git([[:space:]]+-[^[:space:]]+[[:space:]]+[^[:space:]]+)*[[:space:]]+push'; then
  exit 0
fi

if printf '%s' "$command" | grep -q 'research'; then
  exit 0
fi

cat <<'JSON'
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "appリポジトリへのpushは事前確認が要ります。コミットまでで止めて「pushしていい？」と確認してください（researchリポジトリは確認不要）。"
  }
}
JSON
