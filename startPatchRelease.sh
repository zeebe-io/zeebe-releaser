#!/bin/bash
set -exuo pipefail

if [ ! -f credentials ]
then
  echo "Make sure you store your cloud credentials in a file called 'credentials', otherwise the scripts will not work. DO NOT COMMIT THE CREDENTIALS!"
  exit 1
fi

source credentials

zbctlCmd=zbctl
# zbctl
if [ ! "$(which zbctl)" ]
then
  wget https://github.com/camunda-cloud/zeebe/releases/download/1.2.1/zbctl
  chmod +x zbctl
  zbctlCmnd=./zbctl
fi

$zbctlCmd status
$zbctlCmd create instance zeebe-patch-release-process
