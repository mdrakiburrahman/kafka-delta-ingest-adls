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

// ADLS stuff
import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.file.datalake.DataLakeDirectoryClient;
import com.azure.storage.file.datalake.DataLakeFileClient;
import com.azure.storage.file.datalake.DataLakeFileSystemClient;
import com.azure.storage.file.datalake.DataLakeServiceClient;
import com.azure.storage.file.datalake.DataLakeServiceClientBuilder;
import com.azure.storage.file.datalake.models.ListPathsOptions;
import com.azure.storage.file.datalake.models.PathItem;
import com.azure.storage.file.datalake.models.AccessControlChangeCounters;
import com.azure.storage.file.datalake.models.AccessControlChangeResult;
import com.azure.storage.file.datalake.models.AccessControlType;
import com.azure.storage.file.datalake.models.PathAccessControl;
import com.azure.storage.file.datalake.models.PathAccessControlEntry;
import com.azure.storage.file.datalake.models.PathPermissions;
import com.azure.storage.file.datalake.models.PathRemoveAccessControlEntry;
import com.azure.storage.file.datalake.models.RolePermissions;
import com.azure.storage.file.datalake.options.PathSetAccessControlRecursiveOptions;

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

public class KDI {
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
     * Generate the name of a parquet file
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
     * Creates a parquet file and INSERTs into a Delta table
     */
    public static void WriteToDeltaLocal(DeltaLog log, Configuration conf, Path WritePath, String WriteDir,
            UserRank dataToWrite[], Schema avroSchema, StructType javaSchema) {

        // Create directory if not exists - we need this for our Parquet unique name
        // generator
        File pathAsFile = new File(WriteDir);
        // Create directory if it doesn't exist
        if (!Files.exists(Paths.get(WriteDir))) {
            pathAsFile.mkdir();
        }

        // Variables for both routes
        String NewFile = GenerateParquetFileNameLocal(new File(WriteDir));
        Path filePath = new Path("/" + NewFile);
        filePath = Path.mergePaths(WritePath, filePath);

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
    static public DataLakeDirectoryClient CreateDirectoryIfNotExists(DataLakeServiceClient serviceClient,
            String fileSystemName, String directoryName) {
        DataLakeFileSystemClient fileSystemClient = serviceClient.getFileSystemClient(fileSystemName);

        if (fileSystemClient.getDirectoryClient(directoryName).exists()) {
            return fileSystemClient.getDirectoryClient(directoryName);
        } else {
            return fileSystemClient.createDirectory(directoryName);
        }
    }

    public static void main(String[] args) {
        // = = = =
        // Logger
        // = = = =
        BasicConfigurator.configure();

        // = = = = = = = = = = = = = = = =
        // Generate Hadoop Config objects
        // = = = = = = = = = = = = = = = =
        Configuration local_config = new Configuration();

        Configuration adls_config = GenerateADLSConfig(
                System.getenv("ADLS_STORAGE_ACCOUNT_NAME"),
                System.getenv("ADLS_CLIENT_ID"),
                System.getenv("ADLS_CLIENT_SECRET"),
                System.getenv("ADLS_CLIENT_TENANT"));

        // = = = = = = = = = = = = = =
        // Generate fake data + schema
        // = = = = = = = = = = = = = =
        StructType JavaSchema = new StructType()
                .add("userId", new IntegerType())
                .add("rank", new IntegerType());

        UserRank dataToWrite[] = new UserRank[] {
                new UserRank(1, 3),
                new UserRank(2, 0),
                new UserRank(3, 100)
        };

        // = = = = = = = = = =
        // ADLS Client object
        // = = = = = = = = = =
        DataLakeServiceClient adls_client = GetDataLakeServiceClient(
                System.getenv("ADLS_STORAGE_ACCOUNT_NAME"),
                System.getenv("ADLS_CLIENT_ID"),
                System.getenv("ADLS_CLIENT_SECRET"),
                System.getenv("ADLS_CLIENT_TENANT"));

        // = = = = = = =
        // Write: Local
        // = = = = = = =
        String WriteDir_local = "/tmp/delta_standalone_write";

        System.out.println(MessageFormat.format("Writing Delta To: {0}", WriteDir_local));

        Path WritePath_local = new Path(WriteDir_local);

        DeltaLog local_write_log = DeltaLog.forTable(local_config, WritePath_local);

        WriteToDeltaLocal(local_write_log, local_config, WritePath_local, WriteDir_local, dataToWrite,
                UserRank.getClassSchema(), JavaSchema);

        // = = = = = =
        // Read: Local
        // = = = = = =
        String ReadDir_local = "/tmp/delta_standalone_write";

        System.out.println(MessageFormat.format("Reading Delta Files From: {0}", ReadDir_local));

        DeltaLog local_read_log = DeltaLog.forTable(local_config, ReadDir_local);

        printSnapshotDetails("Local table", local_read_log.snapshot());

        // = = = = = = =
        // Write: ADLS
        // = = = = = = =
        String WriteDir_adls = "tmp/delta_standalone_write";

        DataLakeDirectoryClient adls_directory = CreateDirectoryIfNotExists(adls_client,
                System.getenv("ADLS_STORAGE_FRAUD_CONTAINER_NAME"), WriteDir_adls);

        System.out.println(MessageFormat.format("Writing Delta To: {0}", WriteDir_adls));

        Path WritePath_adls = new Path(
                MessageFormat.format("abfs://{0}@{1}.dfs.core.windows.net/{2}",
                        System.getenv("ADLS_STORAGE_FRAUD_CONTAINER_NAME"), // <-- Container writing to
                        System.getenv("ADLS_STORAGE_ACCOUNT_NAME"),
                        WriteDir_adls));

        // DeltaLog adls_write_log = DeltaLog.forTable(adls_config, WritePath_adls);
        // Store adls = Store.ADLS;

        // WriteToDelta(adls_write_log, adls, adls_config, WritePath_adls,
        // WriteDir_adls, dataToWrite, UserRank.getClassSchema(), JavaSchema);

        // = = = = = =
        // Read: ADLS
        // = = = = = =
        String ReadDir_adls = "kafka/scene_raw";

        System.out.println(MessageFormat.format("Reading Delta Files From: {0}", ReadDir_adls));

        Path ReadPath_adls = new Path(
                MessageFormat.format("abfs://{0}@{1}.dfs.core.windows.net/{2}",
                        System.getenv("ADLS_STORAGE_FRAUD_CONTAINER_NAME"), // <-- Container reading from
                        System.getenv("ADLS_STORAGE_ACCOUNT_NAME"),
                        ReadDir_adls));

        DeltaLog adls_read_log = DeltaLog.forTable(adls_config, ReadPath_adls);

        printSnapshotDetails("ADLS table", adls_read_log.snapshot());

    }
}