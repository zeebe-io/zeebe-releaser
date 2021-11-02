#!/bin/bash
set -exuo pipefail

if [ ! -f credentials ]
then
  echo "Make sure you store your cloud credentials in a file called 'credentials', otherwise the scripts will not work. DO NOT COMMIT THE CREDENTIALS!"
  exit 1
fi

source credentials

release=$1

# zbctl
if [ ! "$(which zbctl)" ]
then
  wget https://github.com/camunda-cloud/zeebe/releases/download/1.0.0/zbctl
  chmod +x zbctl
fi

./zbctl status

./zbctl publish message "qa-done" --correlationKey "$release"
