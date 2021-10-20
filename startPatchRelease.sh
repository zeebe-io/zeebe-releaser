#!/bin/bash
set -exuo pipefail

if [ ! -f credentials ]
then
  echo "Make sure you store your cloud credentials in a file called 'credentials', otherwise the scripts will not work. DO NOT COMMIT THE CREDENTIALS!"
  exit 1
fi

source credentials

# zbctl
if [ ! "$(which zbctl)" ]
then
  wget https://github.com/camunda-cloud/zeebe/releases/download/1.2.1/zbctl
  chmod +x zbctl
fi

zbctl status

zbctl create instance zeebe-patch-release-process
