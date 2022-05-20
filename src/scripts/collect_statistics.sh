#!/bin/sh

usage()
{
cat << EOF
Usage: `basename $0` <options> [<logfile>]

Collects statistics information from a Tentackle log file, tab separated to stdout.

OPTIONS:
   -s           collect sql statistics
   -r           collect rmi statistics
   -p           collect pdo statistics
   -o           collect operation statistics
   -n           use the locale's decimal point character (default is period)
   -l <ms>      collect only executions with at least given milliseconds
   -u <ms>      collect only executions with no more than given milliseconds
   -x <regex>   use regular expression to filter statements/method-names
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
        operation = substr($0, ndx+3)
        min = $1
        max = $2
        ms = $3
        if ($4 == "ms") {
            cnt = $6
        }
        else {  # extended sql with fetch results
            cnt = $12
            extended++
            if (minRows[operation] == "" || minRows[operation] > $4) {
              minRows[operation] = $4
            }
            if (maxRows[operation] == "" || maxRows[operation] < $5) {
              maxRows[operation] = $5
            }
            totRows[operation] += $6
            if (minFetch[operation] == "" || minFetch[operation] > $7) {
              minFetch[operation] = $7
            }
            if (maxFetch[operation] == "" || maxFetch[operation] < $8) {
              maxFetch[operation] = $8
            }
            totFetch[operation] += $9;
        }
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
            millis[operation] += ms
            invocs[operation] += cnt
            if (minms[operation] == "" || minms[operation] > min) {
                minms[operation] = min
            }
            if (maxms[operation] == "" || maxms[operation] < max) {
                maxms[operation] = max
            }
        }
    }
}

END {
    if (extended > 0) {
        printf("calls\ttotal[ms]\tavg[ms]\tmin[ms]\tmax[ms]\texec[ms]\texec-avg[ms]\texec-min[ms]\texec-max[ms]\trows\trows-avg\trows-min\trows-max\tfetch[ms]\tfetch-avg[ms]\tfetch-min[ms]\tfetch-max[ms]\tfetch-row[ms]\t  ---\tSQL statement\n");
        for (operation in millis) {
            exec = millis[operation]
            calls = invocs[operation]
            fetch = totFetch[operation]
            total = exec + fetch
            if (fetch == "") {
                printf("%d\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t\t\t\t\t\t\t\t\t\t\t%s\n",\
                       calls, total, total/calls, minms[operation], maxms[operation], exec, exec/calls, minms[operation], maxms[operation], operation)
            }
            else {
                rows = totRows[operation]
                if (rows > 0) {
                  rowfetch = fetch/rows
                }
                else {
                  rowfetch = 0;
                }
                printf("%d\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%d\t%.3f\t%d\t%d\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t\t%s\n",\
                       calls, total, total/calls, minms[operation]+minFetch[operation], maxms[operation]+maxFetch[operation],\
                       exec, exec/calls, minms[operation], maxms[operation],\
                       rows, rows/calls, minRows[operation], maxRows[operation],\
                       fetch, fetch/calls, minFetch[operation], maxFetch[operation], rowfetch,\
                       operation)
            }
        }
    }
    else {
        printf("calls\ttotal[ms]\tavg[ms]\tmin[ms]\tmax[ms]\t  ---\toperation\n");
        for (operation in millis) {
            totalms = millis[operation]
            calls = invocs[operation]
            printf("%d\t%.3f\t%.3f\t%.3f\t%.3f\t\t%s\n",\
                   calls, totalms, totalms/calls, minms[operation], maxms[operation], operation)
        }
    }
}'

