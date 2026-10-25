#!/bin/sh
# Enforces the gitflow branch model for a pull request: check-branch.sh <head> <base>
head=$1
base=$2

ok() { exit 0; }
fail() {
    echo "✖ '$head' cannot be merged into '$base'. $1" >&2
    echo "  See CONTRIBUTING.md#branching-model-gitflow" >&2
    exit 1
}

case "$base" in
    main)
        case "$head" in release/[0-9]*.[0-9]*.[0-9]* | hotfix/*) ok ;; esac
        fail "Only release/X.Y.Z and hotfix/* branches merge into main." ;;
    develop)
        case "$head" in feature/* | bugfix/* | docs/* | chore/* | release/* | hotfix/* | main | dependabot/*) ok ;; esac
        fail "Branch from develop as feature/*, bugfix/*, docs/* or chore/*." ;;
    release/*)
        case "$head" in bugfix/*) ok ;; esac
        fail "Only bugfix/* branches merge into a release branch." ;;
    *)
        fail "Pull requests must target develop (or main for releases/hotfixes)." ;;
esac
