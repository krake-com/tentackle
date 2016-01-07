#!/bin/bash

# collects and sums up statistics info from a server-logfile
# the tab-separated output can be easily imported by excel or similar spreadsheet app
#
# $Id: sumup_statistics.sh 2366 2015-11-19 12:39:42Z svn $


usage()
{
cat << EOF
usage: $0 options

this script extracts sql- or rmi-statistics from a tentackle log file.

OPTIONS:
   -s         extract sql statistics
   -r         extract rmi statistics
   -p         extract pdo statistics
   -o         extract operation statistics
   -n         use locale for number format
   -l <ms>    extract only requests with a lower bound of given milliseconds
   -u <ms>    extract only requests with an upper bound of given milliseconds
   -x <expr>  regex to filter statements/method-names
EOF
}



PATTERN=""
LOWERMS=""
UPPERMS=""
EXPR=""
LOCALEOPT=""

while getopts "noprsl:u:x:" OPTION
do
    case $OPTION in
        s)
        PATTERN="    >SQL-Stats:"
        ;;
        r)
        PATTERN="    >RMI-Stats:"
        ;;
        p)
        PATTERN="    >PDO-Stats:"
        ;;
        o)
        PATTERN="    >OPN-Stats:"
        ;;
        n)
        LOCALEOPT="-N"
        ;;
        l)
        LOWERMS=$OPTARG
        ;;
        u)
        UPPERMS=$OPTARG
        ;;
        x)
        EXPR=$OPTARG
        ;;
        ?)
        usage
        exit
        ;;
    esac
done

if [ "$PATTERN" = "" ]
then
    usage
    exit
fi

shift $(expr $OPTIND - 1 )
FILE=$1

fgrep "$PATTERN" $FILE | grep "$EXPR" | cut -c 17- | awk $LOCALEOPT '{

    ndx = index($0, " x ")
    if (ndx) {
        sql = substr($0, ndx+3)
        min = $1
        max = $2
        ms = $3
        cnt = $6
        dur = ms / cnt
        lowerms = "'"$LOWERMS"'"
        if (lowerms == "") {
            lowerms = -1
        }
        else {
            lowerms += 0
        }
        upperms = "'"$UPPERMS"'"
        if (upperms == "") {
            upperms = -1
        }
        else {
            upperms += 0
        }
        if (lowerms < max && (upperms < 0 || min < upperms)) {
            millis[sql] += ms
            invocs[sql] += cnt
            if (minms[sql] == "" || minms[sql] > min) {
                minms[sql] = min
            }
            if (maxms[sql] == "" || maxms[sql] < max) {
                maxms[sql] = max
            }
        }
    }
}

END {
    printf("ms\tcalls\tms/call\tms/min\tms/max\toperation\n");
    for (sql in millis) {
        totalms = millis[sql]
        calls = invocs[sql]
        printf("%.3f\t%d\t%.3f\t%.3f\t%.3f\t%s\n", totalms, calls, totalms/calls, minms[sql], maxms[sql], sql)
    }
}'

