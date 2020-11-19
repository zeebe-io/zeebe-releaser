#!/bin/bash
set -exuo pipefail

source credentials

# zbctl
if [ ! -f zbctl ]
then
  wget https://github.com/zeebe-io/zeebe/releases/download/0.24.5/zbctl
fi

chmod +x zbctl

zbctl status

zbctl create instance zeebe-repeating-release
