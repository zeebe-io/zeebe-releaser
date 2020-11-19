#!/bin/bash
set -exuo pipefail

export ZEEBE_ADDRESS='fe00577d-ac32-4615-a780-090f22281d3f.zeebe.ultrawombat.com:443'
export ZEEBE_CLIENT_ID='BHg4xZHB1NGxBaVsWBvHgJEUbqg.LMAu'
export ZEEBE_CLIENT_SECRET='u4DcSoJj9thk3I1qBYWIHwG1a3uQCT4TOPIV7.btXzwOwrRKgD~~oe7oQMNliwXI'
export ZEEBE_AUTHORIZATION_SERVER_URL='https://login.cloud.ultrawombat.com/oauth/token'

# zbctl

if [ ! -f zbctl ]
then
  wget https://github.com/zeebe-io/zeebe/releases/download/0.24.5/zbctl
fi

chmod +x zbctl

zbctl status

zbctl create instance zeebe-repeating-release
