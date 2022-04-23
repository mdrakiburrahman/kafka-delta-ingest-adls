package com.microsoft.kdi;

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

public class KDI {
    /**
     *  Generate stats about the Delta table
     *  - Number of rows
     *  - Schema
     */
    public static void printSnapshotDetails(String title, Snapshot snapshot) {
        System.out.println("\n===== " + title + " =====\n");
        System.out.println("version: " + snapshot.getVersion());
        System.out.println("number data files: " + snapshot.getAllFiles().size());
        System.out.println("data files:");
        snapshot.getAllFiles().forEach(file -> System.out.println(file.getPath()));

        CloseableIterator<RowRecord> iter = snapshot.open();
        RowRecord row = null;
        int numRows = 0;
        while (iter.hasNext()) { // Get number of rows
            row = iter.next();
            numRows++;
            // System.out.println(row.toString());
        }
        System.out.println("\nnumber of rows: " + numRows);
        System.out.println("data schema:");
        System.out.println(row.getSchema().getTreeString());
        System.out.println("\n");
    }

    /**  
     * Generate the name of a parquet file
     * 
     * For example, if directory is empty: 
     *  
     *    part-00000-07bdefde-6514-4aee-a0f7-e124fea7955a-c000.snappy.parquet
     * 
     * Whereas if a file already exists that has part-00000-...snappy.parquet, then the next file will be:
     * 
     *    part-00001-5dc5f78d-38d1-449e-b72a-8c7d6cee1155-c000.snappy.parquet
     */ 

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

    /**  
     * Creates a parquet file and INSERTs into a Delta table
     */ 
    public static void WriteToDelta(String WritePath, Schema avroSchema, UserRank dataToWrite[], StructType schema)
    {
        File pathAsFile = new File(WritePath);
        
        // Create directory if it doesn't exist
        if (!Files.exists(Paths.get(WritePath))) {
            pathAsFile.mkdir();
        }

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

        // Add the NewFile to Delta Table - creates table if not exists
        try {
            OptimisticTransaction txn = log.startTransaction(); //Start a new transaction
            Metadata metadata = Metadata.builder().schema(schema).build();

            // If Delta table does not exist, add schema
            // We don't want to double add this basically
            if (!(log.snapshot().getVersion() > -1)) {
                txn.updateMetadata(metadata);
            }

            // Find parquet files that match the filename (which is unique)
            // We keep it this way so if we want to add more files later, we can just add them to the list of patterns
            FileSystem fs = DeltaPath.getFileSystem(conf);
            List<FileStatus> files = Arrays.stream(fs.listStatus(DeltaPath))
                    .filter(f -> f.isFile() && f.getPath().getName().equals(NewFile))
                    .collect(Collectors.toList());
            
            // Generate Delta "AddFiles"
            List<AddFile> addFiles = files.stream().map(file -> {
                return new AddFile(
                        // if targetPath is not a prefix, relativize returns the path unchanged
                        DeltaPath.toUri().relativize(file.getPath().toUri()).toString(),    // path
                        Collections.emptyMap(),                                             // partitionValues
                        file.getLen(),                                                      // size
                        file.getModificationTime(),                                         // modificationTime
                        true,                                                               // dataChange
                        null,                                                               // stats
                        null                                                                // tags
                );
            }).collect(Collectors.toList());

            final String engineInfo = "kdi-adls";
            txn.commit(addFiles, new Operation(Operation.Name.WRITE), engineInfo);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // = = = = = = 
        // Write Demo
        // = = = = = = 
        String WritePath = "/tmp/delta_standalone_write";
        System.out.println(MessageFormat.format("Creating Parquet To: {0}", WritePath));

        // Hard code schema for now - it's going to be static for Kafka anyway so we don't need to worry about it
        StructType schema = new StructType()
            .add("userId", new IntegerType())
            .add("rank", new IntegerType());

        UserRank dataToWrite[] = new UserRank[] {
            new UserRank(1, 3),
            new UserRank(2, 0),
            new UserRank(3, 100)
        };

        WriteToDelta(WritePath, UserRank.getClassSchema(), dataToWrite, schema);

        // = = = = = = 
        // Read Demo
        // = = = = = = 
        System.out.println(MessageFormat.format("Reading Delta Files From: {0}", WritePath));
        DeltaLog kdi_read_log = DeltaLog.forTable(new Configuration(), WritePath);

        printSnapshotDetails("KDI table", kdi_read_log.snapshot());

        // = = = = = = = = = =
        // Read from ADLS demo
        // = = = = = = = = = =
        Configuration conf = new Configuration();

        // ADLS Config
        conf.set(
            MessageFormat.format("fs.azure.account.auth.type.{0}.dfs.core.windows.net", 
            System.getenv("ADLS_STORAGE_ACCOUNT_NAME")),
            "OAuth");
        conf.set(
            MessageFormat.format("fs.azure.account.oauth.provider.type.{0}.dfs.core.windows.net", 
            System.getenv("ADLS_STORAGE_ACCOUNT_NAME")),
            "org.apache.hadoop.fs.azurebfs.oauth2.ClientCredsTokenProvider");
        conf.set(
            MessageFormat.format("fs.azure.account.oauth2.client.id.{0}.dfs.core.windows.net", 
            System.getenv("ADLS_STORAGE_ACCOUNT_NAME")),
            System.getenv("ADLS_CLIENT_ID"));
        conf.set(
            MessageFormat.format("fs.azure.account.oauth2.client.secret.{0}.dfs.core.windows.net", 
            System.getenv("ADLS_STORAGE_ACCOUNT_NAME")),
            System.getenv("ADLS_CLIENT_SECRET"));
        conf.set(
            MessageFormat.format("fs.azure.account.oauth2.client.endpoint.{0}.dfs.core.windows.net", 
            System.getenv("ADLS_STORAGE_ACCOUNT_NAME")),
            MessageFormat.format("https://login.microsoftonline.com/{0}/oauth2/token", 
            System.getenv("ADLS_CLIENT_TENANT")));
        conf.set(
        "io.delta.standalone.LOG_STORE_CLASS_KEY", 
        "io.delta.standalone.internal.storage.AzureLogStore");

        String DeltaTablePath = "kafka/scene_raw";

        Path DeltaPath = new Path(
            MessageFormat.format("abfs://{0}@{1}.dfs.core.windows.net/{2}", 
                System.getenv("ADLS_STORAGE_CONTAINER_NAME"), 
                System.getenv("ADLS_STORAGE_ACCOUNT_NAME"),
                DeltaTablePath));
        DeltaLog adls_read_log = DeltaLog.forTable(conf, DeltaPath);
        
        printSnapshotDetails("ADLS table", adls_read_log.snapshot());

    }
}

