#!/bin/bash

export USER_ID=$(id -u)
export GROUP_ID=$(id -g)
envsubst < ${HOME}/src/resources/passwd.template > /tmp/passwd
export LD_PRELOAD=libnss_wrapper.so
export NSS_WRAPPER_PASSWD=/tmp/passwd
export NSS_WRAPPER_GROUP=/etc/group

echo "hostname=$HOSTNAME" >> /etc/ssmtp/ssmtp.conf

TMPDATE=`date "+%m.%d"`
TMPFILE=/tmp/myscallog.$TMPDATE

echo "To: $RSV_TO" > $TMPFILE
echo "From: $RSV_FROM" >> $TMPFILE
echo "Subject: $RSV_SUBJECT $TMPDATE" >> $TMPFILE
echo "" >> $TMPFILE

echo "----> `date \"+%H:%M:%S\"` Starting sailrsv" &>> $TMPFILE

nohup ${HOME}/jdk-17/bin/java -jar $HOME/app-standalone.jar &>> $TMPFILE

echo "----> `date \"+%H:%M:%S\"` Finished sailrsv" &>> $TMPFILE

cat $TMPFILE | ssmtp $RSV_TO

# uncomment below when running as app rather than cronjob to debug
while true; do sleep 2; done
