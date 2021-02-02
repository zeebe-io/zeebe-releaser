#!/bin/bash
set -exuo pipefail

if [ ! -f credentials ]
then
  echo "Make sure you store your cloud credentials in a file called 'credentials', othwise the scripts will not work. DO NOT COMMIT THE CREDENTIALS!"
  exit 1
fi

source credentials

# zbctl
if [ ! -f zbctl ]
then
  wget https://github.com/zeebe-io/zeebe/releases/download/0.24.5/zbctl
fi

chmod +x zbctl

zbctl status

zbctl deploy *.bpmn
