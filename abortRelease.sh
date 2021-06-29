#!/bin/bash
set -exo pipefail

if [ -z $1 ]
then
  echo "Please provide the startDateTime of the release process that should be cancelled"
  echo "You can find the startDateTime in Operate as variable in the zeebe-release process"
  exit 1
fi

if [ ! -f credentials ]
then
  echo "Make sure you store your cloud credentials in a file called 'credentials', otherwise the scripts will not work. DO NOT COMMIT THE CREDENTIALS!"
  exit 1
fi

source credentials

# zbctl
if [ ! "$(which zbctl)" ]
then
  wget https://github.com/camunda-cloud/zeebe/releases/download/1.0.0/zbctl
  chmod +x zbctl
fi

zbctl status

zbctl publish message abort --correlationKey="$1"
