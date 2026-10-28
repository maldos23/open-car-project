#!/bin/sh
# Validates a commit message (file path or literal text) against Conventional Commits 1.0.
# Used by the local commit-msg hook (.githooks/commit-msg) and by CI on pull requests.
#   scripts/check-commit-msg.sh .git/COMMIT_EDITMSG
#   scripts/check-commit-msg.sh --text "feat(grid): add snapping"
set -e

if [ "$1" = "--text" ]; then message=$2; else message=$(cat "$1"); fi
header=$(printf '%s\n' "$message" | head -n1)

# Public repo: no session links, private IP addresses or adb endpoints anywhere in the message.
leak='claude\.ai/code/session_|session_[A-Za-z0-9]{16,}|(^|[^0-9.])(10\.[0-9]+|192\.168|172\.(1[6-9]|2[0-9]|3[01]))\.[0-9]+\.[0-9]+'
if printf '%s\n' "$message" | grep -Eq "$leak"; then
  echo "✖ Commit message contains a session link or a private IP address; remove it." >&2
  exit 1
fi

types='feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert'
pattern="^($types)(\([a-z0-9._/-]+\))?!?: [^ ].{0,99}$"

# Git-generated messages (gitflow merges, autosquash) are allowed as-is.
if printf '%s' "$header" | grep -Eq '^(Merge |fixup! |squash! )'; then exit 0; fi

if printf '%s' "$header" | grep -Eq "$pattern"; then exit 0; fi

cat >&2 <<MSG
✖ Commit message does not follow Conventional Commits:
    $header

  Format:  <type>(<optional scope>)!: <description>   (header ≤ 100 chars after the type)
  Types:   feat fix docs style refactor perf test build ci chore revert
  Example: feat(media): show album art in the island
  See CONTRIBUTING.md#commit-messages
MSG
exit 1
