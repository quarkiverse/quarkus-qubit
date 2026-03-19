#!/bin/bash
set -euo pipefail

jbang .github/UpdateReadme.java "${CURRENT_VERSION}"
git commit -m "Update README to ${CURRENT_VERSION}" README.md
