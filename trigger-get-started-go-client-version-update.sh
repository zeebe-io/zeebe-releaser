#!/bin/bash

set -ex -o pipefail

# work in progress
#
# missing pieces: 
# - authentication
# - not entirely sure how to reference the workflow in workflows/**main**/dispatches 

# See also https://github.com/felixp8/dispatch-and-wait/blob/master/entrypoint.sh
# for inspiration on how to trigger a process, find the triggered process and wait for the result

# Use with PUSH_CHANGES = false to verify a release candidate
# Use with PUSH_CHANGES = true after release



curl \
  -X POST \
  -H "Accept: application/vnd.github.v3+json" \
  https://api.github.com/repos/zeebe-io/zeebe-get-started-go-client/actions/workflows/main/dispatches \
  -d '{"ref":"master", "inputs": {"VERSION":"0.25.1", "PUSH_CHANGES":"true"}}'
