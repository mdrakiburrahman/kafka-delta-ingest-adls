package com.microsoft.kdi;

// Jave stuff
import static java.lang.System.*;
import java.time.*;
import java.util.*;

// Kafka stuff
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.*;

// Delta stuff
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

// ADLS stuff
import com.azure.storage.file.datalake.DataLakeFileSystemClient;
import com.azure.storage.file.datalake.DataLakeServiceClient;
import com.azure.storage.file.datalake.DataLakeServiceClientBuilder;
import com.azure.storage.file.datalake.models.ListPathsOptions;
import com.azure.storage.file.datalake.models.PathItem;
import com.azure.core.http.rest.PagedIterable;

// Azure AD stuff
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;

// Logger stuff
import org.apache.log4j.BasicConfigurator;

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

public final class KDI {
    /**
     * Generate stats about the Delta table
     * - Number of rows
     * - Schema
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
     * Generate the name of a parquet file : Local
     * 
     * For example, if directory is empty:
     * 
     * part-00000-07bdefde-6514-4aee-a0f7-e124fea7955a-c000.snappy.parquet
     * 
     * Whereas if a file already exists that has part-00000-...snappy.parquet, then
     * the next file will be:
     * 
     * part-00001-5dc5f78d-38d1-449e-b72a-8c7d6cee1155-c000.snappy.parquet
     */
    public static String GenerateParquetFileNameLocal(File pathAsFile) {
        String FileName = "";

        // Generate random Guid
        String Guid = java.util.UUID.randomUUID().toString();

        if (pathAsFile.isDirectory()) {
            File[] files = pathAsFile.listFiles();
            if (files.length == 0) // If no files exist, then use the default name
            {
                FileName = "part-00000-" + Guid + "-c000.snappy.parquet";
            } else // Otherwise, find the next available file name
            {
                // Get list of all files in directory
                String[] fileNames = new String[files.length];
                for (int i = 0; i < files.length; i++) {
                    // if name does not match "_delta_log" or end with ".crc", append to list
                    if (!files[i].getName().equals("_delta_log") && !files[i].getName().endsWith(".crc")) {
                        fileNames[i] = files[i].getName();
                    }
                }
                // Remove all null entries in fileNames
                fileNames = java.util.Arrays.stream(fileNames).filter(s -> s != null).toArray(String[]::new);

                // Find file that has the highest integer in part-XXXXX
                int max = Integer.MIN_VALUE;
                for (int i = 0; i < fileNames.length; i++) {
                    String[] fileNameParts = fileNames[i].split("-");
                    int fileNamePart = Integer.parseInt(fileNameParts[1]);
                    if (fileNamePart > max) {
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
     * Generate the name of a parquet file : ADLS
     */
    public static String GenerateParquetFileNameADLS(DataLakeFileSystemClient adls_fileSystemClient, String WriteDir) {
        String FileName = "";

        // Generate random Guid
        String Guid = java.util.UUID.randomUUID().toString();

        // For looping over all files in directory
        ListPathsOptions options = new ListPathsOptions().setPath(WriteDir);
        PagedIterable<PathItem> pagedIterable = adls_fileSystemClient.listPaths(options, null);
        java.util.Iterator<PathItem> iterator = pagedIterable.iterator();

        if (!iterator.hasNext()) // If no files exist, then use the default name
        {
            FileName = "part-00000-" + Guid + "-c000.snappy.parquet";
        } else { // At least one file exists
            // Find file that has the highest integer in part-XXXXX
            int max = Integer.MIN_VALUE;

            while (iterator.hasNext()) {
                PathItem item = iterator.next();

                // Split item.getName by "/" and return the last part
                String[] fullName = item.getName().split("/");
                String fileName = fullName[fullName.length - 1];

                // If name does not match "_delta_log" or end with ".crc", consider
                if (!fileName.equals("_delta_log") && !fileName.endsWith(".crc")) {
                    // Get the integer part of the file name and update max
                    String[] fileNameParts = fileName.split("-");
                    int fileNamePart = Integer.parseInt(fileNameParts[1]);
                    if (fileNamePart > max) {
                        max = fileNamePart;
                    }
                }
            }
            // Generate new file name
            FileName = "part-" + String.format("%05d", ++max) + "-" + Guid + "-c000.snappy.parquet";
        }
        return FileName;
    }

    /**
     * Suported storages
     */
    public enum Storage {
        LOCAL,
        ADLS
    }

    /**
     * Creates a parquet file and INSERTs into a Delta table - handles LOCAL and
     * ADLS
     */
    public static void WriteToDelta(Storage storageType, DataLakeFileSystemClient adls_fileSystemClient, DeltaLog log,
            Configuration conf, Path WritePath, String WriteDir, UserRank dataToWrite[], Schema avroSchema,
            StructType javaSchema) {
        // Common Variables
        Path filePath = null;
        final String NewFile;

        // Deal with LOCAL and ADLS
        if (storageType == Storage.LOCAL) {
            // Create directory if not exists - we need this for our Parquet unique name
            // generator
            File pathAsFile = new File(WriteDir);
            // Create directory if it doesn't exist
            if (!Files.exists(Paths.get(WriteDir))) {
                pathAsFile.mkdir();
            }
            // Variables for both routes
            NewFile = GenerateParquetFileNameLocal(new File(WriteDir));
            filePath = new Path("/" + NewFile);
            filePath = Path.mergePaths(WritePath, filePath);
        } else if (storageType == Storage.ADLS) {
            // Our directory is already created at this point in ADLS
            NewFile = GenerateParquetFileNameADLS(adls_fileSystemClient, WriteDir);
            filePath = new Path("/" + NewFile);
            filePath = Path.mergePaths(WritePath, filePath);
        } else {
            throw new IllegalArgumentException("Storage type not supported");
        }

        // Write to parquet with AvroParquetWriter
        try (
                ParquetWriter<UserRank> writer = AvroParquetWriter.<UserRank>builder(filePath)
                        .withSchema(avroSchema)
                        .withCompressionCodec(CompressionCodecName.SNAPPY)
                        .withPageSize(65535)
                        .withDictionaryEncoding(true)
                        .build()) {
            for (UserRank userRank : dataToWrite) {
                writer.write(userRank);
            }
        } catch (java.io.IOException e) {
            System.out.println(String.format("Error writing parquet file %s", e.getMessage()));
            e.printStackTrace();
        }

        // Commit Delta Table Transaction - add the NewFile to Delta Table - creates
        // table if not exists
        try {
            OptimisticTransaction txn = log.startTransaction(); // Start a new transaction
            Metadata metadata = Metadata.builder().schema(javaSchema).build();

            // If Delta table does not exist, add schema
            // We don't want to double add this because that will cause an error during
            // reads
            if (!(log.snapshot().getVersion() > -1)) {
                txn.updateMetadata(metadata);
            }

            // Find parquet files that match the filename (which is unique)
            // We keep it this way so if we want to add more files later, we can just add
            // them to the list of patterns
            FileSystem fs = WritePath.getFileSystem(conf);
            List<FileStatus> files = Arrays.stream(fs.listStatus(WritePath))
                    .filter(f -> f.isFile() && f.getPath().getName().equals(NewFile))
                    .collect(Collectors.toList());

            // Generate Delta "AddFiles"
            List<AddFile> addFiles = files.stream().map(file -> {
                return new AddFile(
                        // if targetPath is not a prefix, relativize returns the path unchanged
                        WritePath.toUri().relativize(file.getPath().toUri()).toString(), // path
                        Collections.emptyMap(), // partitionValues
                        file.getLen(), // size
                        file.getModificationTime(), // modificationTime
                        true, // dataChange
                        null, // stats
                        null // tags
                );
            }).collect(Collectors.toList());

            final String engineInfo = "kdi-adls";
            txn.commit(addFiles, new Operation(Operation.Name.WRITE), engineInfo);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Generate an ADLS Hadoop config object
     */
    public static Configuration GenerateADLSConfig(String accountName, String clientId, String clientSecret,
            String tenantId) {
        Configuration conf = new Configuration();

        // ADLS Config
        conf.set(
                MessageFormat.format("fs.azure.account.auth.type.{0}.dfs.core.windows.net", accountName),
                "OAuth");
        conf.set(
                MessageFormat.format("fs.azure.account.oauth.provider.type.{0}.dfs.core.windows.net", accountName),
                "org.apache.hadoop.fs.azurebfs.oauth2.ClientCredsTokenProvider");
        conf.set(
                MessageFormat.format("fs.azure.account.oauth2.client.id.{0}.dfs.core.windows.net", accountName),
                clientId);
        conf.set(
                MessageFormat.format("fs.azure.account.oauth2.client.secret.{0}.dfs.core.windows.net", accountName),
                clientSecret);
        conf.set(
                MessageFormat.format("fs.azure.account.oauth2.client.endpoint.{0}.dfs.core.windows.net", accountName),
                MessageFormat.format("https://login.microsoftonline.com/{0}/oauth2/token",
                        tenantId));
        conf.set(
                "io.delta.standalone.LOG_STORE_CLASS_KEY",
                "io.delta.standalone.internal.storage.AzureLogStore");

        return conf;
    }

    /**
     * Generate an ADLS client
     */
    static public DataLakeServiceClient GetDataLakeServiceClient(String accountName, String clientId,
            String ClientSecret, String tenantID) {
        String endpoint = "https://" + accountName + ".dfs.core.windows.net";

        ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
                .clientId(clientId)
                .clientSecret(ClientSecret)
                .tenantId(tenantID)
                .build();

        DataLakeServiceClientBuilder builder = new DataLakeServiceClientBuilder();
        return builder.credential(clientSecretCredential).endpoint(endpoint).buildClient();
    }

    /**
     * Create an ADLS directory if not exists
     */
    static public DataLakeFileSystemClient CreateDirectoryIfNotExists(DataLakeServiceClient serviceClient,
            String fileSystemName, String directoryName) {
        DataLakeFileSystemClient fileSystemClient = serviceClient.getFileSystemClient(fileSystemName);

        if (!(fileSystemClient.getDirectoryClient(directoryName).exists())) {
            fileSystemClient.createDirectory(directoryName);
        }

        return fileSystemClient;
    }

    public static void main(String[] args) {
        // = = = =
        // Logger
        // = = = =
        BasicConfigurator.configure();

        // = = = = = =
        // Kafka stuff
        // = = = = = =
        final var topic = System.getenv("KAFKA_TOPIC");
        final var broker = System.getenv("KAFKA_BROKER_ADDRESS");
        final var consumer_self = System.getenv("KAFKA_CONSUMER_NAME_SELF");

        System.out.println("\n==========================================================");
        System.out.println(MessageFormat.format("➡  Kafka Broker: {0}", broker));
        System.out.println(MessageFormat.format("➡  Kafka Topic: {0}", topic));
        System.out.println(MessageFormat.format("➡  My Consumer Name: {0}", consumer_self));
        System.out.println("==========================================================\n");

        // http://people.apache.org/~nehanarkhede/kafka-0.9-producer-javadoc/doc/org/apache/kafka/clients/consumer/ConsumerConfig.html
        final Map<String, Object> config = Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.GROUP_ID_CONFIG, consumer_self,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);

        try (var consumer = new KafkaConsumer<String, String>(config)) {
            consumer.subscribe(Set.of(topic));

            while (true) {
                final var records = consumer.poll(Duration.ofMillis(100));
                if (records.count() != 0)
                {
                    System.out.println("⏩ Got " + records.count() + " record(s)");
                }

                for (var record : records) {
                    out.format("\n-> Got record with topic %s%n\n", record.topic());
                    out.format("-> Got record with key %s%n\n", record.key());
                    out.format("-> Got record with value %s%n\n", record.value());
                    out.format("-> Got record with offset %s%n\n", record.offset());
                    out.format("-> Got record with partition %s%n\n", record.partition());
                    out.format("-> Got record with timestamp %s%n\n\n", record.timestamp());
                }
                consumer.commitAsync();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // // = = = = = = = = = = = = = =
        // // Generate fake data + schema
        // // = = = = = = = = = = = = = =
        // StructType JavaSchema = new StructType()
        // .add("userId", new IntegerType())
        // .add("rank", new IntegerType());

        // UserRank dataToWrite[] = new UserRank[] {
        // new UserRank(1, 3),
        // new UserRank(2, 0),
        // new UserRank(3, 100)
        // };

        // // = = = = = = = = = = = = = = = =
        // // Generate Hadoop Config objects
        // // = = = = = = = = = = = = = = = =
        // Configuration local_config = new Configuration();

        // Configuration adls_config = GenerateADLSConfig(
        // System.getenv("ADLS_STORAGE_ACCOUNT_NAME"),
        // System.getenv("ADLS_CLIENT_ID"),
        // System.getenv("ADLS_CLIENT_SECRET"),
        // System.getenv("ADLS_CLIENT_TENANT"));

        // // = = = = = = = = = =
        // // ADLS Client object
        // // = = = = = = = = = =
        // DataLakeServiceClient adls_client = GetDataLakeServiceClient(
        // System.getenv("ADLS_STORAGE_ACCOUNT_NAME"),
        // System.getenv("ADLS_CLIENT_ID"),
        // System.getenv("ADLS_CLIENT_SECRET"),
        // System.getenv("ADLS_CLIENT_TENANT"));

        // // = = = = = = = = = =
        // // Paths: Local + ADLS
        // // = = = = = = = = = =
        // String Dir_local = "/tmp/delta_standalone_write";
        // String Dir_adls = "tmp/delta_standalone_write";

        // Path Path_local = new Path(Dir_local);
        // Path Path_adls = new Path(
        // MessageFormat.format("abfs://{0}@{1}.dfs.core.windows.net/{2}",
        // System.getenv("ADLS_STORAGE_CDC_CONTAINER_NAME"),
        // System.getenv("ADLS_STORAGE_ACCOUNT_NAME"),
        // Dir_adls));

        // // = = = = = = =
        // // Write: Local
        // // = = = = = = =
        // System.out.println(MessageFormat.format("Writing Delta To: {0}", Dir_local));
        // DeltaLog local_write_log = DeltaLog.forTable(local_config, Path_local);
        // WriteToDelta(Storage.LOCAL, null, local_write_log, local_config, Path_local,
        // Dir_local, dataToWrite, UserRank.getClassSchema(), JavaSchema);

        // // = = = = = =
        // // Read: Local
        // // = = = = = =
        // System.out.println(MessageFormat.format("Reading Delta Files From: {0}",
        // Dir_local));
        // DeltaLog local_read_log = DeltaLog.forTable(local_config, Dir_local);
        // printSnapshotDetails("Local table", local_read_log.snapshot());

        // // = = = = = = =
        // // Write: ADLS
        // // = = = = = = =
        // System.out.println(MessageFormat.format("Writing Delta To: {0}", Dir_adls));
        // DataLakeFileSystemClient adls_fileSystemClient =
        // CreateDirectoryIfNotExists(adls_client,
        // System.getenv("ADLS_STORAGE_CDC_CONTAINER_NAME"), Dir_adls);
        // DeltaLog adls_write_log = DeltaLog.forTable(adls_config, Path_adls);
        // WriteToDelta(Storage.ADLS, adls_fileSystemClient, adls_write_log,
        // adls_config, Path_adls, Dir_adls, dataToWrite, UserRank.getClassSchema(),
        // JavaSchema);

        // // = = = = = =
        // // Read: ADLS
        // // = = = = = =
        // System.out.println(MessageFormat.format("Reading Delta Files From: {0}",
        // Dir_adls));
        // DeltaLog adls_read_log = DeltaLog.forTable(adls_config, Path_adls);
        // printSnapshotDetails("ADLS table", adls_read_log.snapshot());

    }
}
