package com.mycompany.app;

// https://delta-io.github.io/connectors/latest/delta-standalone/api/java/io/delta/standalone/Snapshot.html
import io.delta.standalone.DeltaLog;
import io.delta.standalone.Operation;
import io.delta.standalone.OptimisticTransaction;
import io.delta.standalone.Snapshot;
import io.delta.standalone.actions.*;
import io.delta.standalone.data.CloseableIterator;
import io.delta.standalone.data.RowRecord;
import io.delta.standalone.types.*;

// Hadoop stuff
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;

// Java file stuff
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.*;

// Generic Avro dependencies
import org.apache.avro.Schema;

// Generic Parquet dependencies
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.hadoop.ParquetWriter;

// Avro->Parquet dependencies
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

    // Generate the name of a parquet file
    // For example, if directory is empty: 
    //      part-00000-07bdefde-6514-4aee-a0f7-e124fea7955a-c000.snappy.parquet
    // Whereas if a file already exists that has part-00000-...snappy.parquet, then the next file will be:
    //      part-00001-5dc5f78d-38d1-449e-b72a-8c7d6cee1155-c000.snappy.parquet
    public static String GenerateParquetFileName(File pathAsFile)
    {
        String FileName = "";
        
        // Generate random Guid
        String Guid = java.util.UUID.randomUUID().toString();

        if (pathAsFile.isDirectory())
        {
            File[] files = pathAsFile.listFiles();
            if (files.length == 0) // If no files exist, then use the default name
            {
                FileName = "part-00000-" + Guid + "-c000.snappy.parquet";
            }
            else // Otherwise, find the next available file name
            {
                // Get list of all files in directory
                String[] fileNames = new String[files.length];
                for (int i = 0; i < files.length; i++)
                {
                    // if name does not match "_delta_log" or end with ".crc", append to list
                    if (!files[i].getName().equals("_delta_log") && !files[i].getName().endsWith(".crc"))
                    {
                        fileNames[i] = files[i].getName();
                    }
                }
                // Remove all null entries in fileNames
                fileNames = java.util.Arrays.stream(fileNames).filter(s -> s != null).toArray(String[]::new);

                // Find file that has the highest integer in part-XXXXX
                int max = Integer.MIN_VALUE;
                for (int i = 0; i < fileNames.length; i++)
                {
                    String[] fileNameParts = fileNames[i].split("-");
                    int fileNamePart = Integer.parseInt(fileNameParts[1]);
                    if (fileNamePart > max)
                    {
                        max = fileNamePart;
                    }
                }
                // Generate new file name
                FileName = "part-" + String.format("%05d", ++max) + "-" + Guid + "-c000.snappy.parquet";
            }
        }

        return FileName;
    }

    public static void ReadFromExistingTable (String ReadPath)
    {
        DeltaLog log = DeltaLog.forTable(new Configuration(), ReadPath);

        printSnapshotDetails("current snapshot", log.snapshot());
        printSnapshotDetails("version 0 snapshot", log.getSnapshotForVersionAsOf(0));
        printSnapshotDetails("version 1 snapshot", log.getSnapshotForVersionAsOf(1));
        printSnapshotDetails("version 2 snapshot", log.getSnapshotForVersionAsOf(2));
    }

    public static void main(String[] args) {
        // = = = = = = = = = = = =
        // Read Demo
        // = = = = = = = = = = = =
        String ReadPath = "/tmp/delta_standalone_read";
        System.out.println(MessageFormat.format("Reading pre-created Delta Files From: {0}", ReadPath));
        ReadFromExistingTable(ReadPath);

        // = = = = = = = = = = = =
        // Write Demo
        // = = = = = = = = = = = =
        String WritePath = "/tmp/delta_standalone_write";
        System.out.println(MessageFormat.format("Creating Parquet To: {0}", WritePath));

        File pathAsFile = new File(WritePath);

        // Create directory if it doesn't exist
        if (!Files.exists(Paths.get(WritePath))) {
            pathAsFile.mkdir();
        }

        Schema avroSchema = UserRank.getClassSchema();

        UserRank dataToWrite[] = new UserRank[] {
                new UserRank(1, 3),
                new UserRank(2, 0),
                new UserRank(3, 100)
        };
    
        String NewFile = GenerateParquetFileName(new File(WritePath)); // <- Change to Write folder!

        Path filePath = new Path(WritePath + "/" + NewFile);
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

        // Commit Delta Table Transaction
        Path DeltaPath = new Path(WritePath);
        Configuration conf = new Configuration();
        DeltaLog log = DeltaLog.forTable(conf, DeltaPath);

        // Hard code schema for now - it's going to be static for Kafka anyway so we don't need to worry about it
        StructType schema = new StructType()
                    .add("userId", new IntegerType())
                    .add("rank", new IntegerType());

        // Create Delta Table if not exists
        // ELSE, JUST ADD TO IT
        // TODO: If not exists part
        
        try {
            // Find parquet files
            FileSystem fs = DeltaPath.getFileSystem(conf);
            List<FileStatus> files = Arrays.stream(fs.listStatus(DeltaPath))
                    .filter(f -> f.isFile() && f.getPath().getName().endsWith(".parquet"))
                    .collect(Collectors.toList());
            
            // Generate Delta "AddFiles"
            List<AddFile> addFiles = files.stream().map(file -> {
                return new AddFile(
                        // if targetPath is not a prefix, relativize returns the path unchanged
                        DeltaPath.toUri().relativize(file.getPath().toUri()).toString(),     // path
                        Collections.emptyMap(),                                             // partitionValues
                        file.getLen(),                                                      // size
                        file.getModificationTime(),                                         // modificationTime
                        true,                                                               // dataChange
                        null,                                                               // stats
                        null                                                                // tags
                );
            }).collect(Collectors.toList());

            Metadata metadata = Metadata.builder().schema(schema).build();

            OptimisticTransaction txn = log.startTransaction();
            txn.updateMetadata(metadata);
            final String engineInfo = "kdi-adls";
            txn.commit(addFiles, new Operation(Operation.Name.CONVERT), engineInfo); // <- Change to ADD

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
