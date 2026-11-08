#!/usr/bin/env bash
set -euo pipefail

# Publish generated files as a single root commit, without changing this checkout.
source_directory=${1:?Usage: publish_web.sh DISTRIBUTION_DIRECTORY}
source_directory=$(realpath "$source_directory")
test -s "$source_directory/index.html"
remote_url=$(git remote get-url origin)
expected_commit=$(git ls-remote origin refs/heads/gh-pages | cut -f1)
publish_directory=$(mktemp -d)
trap 'rm -rf "$publish_directory"' EXIT
cp -a "$source_directory/." "$publish_directory/"
touch "$publish_directory/.nojekyll"
git -C "$publish_directory" init --initial-branch=gh-pages
git -C "$publish_directory" config user.name 'github-actions[bot]'
git -C "$publish_directory" config user.email '41898282+github-actions[bot]@users.noreply.github.com'
git -C "$publish_directory" add --all
git -C "$publish_directory" commit --message "Dashboard ${GITHUB_SHA:-$(git rev-parse HEAD)}"
test "$(git -C "$publish_directory" rev-list --count HEAD)" = 1
# Use the checkout's authenticated repository configuration, without copying credentials.
git --git-dir="$(git rev-parse --absolute-git-dir)" \
    -c "core.worktree=$publish_directory" \
    fetch "$publish_directory" gh-pages
# FETCH_HEAD now names the generated root commit in the authenticated source repository.
git push "$remote_url" "--force-with-lease=refs/heads/gh-pages:$expected_commit" FETCH_HEAD:refs/heads/gh-pages
