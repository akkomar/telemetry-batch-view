# telemetry-batch-view

This is a Scala application to build derived datasets, also known as [batch views](http://robertovitillo.com/2016/01/06/batch-views/), of [Telemetry](https://wiki.mozilla.org/Telemetry) data.

[![Build Status](https://travis-ci.org/mozilla/telemetry-batch-view.svg?branch=master)](https://travis-ci.org/mozilla/telemetry-batch-view)
[![codecov.io](https://codecov.io/github/mozilla/telemetry-batch-view/coverage.svg?branch=master)](https://codecov.io/github/mozilla/telemetry-batch-view?branch=master)
[![CircleCi Status](https://circleci.com/gh/mozilla/telemetry-batch-view.svg?style=shield&circle-token=ca31167ac42cc39f898e37facb93db70c0af8691)](https://circleci.com/gh/mozilla/telemetry-batch-view)

Raw JSON [pings](https://ci.mozilla.org/job/mozilla-central-docs/Tree_Documentation/toolkit/components/telemetry/telemetry/pings.html) are stored on S3 within files containing [framed Heka records](https://hekad.readthedocs.org/en/latest/message/index.html#stream-framing). Reading the raw data in through e.g. Spark can be slow as for a given analysis only a few fields are typically used; not to mention the cost of parsing the JSON blobs. Furthermore, Heka files might contain only a handful of records under certain circumstances.

Defining a derived [Parquet](https://parquet.apache.org/) dataset, which uses a columnar layout optimized for analytics workloads, can drastically improve the performance of analysis jobs while reducing the space requirements. A derived dataset might, and should, also perform heavy duty operations common to all analysis that are going to read from that dataset (e.g., parsing dates into normalized timestamps).

### Adding a new derived dataset

See the [views](https://github.com/mozilla/telemetry-batch-view/tree/master/src/main/scala/views) folder for examples of jobs that create derived datasets.

See the [Firefox Data Documentation](https://mozilla.github.io/firefox-data-docs/datasets/reference.html) for more information about the individual derived datasets.
For help finding the right dataset for your analysis, see
[Choosing a Dataset](https://mozilla.github.io/firefox-data-docs/concepts/choosing_a_dataset.html).

### Development
There are two possible workflows for hacking on telemetry-batch-view: you can either create a docker container for building the package and running tests, or import the project into IntelliJ's IDEA.

To run the docker tests, just use the provided `Dockerfile` to build a container, then use the `run-sbt.sh` script to run tests inside it:

    docker build -t telemetry-batch-view .
    ./run-sbt.sh

You may need to increase the amount of memory allocated to Docker for this to work, as some of the tests are very memory hungry at present. At least 4 gigabytes is recommended.

You can also pass arguments to sbt (the scala build tool we use for running the tests) through the runtests.sh. For example, to run only the addon tests, try:

    ./runtests.sh "test-only com.mozilla.telemetry.AddonsViewTest"

If you wish to import the project into IntelliJ IDEA, apply the following changes to `Preferences` -> `Languages & Frameworks` -> `Scala Compile Server`:

- JVM maximum heap size, MB: `2048`
- JVM parameters: `-server -Xmx2G -Xss4M`

Note that the first time the project is opened it takes some time to download all the dependencies.

### Generating Datasets

See the [documentation for specific views](https://github.com/mozilla/telemetry-batch-view/tree/master/docs) for details about running/generating them.

For example, to create a longitudinal view locally:
```bash
sbt "run-main com.mozilla.telemetry.views.LongitudinalView --from 20160101 --to 20160701 --bucket telemetry-test-bucket"
```

For distributed execution we pack all of the classes together into a single JAR and submit it to the cluster:
```bash
sbt assembly
spark-submit --master yarn --deploy-mode client --class com.mozilla.telemetry.views.LongitudinalView target/scala-2.11/telemetry-batch-view-*.jar --from 20160101 --to 20160701 --bucket telemetry-test-bucket
```

In the future, we will modify airflow jobs to actually pull the jar from s3 rather than git checkout and sbt assembly
Something like:
```bash
wget https://s3-us-west-2.amazonaws.com/net-mozaws-data-us-west-2-ops-mavenrepo/snapshots/telemetry-batch-view/telemetry-batch-view/1.1/telemetry-batch-view-1.1.jar
```

### Caveats
If you run into memory issues during compilation time or running the test suite, issue the following command before running sbt:
```bash
export _JAVA_OPTIONS="-Xms4G -Xmx4G -Xss4M -XX:MaxMetaspaceSize=256M"
```

**Slow tests**
By default slow tests are not run when using `sbt test`. To run slow tests use `./runtests.sh slow:test` (or just `sbt slow:test` outside of the Docker environment).

**Running on Windows**

Executing scala/Spark jobs could be particularly problematic on this platform. Here's a list of common issues and the relative solutions:

**Issue:** *I see a weird reflection error or an odd exception when trying to run my code.*

This is probably due to *winutils* being missing or not found. Winutils are needed by HADOOP and can be downloaded from [here](https://github.com/steveloughran/winutils).

**Issue:** *java.net.URISyntaxException: Relative path in absolute URI: ...*

This means that *winutils* cannot be found or that Spark cannot find a valid warehouse directory. Add the following line at the beginning of your entry function to make it work:

```scala
System.setProperty("hadoop.home.dir", "C:\\path\\to\\winutils")
System.setProperty("spark.sql.warehouse.dir", "file:///C:/somereal-dir/spark-warehouse")
```

**Issue:** *The root scratch dir: /tmp/hive on HDFS should be writable. Current permissions are: ---------*

See [SPARK-10528](https://issues.apache.org/jira/browse/SPARK-10528). Run "winutils chmod 777 /tmp/hive" from a privileged prompt to make it work.

Any commits to master should also trigger a circleci build that will do the sbt publishing for you to our local maven repo in s3.
