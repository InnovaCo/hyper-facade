#!/bin/bash -e

for param in "$@"
	do case $param in
		--publish*)
			publish="1"
		;;
		--patch-version=*)
			patch_version="${param#*=}"
		;;
	esac
done

wget http://www.us.apache.org/dist/kafka/0.8.2.1/kafka_2.10-0.8.2.1.tgz -O kafka.tgz
mkdir -p kafka && tar xzf kafka.tgz -C kafka --strip-components 1
kafka/bin/zookeeper-server-start.sh kafka/config/zookeeper.properties &
ZOOKEEPER_PID=$!

kafka/bin/kafka-server-start.sh kafka/config/server.properties &
KAFKA_PID=$!

sleep 5

if [ -n "$publish" ] ; then
  sbt ';set version <<= (version)(_ + ".'${patch_version:-0}'")' 'set testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports")' clean test
elif [ -n "$BUILD_VERSION" ] ; then
  sbt ';set version := "'${BUILD_VERSION:-0}'"' clean test pack -Dsbt.override.build.repos=true
fi

kill $KAFKA_PID
sleep 5
kill $ZOOKEEPER_PID
