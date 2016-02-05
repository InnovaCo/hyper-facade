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

if [ -n "$publish" ] ; then
  sbt ';set version <<= (version)(_ + ".'${patch_version:-0}'")' 'set testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports")' clean test
elif [ -n "$BUILD_VERSION" ] ; then
  sbt ';set version := "'${BUILD_VERSION:-0}'"' clean test pack -Dsbt.override.build.repos=true
fi
