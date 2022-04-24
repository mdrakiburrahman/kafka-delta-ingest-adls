# kafka-delta-ingest-adls: Java

## Build and run locally

### Quickstart
Clean up Delta folders:

**Local**
```bash
rm -rf /tmp/delta_standalone_write
```
**ADLS** - perform via Storage Explorer

⭐ Run install, better than package:
```bash
clear && mvn clean install && java -jar target/kdi-java-1.0-SNAPSHOT.jar
```

Run `INSERTs` in [SQL DB in this repo](https://github.com/mdrakiburrahman/debezium-sql-linux) to generate CDC logs into Kafka:

```sql
USE testDB
GO

-- CDC tables
SELECT * FROM cdc.change_tables
SELECT * FROM cdc.dbo_customers_CT
-- Data Tables
SELECT * FROM customers;

-- INSERT statements
USE testDB
GO

DROP PROCEDURE IF EXISTS dbo.RunInserts
GO

CREATE PROCEDURE dbo.RunInserts @Number int
AS
BEGIN
	DECLARE
		@Counter int= 1
	WHILE @Counter< =@Number
	BEGIN
		INSERT INTO customers(first_name,last_name,email)
		VALUES ('Raki','Rahman', CONCAT(NEWID (), '@microsoft.com'));
		PRINT(@Counter)
		SET @Counter= @Counter + 1
	END
END

EXEC dbo.RunInserts 1000 -- <-- Tune as necessary
SELECT COUNT(*) AS num_rows FROM customers;
```

### Other details
Run locally:
```bash
cd /workspaces/kafka-delta-ingest-adls/src/main/java/com/microsoft/app
java App.java
```

Run via Maven with `java -cp`:
```bash
/workspaces/kafka-delta-ingest-adls
mvn clean package
java -cp target/kdi-java-1.0-SNAPSHOT.jar com.microsoft.kdi.KDI
```

Run as fat standalone jar:
```bash
/workspaces/kafka-delta-ingest-adls
mvn clean package
java -jar target/kdi-java-1.0-SNAPSHOT.jar
```

Run via maven:
```bash
mvn clean package
mvn exec:java -D exec.mainClass=com.microsoft.kdi.KDI
```

To pipe logs in case of errors:
```bash
java -jar target/kdi-java-1.0-SNAPSHOT.jar > err.txt 2>&1
```

## Kafka stuff

```bash
# List topics
/opt/kafka/bin/kafka-topics.sh \
--bootstrap-server kafka:9092 \
--list

# Delete a Consumer Group from a topic - useful for reseting stream
/opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:9092 \
  --delete-offsets \
  --group kdi-java-1 \
  --topic server1.dbo.customers
```

## Spark stuff

```bash
/opt/spark/sbin/start-master.sh # http://localhost:8080/ Master UI
/opt/spark/sbin/start-worker.sh spark://$(hostname):7077 # http://localhost:8081/ Workers UI

# Go into Scala Shell with Delta - http://localhost:4040/ Jobs UI
/opt/spark/bin/spark-shell --packages io.delta:delta-core_2.12:1.0.0 \
  --conf "spark.sql.extensions=io.delta.sql.DeltaSparkSessionExtension" \
  --conf "spark.sql.catalog.spark_catalog=org.apache.spark.sql.delta.catalog.DeltaCatalog"

# [OPTIONAL] Go into PySpark Shell
# /opt/spark/bin/pyspark
```
### Write delta files

```scala
val table = "/tmp/delta_standalone_read/"
for (i <- 0 to 2) { // 3 Delta Versions - Scala loops are inclusive of bound
   spark.range(i * 10, (i + 1) * 10)
      .map(x => (x, x % 5, s"foo-${x % 2}"))
      .toDF("c1", "c2", "c3")
      .write
      .mode("append")
      .format("delta")
      .save(table)
}

spark.read.format("delta").load(table).count
// res1: Long = 30

spark.read.format("delta").load(table).limit(5).show
// +---+---+-----+
// | c1| c2|   c3|
// +---+---+-----+
// | 56|  1|foo-0|
// | 57|  2|foo-1|
// | 58|  3|foo-0|
// | 59|  4|foo-1|
// | 60|  0|foo-0|
// +---+---+-----+
```

And we see:
```bash
ls /tmp/delta_standalone_test
# _delta_log
# part-00000-195768ae-bad8-4c53-b0c2-e900e0f3eaee-c000.snappy.parquet
# part-00000-53c3c553-f74b-4384-b9b5-7aa45bc2291b-c000.snappy.parquet
# ..=

ls /tmp/delta_standalone_test/_delta_log/*.json
# /tmp/delta_standalone_test/_delta_log/00000000000000000000.json  /tmp/delta_standalone_test/_delta_log/00000000000000000002.json
# /tmp/delta_standalone_test/_delta_log/00000000000000000001.json
```

### Read delta files

```scala
val table = "/tmp/delta_standalone_write/"

spark.read.format("delta").load(table).count

spark.read.format("delta").load(table).show
```

## Debug locally

1. **Edit:**
   - Open `src/main/java/com/microsoft/app/App.java`.
   - Try adding some code and check out the language features.
   - Notice that the Java extension pack is already installed in the container since the `.devcontainer/devcontainer.json` lists `"vscjava.vscode-java-pack"` as an extension to install automatically when the container is created.
2. **Terminal:** Press <kbd>Ctrl</kbd>+<kbd>Shift</kbd>+<kbd>\`</kbd> and type `uname` and other Linux commands from the terminal window.
3. **Build, Run, and Debug:**
   - Open `src/main/java/com/microsoft/app/App.java`.
   - Add a breakpoint.
   - Press <kbd>F5</kbd> to launch the app in the container.
   - Once the breakpoint is hit, try hovering over variables, examining locals, and more.
4. **Run a Test:**
   - Open `src/test/java/com/microsoft/app/AppTest.java`.
   - Put a breakpoint in a test.
   - Click the `Debug Test` in the Code Lens above the function and watch it hit the breakpoint.
