package com.mycompany.app;

// https://delta-io.github.io/connectors/latest/delta-standalone/api/java/io/delta/standalone/Snapshot.html
import io.delta.standalone.DeltaLog;
import io.delta.standalone.Snapshot;
import io.delta.standalone.data.CloseableIterator;
import io.delta.standalone.data.RowRecord;

// Hadoop stuff
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;

// Generic Avro dependencies
import org.apache.avro.Schema;

// Generic Parquet dependencies
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.hadoop.ParquetWriter;

// Avro->Parquet dependencies
import org.apache.parquet.avro.AvroSchemaConverter;
import org.apache.parquet.avro.AvroParquetWriter;

public class App {
    public static void printSnapshotDetails(String title, Snapshot snapshot) {
        System.out.println("===== " + title + " =====");
        System.out.println("version: " + snapshot.getVersion());
        System.out.println("number data files: " + snapshot.getAllFiles().size());
        System.out.println("data files:");
        snapshot.getAllFiles().forEach(file -> System.out.println(file.getPath()));

        CloseableIterator<RowRecord> iter = snapshot.open();

        System.out.println("\ndata rows:");
        RowRecord row = null;
        int numRows = 0;
        while (iter.hasNext()) {
            row = iter.next();
            numRows++;

            Long c1 = row.isNullAt("c1") ? null : row.getLong("c1");
            Long c2 = row.isNullAt("c2") ? null : row.getLong("c2");
            String c3 = row.getString("c3");
            System.out.println(c1 + " " + c2 + " " + c3);
        }
        System.out.println("\nnumber rows: " + numRows);
        System.out.println("data schema:");
        System.out.println(row.getSchema().getTreeString());
        System.out.println("\n");
    }

    public static void main(String[] args) {
        // = = = = = = = = = = = =
        // Read Demo
        // = = = = = = = = = = = =
        String ReadPath = "/tmp/delta_standalone_read";
        System.out.println(MessageFormat.format("Reading pre-created Delta Files From: {0}", ReadPath));

        DeltaLog log = DeltaLog.forTable(new Configuration(), ReadPath);

        printSnapshotDetails("current snapshot", log.snapshot());
        printSnapshotDetails("version 0 snapshot", log.getSnapshotForVersionAsOf(0));
        printSnapshotDetails("version 1 snapshot", log.getSnapshotForVersionAsOf(1));
        printSnapshotDetails("version 2 snapshot", log.getSnapshotForVersionAsOf(2));

        // = = = = = = = = = = = =
        // Write Demo
        // = = = = = = = = = = = =
        String Writepath = "/tmp/delta_standalone_write";
        System.out.println(MessageFormat.format("Creating Parquet To: {0}", Writepath));

        File pathAsFile = new File(Writepath);

        // Create directory if it doesn't exist
        if (!Files.exists(Paths.get(Writepath))) {
            pathAsFile.mkdir();
        }

        // Delete all files in directory if they exist
        for (File file : pathAsFile.listFiles()) {
            file.delete();
        }

        Schema avroSchema = UserRank.getClassSchema();
        MessageType parquetSchema = new AvroSchemaConverter().convert(avroSchema);

        UserRank dataToWrite[] = new UserRank[] {
                new UserRank(1, 3),
                new UserRank(2, 0),
                new UserRank(3, 100)
        };

        Path filePath = new Path(Writepath + "/example.parquet");
        int pageSize = 65535;

        // Write to parquet with AvroParquetWriter
        try (
                ParquetWriter<UserRank> writer = AvroParquetWriter.<UserRank>builder(filePath)
                    .withSchema(avroSchema)
                    .withCompressionCodec(CompressionCodecName.SNAPPY)
                    .withPageSize(pageSize)
                    .withDictionaryEncoding(true)
                    .build()) {
            for (UserRank userRank : dataToWrite) {
                writer.write(userRank);
            }
        } catch (java.io.IOException e) {
            System.out.println(String.format("Error writing parquet file %s", e.getMessage()));
            e.printStackTrace();
        }
    }
}
